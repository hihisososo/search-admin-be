package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.common.service.ProductBulkFetchService;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.dto.EvaluationReportDetailResponse;
import com.yjlee.search.evaluation.dto.EvaluationReportSummaryResponse;
import com.yjlee.search.evaluation.exception.ReportNotFoundException;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.model.EvaluationReportDetail;
import com.yjlee.search.evaluation.model.EvaluationReportDocument;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.model.ReportDocumentType;
import com.yjlee.search.evaluation.repository.EvaluationReportDetailRepository;
import com.yjlee.search.evaluation.repository.EvaluationReportDocumentRepository;
import com.yjlee.search.evaluation.repository.EvaluationReportRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchMode;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.IndexResolver;
import com.yjlee.search.search.service.SearchService;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class EvaluationReportService {

  private final EvaluationQueryService evaluationQueryService;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final EvaluationReportRepository evaluationReportRepository;
  private final EvaluationReportDetailRepository reportDetailRepository;
  private final EvaluationReportDocumentRepository reportDocumentRepository;
  private final SearchService searchService;
  private final ElasticsearchClient elasticsearchClient;
  private final IndexResolver indexResolver;
  private final EvaluationReportPersistenceService persistenceService;
  private final ProductBulkFetchService productBulkFetchService;

  @PreDestroy
  public void shutdown() {
    log.info("EvaluationReportService 종료 처리 완료");
  }

  public EvaluationReportService(
      EvaluationQueryService evaluationQueryService,
      QueryProductMappingRepository queryProductMappingRepository,
      EvaluationReportRepository evaluationReportRepository,
      EvaluationReportDetailRepository reportDetailRepository,
      EvaluationReportDocumentRepository reportDocumentRepository,
      SearchService searchService,
      ElasticsearchClient elasticsearchClient,
      IndexResolver indexResolver,
      EvaluationReportPersistenceService persistenceService,
      ProductBulkFetchService productBulkFetchService) {
    this.evaluationQueryService = evaluationQueryService;
    this.queryProductMappingRepository = queryProductMappingRepository;
    this.evaluationReportRepository = evaluationReportRepository;
    this.reportDetailRepository = reportDetailRepository;
    this.reportDocumentRepository = reportDocumentRepository;
    this.searchService = searchService;
    this.elasticsearchClient = elasticsearchClient;
    this.indexResolver = indexResolver;
    this.persistenceService = persistenceService;
    this.productBulkFetchService = productBulkFetchService;
  }

  private static final int DEFAULT_RETRIEVAL_SIZE = 300;

  public EvaluationExecuteResponse executeEvaluation(
      String reportName, ProgressCallback progressCallback) {
    return executeEvaluation(reportName, SearchMode.KEYWORD_ONLY, 60, 100, progressCallback);
  }

  public EvaluationExecuteResponse executeEvaluation(
      String reportName,
      SearchMode searchMode,
      Integer rrfK,
      Integer hybridTopK,
      ProgressCallback progressCallback) {
    log.info(
        "평가 실행 시작: {}, 검색 결과 개수: {}, 검색모드: {}", reportName, DEFAULT_RETRIEVAL_SIZE, searchMode);

    List<EvaluationQuery> queries = evaluationQueryService.getAllQueries();

    // 캐시 제거 - 직접 조회 방식 사용

    List<EvaluationExecuteResponse.QueryEvaluationDetail> queryDetails = new ArrayList<>();

    double totalRecall300 = 0.0; // Recall@300
    double totalPrecision20 = 0.0; // Precision@20

    // Thread-safe 리스트 사용으로 스레드 안전성 확보
    List<EvaluationExecuteResponse.QueryEvaluationDetail> synchronizedQueryDetails =
        new CopyOnWriteArrayList<>();

    // 진행률 추적을 위한 AtomicInteger
    AtomicInteger completed = new AtomicInteger(0);
    int totalQueries = queries.size();

    // 병렬 처리로 성능 개선
    List<CompletableFuture<EvaluationExecuteResponse.QueryEvaluationDetail>> futures =
        queries.stream()
            .map(
                query -> {
                  CompletableFuture<EvaluationExecuteResponse.QueryEvaluationDetail> future =
                      CompletableFuture.supplyAsync(
                          () -> evaluateQuery(query.getQuery(), searchMode, rrfK, hybridTopK));
                  // 각 쿼리 완료시 진행률 업데이트
                  future.whenComplete(
                      (result, ex) -> {
                        int done = completed.incrementAndGet();
                        if (progressCallback != null) {
                          int progress = 10 + (done * 80 / totalQueries);
                          progressCallback.updateProgress(
                              progress, String.format("평가 진행 중: %d/%d 쿼리 완료", done, totalQueries));
                        }
                      });
                  return future;
                })
            .collect(Collectors.toList());

    // 모든 작업 완료 대기 및 결과 수집
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
          .get(1800, TimeUnit.SECONDS); // 30분 타임아웃

      for (CompletableFuture<EvaluationExecuteResponse.QueryEvaluationDetail> future : futures) {
        try {
          EvaluationExecuteResponse.QueryEvaluationDetail detail = future.get();
          if (detail != null) {
            synchronizedQueryDetails.add(detail);
          }
        } catch (Exception e) {
          log.warn("쿼리 평가 결과 수집 실패", e);
        }
      }
    } catch (Exception e) {
      log.error("병렬 처리 완료 대기 실패", e);
    }

    // 결과 수집 (동기화된 리스트에서)
    queryDetails.addAll(synchronizedQueryDetails);

    // 각 쿼리마다 메트릭 계산해서 합산 및 detail에 저장
    for (EvaluationExecuteResponse.QueryEvaluationDetail detail : queryDetails) {
      String query = detail.getQuery();
      // 정답셋 직접 조회
      Set<String> relevantDocs = getRelevantDocuments(query);
      // Recall@300 계산을 위해 항상 300개 검색
      int searchSize = 300;
      List<String> retrievedDocs =
          getRetrievedDocumentsOrdered(query, searchMode, rrfK, hybridTopK, searchSize);

      // Recall@300
      double recall300 = computeRecallAtK(retrievedDocs, relevantDocs, 300);
      totalRecall300 += recall300;
      detail.setRecallAt300(recall300);

      // Precision@20
      double precision20 = computePrecisionAtK(retrievedDocs, relevantDocs, 20);
      totalPrecision20 += precision20;
      detail.setPrecisionAt20(precision20);
    }

    double avgRecall300 = queries.isEmpty() ? 0.0 : totalRecall300 / queries.size();
    double avgPrecision20 = queries.isEmpty() ? 0.0 : totalPrecision20 / queries.size();

    // 트랜잭션 내에서 DB 저장 처리 (외부 서비스 호출)
    EvaluationReport report =
        persistenceService.saveEvaluationResults(
            reportName, queries.size(), avgRecall300, avgPrecision20, queryDetails);

    log.info(
        "평가 실행 완료: Recall@300={}, Precision@20={}",
        String.format("%.3f", avgRecall300),
        String.format("%.3f", avgPrecision20));

    // 캐시 제거됨

    return EvaluationExecuteResponse.builder()
        .reportId(report.getId())
        .reportName(reportName)
        .recall300(avgRecall300)
        .precision20(avgPrecision20)
        .totalQueries(queries.size())
        .queryDetails(queryDetails)
        .createdAt(report.getCreatedAt())
        .build();
  }

  public EvaluationExecuteResponse.QueryEvaluationDetail evaluateQuery(String query) {
    return evaluateQuery(query, SearchMode.KEYWORD_ONLY, 60, 100);
  }

  public EvaluationExecuteResponse.QueryEvaluationDetail evaluateQuery(
      String query, SearchMode searchMode, Integer rrfK, Integer hybridTopK) {
    // 정답셋 직접 조회
    Set<String> relevantDocs = getRelevantDocuments(query);
    // Recall@300 계산을 위해 항상 300개 검색
    int searchSize = 300;
    List<String> retrievedDocs =
        getRetrievedDocumentsOrdered(query, searchMode, rrfK, hybridTopK, searchSize); // 순서 유지
    Set<String> retrievedSet = new LinkedHashSet<>(retrievedDocs);
    Set<String> correctDocs = getIntersection(relevantDocs, retrievedSet);

    List<String> missingIds =
        relevantDocs.stream()
            .filter(doc -> !retrievedSet.contains(doc))
            .collect(Collectors.toList());

    List<String> wrongIds =
        retrievedDocs.stream()
            .filter(doc -> !relevantDocs.contains(doc))
            .collect(Collectors.toList());

    // MISSING과 WRONG 문서들의 정보만 구성 (캐시에서 가져오기)
    Set<String> docIdsToFetch = new HashSet<>();
    docIdsToFetch.addAll(missingIds);
    docIdsToFetch.addAll(wrongIds);
    Map<String, ProductDocument> productMap = getProductsBulk(new ArrayList<>(docIdsToFetch));

    List<EvaluationExecuteResponse.DocumentInfo> missingDocs =
        missingIds.stream()
            .map(
                id -> {
                  ProductDocument p = productMap.get(id);
                  return EvaluationExecuteResponse.DocumentInfo.builder()
                      .productId(id)
                      .productName(p != null ? p.getNameRaw() : null)
                      .productSpecs(p != null ? p.getSpecsRaw() : null)
                      .build();
                })
            .toList();

    List<EvaluationExecuteResponse.DocumentInfo> wrongDocs =
        wrongIds.stream()
            .map(
                id -> {
                  ProductDocument p = productMap.get(id);
                  return EvaluationExecuteResponse.DocumentInfo.builder()
                      .productId(id)
                      .productName(p != null ? p.getNameRaw() : null)
                      .productSpecs(p != null ? p.getSpecsRaw() : null)
                      .build();
                })
            .toList();

    return EvaluationExecuteResponse.QueryEvaluationDetail.builder()
        .query(query)
        .relevantCount(relevantDocs.size())
        .retrievedCount(retrievedDocs.size())
        .correctCount(correctDocs.size())
        .missingDocuments(missingDocs)
        .wrongDocuments(wrongDocs)
        .build();
  }

  private double computeMrrAtK(List<String> retrievedOrder, Set<String> relevantSet, int k) {
    int limit = Math.min(k, retrievedOrder.size());
    for (int i = 0; i < limit; i++) {
      if (relevantSet.contains(retrievedOrder.get(i))) {
        return 1.0 / (i + 1);
      }
    }
    return 0.0;
  }

  private double computeRecallAtK(List<String> retrievedOrder, Set<String> relevantSet, int k) {
    if (relevantSet == null || relevantSet.isEmpty()) return 0.0;
    int limit = Math.min(k, retrievedOrder.size());
    int hits = 0;
    for (int i = 0; i < limit; i++) {
      if (relevantSet.contains(retrievedOrder.get(i))) hits++;
    }
    return (double) hits / relevantSet.size();
  }

  private double computePrecisionAtK(List<String> retrievedOrder, Set<String> relevantSet, int k) {
    if (retrievedOrder == null || retrievedOrder.isEmpty()) return 0.0;
    int limit = Math.min(k, retrievedOrder.size());
    int hits = 0;
    for (int i = 0; i < limit; i++) {
      if (relevantSet.contains(retrievedOrder.get(i))) hits++;
    }
    // 분모를 항상 k로 고정하여 엄격한 평가
    return (double) hits / k;
  }

  private double computeAveragePrecision(List<String> retrievedOrder, Set<String> relevantSet) {
    if (relevantSet == null || relevantSet.isEmpty()) return 0.0;
    int hits = 0;
    double sumPrecision = 0.0;
    for (int i = 0; i < retrievedOrder.size(); i++) {
      if (relevantSet.contains(retrievedOrder.get(i))) {
        hits++;
        sumPrecision += (double) hits / (i + 1);
      }
    }
    return hits == 0 ? 0.0 : (sumPrecision / relevantSet.size());
  }

  private double computeNdcg(List<String> retrievedOrder, Set<String> relevantSet) {
    if (retrievedOrder == null || retrievedOrder.isEmpty()) return 0.0;
    if (relevantSet == null || relevantSet.isEmpty()) return 0.0;

    // DCG 계산: 정답셋에 있으면 1, 없으면 0
    double dcg = 0.0;
    for (int i = 0; i < retrievedOrder.size(); i++) {
      String pid = retrievedOrder.get(i);
      int rel = relevantSet.contains(pid) ? 1 : 0;
      if (rel > 0) {
        dcg += (Math.pow(2.0, rel) - 1.0) / (Math.log(i + 2) / Math.log(2));
      }
    }

    // IDCG 계산: 이상적인 순서 (정답을 먼저 배치)
    // 올바른 방법: min(검색결과크기, 관련문서크기)개의 1을 상위에 배치
    int k = retrievedOrder.size();
    int numRelevant = relevantSet.size();
    int numIdealOnes = Math.min(k, numRelevant);

    List<Integer> ideal = new ArrayList<>();
    // 상위 numIdealOnes개는 1로 채움
    for (int i = 0; i < numIdealOnes; i++) {
      ideal.add(1);
    }
    // 나머지는 0으로 채움
    for (int i = numIdealOnes; i < k; i++) {
      ideal.add(0);
    }

    double idcg = 0.0;
    for (int i = 0; i < ideal.size(); i++) {
      int rel = ideal.get(i);
      if (rel > 0) {
        idcg += (Math.pow(2.0, rel) - 1.0) / (Math.log(i + 2) / Math.log(2));
      }
    }

    if (idcg == 0.0) return 0.0;
    return dcg / idcg;
  }

  private Set<String> getRelevantDocuments(String query) {
    Optional<EvaluationQuery> evaluationQueryOpt = evaluationQueryService.findByQuery(query);
    if (evaluationQueryOpt.isEmpty()) {
      log.warn("평가 쿼리를 찾을 수 없습니다: {}", query);
      return Collections.emptySet();
    }

    List<QueryProductMapping> mappings =
        queryProductMappingRepository.findByEvaluationQueryAndRelevanceScoreGreaterThanEqual(
            evaluationQueryOpt.get(), 1);
    return mappings.stream().map(QueryProductMapping::getProductId).collect(Collectors.toSet());
  }

  private Set<String> getRetrievedDocuments(String query) {
    try {
      log.info("DEV 환경 검색 API 호출: {}, 검색 결과 개수: {}", query, DEFAULT_RETRIEVAL_SIZE);

      // DEV 환경 시뮬레이션 검색 요청 생성
      SearchSimulationRequest searchRequest = new SearchSimulationRequest();
      searchRequest.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
      searchRequest.setQuery(query);
      searchRequest.setPage(0);
      searchRequest.setSize(DEFAULT_RETRIEVAL_SIZE); // 고정 300개 조회
      searchRequest.setExplain(false);

      // 시뮬레이션 검색 API 호출
      SearchExecuteResponse searchResponse = searchService.searchProductsSimulation(searchRequest);

      // 검색 결과에서 상품 ID 추출
      Set<String> retrievedProductIds =
          searchResponse.getHits().getData().stream()
              .map(product -> product.getId())
              .collect(Collectors.toCollection(LinkedHashSet::new));

      log.info("검색 결과: {} 개 상품 조회", retrievedProductIds.size());
      return retrievedProductIds;

    } catch (Exception e) {
      log.error("검색 API 호출 실패: {}", query, e);
      // 검색 실패 시 빈 셋 반환
      return new HashSet<>();
    }
  }

  // 순서를 보존한 검색 결과 목록
  private List<String> getRetrievedDocumentsOrdered(String query) {
    return getRetrievedDocumentsOrdered(
        query, SearchMode.KEYWORD_ONLY, 60, 100, DEFAULT_RETRIEVAL_SIZE);
  }

  private List<String> getRetrievedDocumentsOrdered(
      String query, SearchMode searchMode, Integer rrfK, Integer hybridTopK) {
    return getRetrievedDocumentsOrdered(
        query, searchMode, rrfK, hybridTopK, DEFAULT_RETRIEVAL_SIZE);
  }

  private List<String> getRetrievedDocumentsOrdered(
      String query, SearchMode searchMode, Integer rrfK, Integer hybridTopK, int size) {
    try {
      log.info("DEV 환경 검색 API 호출(ordered): {}, 검색 결과 개수: {}", query, size);

      SearchSimulationRequest searchRequest = new SearchSimulationRequest();
      searchRequest.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
      searchRequest.setQuery(query);
      searchRequest.setPage(0);
      searchRequest.setSize(size);
      searchRequest.setExplain(false);
      searchRequest.setSearchMode(searchMode);
      searchRequest.setRrfK(rrfK);
      searchRequest.setHybridTopK(hybridTopK);

      SearchExecuteResponse searchResponse = searchService.searchProductsSimulation(searchRequest);

      List<String> retrievedProductIds =
          searchResponse.getHits().getData().stream().map(product -> product.getId()).toList();

      log.info("검색 결과(ordered): {} 개 상품 조회", retrievedProductIds.size());
      return retrievedProductIds;

    } catch (Exception e) {
      log.error("검색 API 호출 실패(ordered): {}", query, e);
      return Collections.emptyList();
    }
  }

  private Set<String> getIntersection(Set<String> set1, Set<String> set2) {
    Set<String> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);
    return intersection;
  }

  @Transactional(readOnly = true)
  public List<EvaluationReport> getAllReports() {
    return evaluationReportRepository.findByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public List<EvaluationReport> getReportsByKeyword(String keyword) {
    if (keyword == null || keyword.trim().isEmpty()) {
      return getAllReports();
    }
    return evaluationReportRepository.findByReportNameContainingIgnoreCaseOrderByCreatedAtDesc(
        keyword.trim());
  }
  
  @Transactional(readOnly = true)
  public List<EvaluationReportSummaryResponse> getReportSummariesByKeyword(String keyword) {
    List<EvaluationReport> reports = getReportsByKeyword(keyword);
    return reports.stream()
        .map(r -> EvaluationReportSummaryResponse.builder()
            .id(r.getId())
            .reportName(r.getReportName())
            .totalQueries(r.getTotalQueries())
            .averagePrecision20(r.getAveragePrecision20())
            .averageRecall300(r.getAverageRecall300())
            .createdAt(r.getCreatedAt())
            .build())
        .toList();
  }

  @Transactional(readOnly = true)
  public EvaluationReport getReportById(Long reportId) {
    return evaluationReportRepository.findById(reportId).orElse(null);
  }

  @Transactional(readOnly = true)
  public EvaluationReportDetailResponse getReportDetail(Long reportId) {
    EvaluationReport report = evaluationReportRepository.findById(reportId)
        .orElseThrow(() -> new ReportNotFoundException(reportId));

    // 상세/문서 테이블에서 조회
    List<EvaluationReportDetail> rows =
        reportDetailRepository.findByReport(report);
    List<EvaluationReportDocument> miss =
        reportDocumentRepository.findByReportAndDocType(
            report, ReportDocumentType.MISSING);
    List<EvaluationReportDocument> wrong =
        reportDocumentRepository.findByReportAndDocType(
            report, ReportDocumentType.WRONG);

    Map<String, List<EvaluationReportDetailResponse.DocumentInfo>> missingByQuery = new HashMap<>();
    for (var d : miss) {
      missingByQuery
          .computeIfAbsent(d.getQuery(), k -> new ArrayList<>())
          .add(
              EvaluationReportDetailResponse.DocumentInfo.builder()
                  .productId(d.getProductId())
                  .productName(d.getProductName())
                  .productSpecs(d.getProductSpecs())
                  .build());
    }
    Map<String, List<EvaluationReportDetailResponse.DocumentInfo>> wrongByQuery = new HashMap<>();
    for (var d : wrong) {
      wrongByQuery
          .computeIfAbsent(d.getQuery(), k -> new ArrayList<>())
          .add(
              EvaluationReportDetailResponse.DocumentInfo.builder()
                  .productId(d.getProductId())
                  .productName(d.getProductName())
                  .productSpecs(d.getProductSpecs())
                  .build());
    }

    List<EvaluationReportDetailResponse.QueryDetail> details = new ArrayList<>();
    for (var r : rows) {
      EvaluationReportDetailResponse.QueryDetail qd =
          EvaluationReportDetailResponse.QueryDetail.builder()
              .query(r.getQuery())
              .relevantCount(r.getRelevantCount())
              .retrievedCount(r.getRetrievedCount())
              .correctCount(r.getCorrectCount())
              .precisionAt20(r.getPrecisionAt20())
              .recallAt300(r.getRecallAt300())
              .missingDocuments(missingByQuery.getOrDefault(r.getQuery(), List.of()))
              .wrongDocuments(wrongByQuery.getOrDefault(r.getQuery(), List.of()))
              .build();
      details.add(qd);
    }

    // 평가 시점에 계산된 메트릭 사용 (DB에 저장된 값)

    return EvaluationReportDetailResponse.builder()
        .id(report.getId())
        .reportName(report.getReportName())
        .totalQueries(report.getTotalQueries())
        .averageRecall300(report.getAverageRecall300())
        .averagePrecision20(report.getAveragePrecision20())
        .createdAt(report.getCreatedAt())
        .queryDetails(details)
        .build();
  }

  @Transactional
  public void deleteReport(Long reportId) {
    if (!evaluationReportRepository.existsById(reportId)) {
      throw new ReportNotFoundException(reportId);
    }
    try {
      log.info("평가 리포트 삭제 시작: reportId={}", reportId);

      // 벌크 삭제 쿼리 사용으로 성능 개선
      // SELECT 없이 바로 DELETE 실행
      reportDetailRepository.deleteByReportIdBulk(reportId);
      log.debug("리포트 상세 삭제 완료");

      reportDocumentRepository.deleteByReportIdBulk(reportId);
      log.debug("리포트 문서 삭제 완료");

      // 리포트 자체 삭제
      evaluationReportRepository.deleteById(reportId);
      log.info("평가 리포트 삭제 완료: reportId={}", reportId);

    } catch (Exception e) {
      log.error("리포트 삭제 실패: {}", reportId, e);
      throw e;
    }
  }

  // 저장용 상세 생성: 상품명/스펙 포함
  private List<PersistedQueryEvaluationDetail> buildPersistedDetails(
      List<EvaluationQuery> queries, Integer retrievalSize) {
    List<PersistedQueryEvaluationDetail> out = new ArrayList<>();
    for (EvaluationQuery q : queries) {
      try {
        Set<String> relevant = getRelevantDocuments(q.getQuery());
        Set<String> retrieved = getRetrievedDocuments(q.getQuery());
        Set<String> correct = getIntersection(relevant, retrieved);
        List<String> missingIds = relevant.stream().filter(id -> !retrieved.contains(id)).toList();
        List<String> wrongIds = retrieved.stream().filter(id -> !relevant.contains(id)).toList();

        Map<String, ProductDocument> productMap =
            getProductsBulk(new ArrayList<>(union(missingIds, wrongIds)));

        List<PersistedDocumentInfo> missingDocs =
            missingIds.stream()
                .map(
                    id -> {
                      ProductDocument p = productMap.get(id);
                      return PersistedDocumentInfo.builder()
                          .productId(id)
                          .productName(p != null ? p.getNameRaw() : null)
                          .productSpecs(p != null ? p.getSpecsRaw() : null)
                          .build();
                    })
                .toList();

        List<PersistedDocumentInfo> wrongDocs =
            wrongIds.stream()
                .map(
                    id -> {
                      ProductDocument p = productMap.get(id);
                      return PersistedDocumentInfo.builder()
                          .productId(id)
                          .productName(p != null ? p.getNameRaw() : null)
                          .productSpecs(p != null ? p.getSpecsRaw() : null)
                          .build();
                    })
                .toList();

        // 지표 계산
        List<String> retrievedList = new ArrayList<>(retrieved);
        double ndcg = computeNdcg(retrievedList, relevant);
        double ndcgAt10 =
            computeNdcg(retrievedList.subList(0, Math.min(10, retrievedList.size())), relevant);
        double ndcgAt20 =
            computeNdcg(retrievedList.subList(0, Math.min(20, retrievedList.size())), relevant);
        double mrrAt10 = computeMrrAtK(retrievedList, relevant, 10);
        double recallAt50 = computeRecallAtK(retrievedList, relevant, 50);
        double averagePrecision = computeAveragePrecision(retrievedList, relevant);
        double recallAt300 = computeRecallAtK(retrievedList, relevant, 300);

        out.add(
            PersistedQueryEvaluationDetail.builder()
                .query(q.getQuery())
                .ndcg(ndcg)
                .relevantCount(relevant.size())
                .retrievedCount(retrieved.size())
                .correctCount(correct.size())
                .missingDocuments(missingDocs)
                .wrongDocuments(wrongDocs)
                .ndcgAt10(ndcgAt10)
                .ndcgAt20(ndcgAt20)
                .mrrAt10(mrrAt10)
                .recallAt50(recallAt50)
                .averagePrecision(averagePrecision)
                .recallAt300(recallAt300)
                .build());
      } catch (Exception e) {
        log.warn("저장용 상세 생성 실패: {}", q.getQuery(), e);
      }
    }
    return out;
  }

  private List<String> union(List<String> a, List<String> b) {
    List<String> u = new ArrayList<>(a);
    for (String x : b) if (!u.contains(x)) u.add(x);
    return u;
  }

  private Map<String, ProductDocument> getProductsBulk(List<String> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return new HashMap<>();
    }
    return productBulkFetchService.fetchBulk(productIds, IndexEnvironment.EnvironmentType.DEV);
  }

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class PersistedDocumentInfo {
    private String productId;
    private String productName;
    private String productSpecs;
  }

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class PersistedQueryEvaluationDetail {
    private String query;
    private Double ndcg;
    private Double ndcgAt10;
    private Double ndcgAt20;
    private Double mrrAt10;
    private Double recallAt50;
    private Double averagePrecision;
    private Double recallAt300;
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private List<PersistedDocumentInfo> missingDocuments;
    private List<PersistedDocumentInfo> wrongDocuments;
  }
}
