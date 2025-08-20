package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.dto.EvaluationReportDetailResponse;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationReportRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.IndexResolver;
import com.yjlee.search.search.service.SearchService;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationReportService {

  private final EvaluationQueryService evaluationQueryService;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final EvaluationReportRepository evaluationReportRepository;
  private final com.yjlee.search.evaluation.repository.EvaluationReportDetailRepository
      reportDetailRepository;
  private final com.yjlee.search.evaluation.repository.EvaluationReportDocumentRepository
      reportDocumentRepository;
  private final SearchService searchService;
  // private final ObjectMapper objectMapper; // 미사용
  private final ElasticsearchClient elasticsearchClient;
  private final IndexResolver indexResolver;
  private final ExecutorService executorService = Executors.newFixedThreadPool(5);

  @PreDestroy
  public void shutdown() {
    executorService.shutdown();
  }

  private static final int DEFAULT_RETRIEVAL_SIZE = 300;

  @Transactional
  public EvaluationExecuteResponse executeEvaluation(String reportName) {
    log.info("📊 평가 실행 시작: {}, 검색 결과 개수: {}", reportName, DEFAULT_RETRIEVAL_SIZE);

    List<EvaluationQuery> queries = evaluationQueryService.getAllQueries();
    List<EvaluationExecuteResponse.QueryEvaluationDetail> queryDetails = new ArrayList<>();

    double totalRecall300 = 0.0; // Recall@300
    double totalNdcg20 = 0.0; // NDCG@20

    // 동기화된 리스트 사용으로 스레드 안전성 확보
    List<EvaluationExecuteResponse.QueryEvaluationDetail> synchronizedQueryDetails =
        Collections.synchronizedList(new ArrayList<>());

    // 병렬 처리로 성능 개선
    List<CompletableFuture<Void>> futures =
        queries.stream()
            .map(
                query ->
                    CompletableFuture.runAsync(
                        () -> {
                          try {
                            EvaluationExecuteResponse.QueryEvaluationDetail detail =
                                evaluateQuery(query.getQuery());
                            if (detail != null) {
                              synchronizedQueryDetails.add(detail);
                            }
                          } catch (Exception e) {
                            log.warn("⚠️ 쿼리 '{}' 평가 실패", query.getQuery(), e);
                          }
                        },
                        executorService))
            .collect(Collectors.toList());

    // 모든 작업 완료 대기
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
          .get(300, java.util.concurrent.TimeUnit.SECONDS); // 5분 타임아웃
    } catch (Exception e) {
      log.error("⚠️ 병렬 처리 완료 대기 실패", e);
    }

    // 결과 수집 (동기화된 리스트에서)
    queryDetails.addAll(synchronizedQueryDetails);

    // 각 쿼리마다 메트릭 계산해서 합산 및 detail에 저장
    for (EvaluationExecuteResponse.QueryEvaluationDetail detail : queryDetails) {
      String query = detail.getQuery();
      Set<String> relevantDocs = getRelevantDocuments(query);
      List<String> retrievedDocs = getRetrievedDocumentsOrdered(query);

      // Recall@300
      double recall300 = computeRecallAtK(retrievedDocs, relevantDocs, 300);
      totalRecall300 += recall300;
      detail.setRecallAt300(recall300);

      // NDCG@20
      double ndcg20 =
          computeNdcg(
              new ArrayList<>(retrievedDocs.subList(0, Math.min(20, retrievedDocs.size()))),
              relevantDocs);
      totalNdcg20 += ndcg20;
      detail.setNdcgAt20(ndcg20);
    }

    double avgRecall300 = queries.isEmpty() ? 0.0 : totalRecall300 / queries.size();
    double avgNdcg20 = queries.isEmpty() ? 0.0 : totalNdcg20 / queries.size();

    EvaluationReport report =
        saveEvaluationReport(reportName, queries.size(), avgRecall300, avgNdcg20);

    // 세부 결과를 구조화 테이블에 저장 - queryDetails 재사용
    java.util.List<com.yjlee.search.evaluation.model.EvaluationReportDetail> detailRows =
        new java.util.ArrayList<>();
    java.util.List<com.yjlee.search.evaluation.model.EvaluationReportDocument> docRows =
        new java.util.ArrayList<>();
    for (EvaluationExecuteResponse.QueryEvaluationDetail d : queryDetails) {
      detailRows.add(
          com.yjlee.search.evaluation.model.EvaluationReportDetail.builder()
              .report(report)
              .query(d.getQuery())
              .relevantCount(d.getRelevantCount())
              .retrievedCount(d.getRetrievedCount())
              .correctCount(d.getCorrectCount())
              .ndcgAt20(d.getNdcgAt20())
              .recallAt300(d.getRecallAt300())
              .build());
      // MISSING, WRONG 타입만 저장
      if (d.getMissingDocuments() != null) {
        for (EvaluationExecuteResponse.DocumentInfo m : d.getMissingDocuments()) {
          docRows.add(
              com.yjlee.search.evaluation.model.EvaluationReportDocument.builder()
                  .report(report)
                  .query(d.getQuery())
                  .productId(m.getProductId())
                  .docType(com.yjlee.search.evaluation.model.ReportDocumentType.MISSING)
                  .productName(m.getProductName())
                  .productSpecs(m.getProductSpecs())
                  .build());
        }
      }
      if (d.getWrongDocuments() != null) {
        for (EvaluationExecuteResponse.DocumentInfo w : d.getWrongDocuments()) {
          docRows.add(
              com.yjlee.search.evaluation.model.EvaluationReportDocument.builder()
                  .report(report)
                  .query(d.getQuery())
                  .productId(w.getProductId())
                  .docType(com.yjlee.search.evaluation.model.ReportDocumentType.WRONG)
                  .productName(w.getProductName())
                  .productSpecs(w.getProductSpecs())
                  .build());
        }
      }
    }
    if (!detailRows.isEmpty()) reportDetailRepository.saveAll(detailRows);
    if (!docRows.isEmpty()) reportDocumentRepository.saveAll(docRows);

    log.info(
        "✅ 평가 실행 완료: Recall@300={}, NDCG@20={}",
        String.format("%.3f", avgRecall300),
        String.format("%.3f", avgNdcg20));

    return EvaluationExecuteResponse.builder()
        .reportId(report.getId())
        .reportName(reportName)
        .recall300(avgRecall300)
        .ndcg20(avgNdcg20)
        .totalQueries(queries.size())
        .queryDetails(queryDetails)
        .createdAt(report.getCreatedAt())
        .build();
  }

  private EvaluationExecuteResponse.QueryEvaluationDetail evaluateQuery(String query) {
    Set<String> relevantDocs = getRelevantDocuments(query);
    List<String> retrievedDocs = getRetrievedDocumentsOrdered(query); // 순서 유지
    Set<String> retrievedSet = new java.util.LinkedHashSet<>(retrievedDocs);
    Set<String> correctDocs = getIntersection(relevantDocs, retrievedSet);

    List<String> missingIds =
        relevantDocs.stream()
            .filter(doc -> !retrievedSet.contains(doc))
            .collect(Collectors.toList());

    List<String> wrongIds =
        retrievedDocs.stream()
            .filter(doc -> !relevantDocs.contains(doc))
            .collect(Collectors.toList());

    // MISSING과 WRONG 문서들의 정보만 구성
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
    return limit == 0 ? 0.0 : (double) hits / limit;
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
    java.util.List<Integer> ideal = new java.util.ArrayList<>();
    for (String pid : retrievedOrder) {
      int rel = relevantSet.contains(pid) ? 1 : 0;
      ideal.add(rel);
    }
    ideal.sort(java.util.Comparator.reverseOrder());

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
      log.warn("⚠️ 평가 쿼리를 찾을 수 없습니다: {}", query);
      return Collections.emptySet();
    }

    List<QueryProductMapping> mappings =
        queryProductMappingRepository.findByEvaluationQueryAndRelevanceScoreGreaterThanEqual(
            evaluationQueryOpt.get(), 1);
    return mappings.stream().map(QueryProductMapping::getProductId).collect(Collectors.toSet());
  }

  private Set<String> getRetrievedDocuments(String query) {
    try {
      log.info("🔍 DEV 환경 검색 API 호출: {}, 검색 결과 개수: {}", query, DEFAULT_RETRIEVAL_SIZE);

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
              .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

      log.info("✅ 검색 결과: {} 개 상품 조회", retrievedProductIds.size());
      return retrievedProductIds;

    } catch (Exception e) {
      log.error("❌ 검색 API 호출 실패: {}", query, e);
      // 검색 실패 시 빈 셋 반환
      return new HashSet<>();
    }
  }

  // 순서를 보존한 검색 결과 목록
  private List<String> getRetrievedDocumentsOrdered(String query) {
    try {
      log.info("🔍 DEV 환경 검색 API 호출(ordered): {}, 검색 결과 개수: {}", query, DEFAULT_RETRIEVAL_SIZE);

      SearchSimulationRequest searchRequest = new SearchSimulationRequest();
      searchRequest.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
      searchRequest.setQuery(query);
      searchRequest.setPage(0);
      searchRequest.setSize(DEFAULT_RETRIEVAL_SIZE);
      searchRequest.setExplain(false);

      SearchExecuteResponse searchResponse = searchService.searchProductsSimulation(searchRequest);

      List<String> retrievedProductIds =
          searchResponse.getHits().getData().stream().map(product -> product.getId()).toList();

      log.info("✅ 검색 결과(ordered): {} 개 상품 조회", retrievedProductIds.size());
      return retrievedProductIds;

    } catch (Exception e) {
      log.error("❌ 검색 API 호출 실패(ordered): {}", query, e);
      return java.util.Collections.emptyList();
    }
  }

  private Set<String> getIntersection(Set<String> set1, Set<String> set2) {
    Set<String> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);
    return intersection;
  }

  @Transactional
  private EvaluationReport saveEvaluationReport(
      String reportName, int totalQueries, double averageRecall300, double averageNdcg20) {
    try {
      // JSON은 더 이상 저장하지 않음 (대용량 방지)
      EvaluationReport report =
          EvaluationReport.builder()
              .reportName(reportName)
              .totalQueries(totalQueries)
              .averageRecall300(averageRecall300)
              .averageNdcg20(averageNdcg20)
              .detailedResults(null)
              .build();

      EvaluationReport savedReport = evaluationReportRepository.save(report);
      log.info("✅ 리포트 저장 완료: ID {}", savedReport.getId());
      return savedReport;

    } catch (Exception e) {
      log.error("❌ 리포트 저장 실패", e);
      throw new RuntimeException("리포트 저장 중 오류가 발생했습니다", e);
    }
  }

  public List<EvaluationReport> getAllReports() {
    return evaluationReportRepository.findByOrderByCreatedAtDesc();
  }

  public List<EvaluationReport> getReportsByKeyword(String keyword) {
    if (keyword == null || keyword.trim().isEmpty()) {
      return getAllReports();
    }
    return evaluationReportRepository.findByReportNameContainingIgnoreCaseOrderByCreatedAtDesc(
        keyword.trim());
  }

  public EvaluationReport getReportById(Long reportId) {
    return evaluationReportRepository.findById(reportId).orElse(null);
  }

  public EvaluationReportDetailResponse getReportDetail(Long reportId) {
    EvaluationReport report = evaluationReportRepository.findById(reportId).orElse(null);
    if (report == null) return null;

    // 상세/문서 테이블에서 조회
    List<com.yjlee.search.evaluation.model.EvaluationReportDetail> rows =
        reportDetailRepository.findByReport(report);
    List<com.yjlee.search.evaluation.model.EvaluationReportDocument> miss =
        reportDocumentRepository.findByReportAndDocType(
            report, com.yjlee.search.evaluation.model.ReportDocumentType.MISSING);
    List<com.yjlee.search.evaluation.model.EvaluationReportDocument> wrong =
        reportDocumentRepository.findByReportAndDocType(
            report, com.yjlee.search.evaluation.model.ReportDocumentType.WRONG);

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
              .ndcgAt20(r.getNdcgAt20())
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
        .averageNdcg20(report.getAverageNdcg20())
        .createdAt(report.getCreatedAt())
        .queryDetails(details)
        .build();
  }

  @Transactional
  public boolean deleteReport(Long reportId) {
    if (!evaluationReportRepository.existsById(reportId)) {
      return false;
    }
    try {
      EvaluationReport report = evaluationReportRepository.findById(reportId).orElse(null);
      if (report == null) return false;

      // 상세/문서 레코드 선삭제 후 리포트 삭제
      reportDetailRepository.deleteByReport(report);
      reportDocumentRepository.deleteByReport(report);
      evaluationReportRepository.delete(report);
    } catch (Exception e) {
      log.error("리포트 삭제 실패: {}", reportId, e);
      throw e;
    }
    return true;
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
    Map<String, ProductDocument> productMap = new HashMap<>();
    if (productIds == null || productIds.isEmpty()) return productMap;
    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      MgetRequest.Builder builder = new MgetRequest.Builder().index(indexName);
      for (String id : productIds) builder.ids(id);
      MgetResponse<ProductDocument> response =
          elasticsearchClient.mget(builder.build(), ProductDocument.class);
      for (MultiGetResponseItem<ProductDocument> item : response.docs()) {
        if (item.result() != null && item.result().found()) {
          productMap.put(item.result().id(), item.result().source());
        }
      }
    } catch (Exception e) {
      log.warn("제품 벌크 조회 실패: {}개", productIds.size(), e);
    }
    return productMap;
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
