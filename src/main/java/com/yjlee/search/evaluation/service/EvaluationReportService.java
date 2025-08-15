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
import com.yjlee.search.evaluation.model.RelevanceStatus;
import com.yjlee.search.evaluation.repository.EvaluationReportRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
// import removed: SearchExecuteRequest no longer used here
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

  @Transactional
  public EvaluationExecuteResponse executeEvaluation(String reportName, Integer retrievalSize) {
    log.info("📊 평가 실행 시작: {}, 검색 결과 개수: {}", reportName, retrievalSize);

    List<EvaluationQuery> queries = evaluationQueryService.getAllQueries();
    List<EvaluationExecuteResponse.QueryEvaluationDetail> queryDetails = new ArrayList<>();

    double totalNdcg = 0.0;
    int totalRelevantDocuments = 0;
    int totalRetrievedDocuments = 0;
    int totalCorrectDocuments = 0;

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
                                evaluateQuery(query.getQuery(), retrievalSize);
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
    for (EvaluationExecuteResponse.QueryEvaluationDetail detail : queryDetails) {
      totalNdcg += detail.getNdcg();
      totalRelevantDocuments += detail.getRelevantCount();
      totalRetrievedDocuments += detail.getRetrievedCount();
      totalCorrectDocuments += detail.getCorrectCount();
    }

    double averageNdcg = queries.isEmpty() ? 0.0 : totalNdcg / queries.size();

    // 저장용 상세: 상품명/스펙 포함 (대용량 JSON 대신 테이블 저장)
    List<PersistedQueryEvaluationDetail> persistedDetails =
        buildPersistedDetails(queries, retrievalSize);

    EvaluationReport report =
        saveEvaluationReport(
            reportName,
            queries.size(),
            averageNdcg,
            totalRelevantDocuments,
            totalRetrievedDocuments,
            totalCorrectDocuments,
            java.util.List.of());

    // 세부 결과를 구조화 테이블에 저장
    java.util.List<com.yjlee.search.evaluation.model.EvaluationReportDetail> detailRows =
        new java.util.ArrayList<>();
    java.util.List<com.yjlee.search.evaluation.model.EvaluationReportDocument> docRows =
        new java.util.ArrayList<>();
    for (PersistedQueryEvaluationDetail d : persistedDetails) {
      detailRows.add(
          com.yjlee.search.evaluation.model.EvaluationReportDetail.builder()
              .report(report)
              .query(d.getQuery())
              .ndcg(d.getNdcg())
              .relevantCount(d.getRelevantCount())
              .retrievedCount(d.getRetrievedCount())
              .correctCount(d.getCorrectCount())
              .build());
      if (d.getMissingDocuments() != null) {
        for (PersistedDocumentInfo m : d.getMissingDocuments()) {
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
        for (PersistedDocumentInfo w : d.getWrongDocuments()) {
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

    log.info("✅ 평가 실행 완료: nDCG={:.3f}", averageNdcg);

    return EvaluationExecuteResponse.builder()
        .reportId(report.getId())
        .reportName(reportName)
        .averageNdcg(averageNdcg)
        .totalQueries(queries.size())
        .totalRelevantDocuments(totalRelevantDocuments)
        .totalRetrievedDocuments(totalRetrievedDocuments)
        .totalCorrectDocuments(totalCorrectDocuments)
        .queryDetails(queryDetails)
        .createdAt(report.getCreatedAt())
        .build();
  }

  private EvaluationExecuteResponse.QueryEvaluationDetail evaluateQuery(
      String query, Integer retrievalSize) {
    Set<String> relevantDocs = getRelevantDocuments(query);
    List<String> retrievedDocs = getRetrievedDocumentsOrdered(query, retrievalSize); // 순서 유지
    Set<String> retrievedSet = new java.util.LinkedHashSet<>(retrievedDocs);
    Set<String> correctDocs = getIntersection(relevantDocs, retrievedSet);

    double ndcg = computeNdcg(query, new ArrayList<>(retrievedDocs), relevantDocs);

    List<String> missingIds =
        relevantDocs.stream()
            .filter(doc -> !retrievedSet.contains(doc))
            .collect(Collectors.toList());

    List<String> wrongIds =
        retrievedDocs.stream()
            .filter(doc -> !relevantDocs.contains(doc))
            .collect(Collectors.toList());

    // 문서 정보 구성 (이름/스펙 포함)
    Map<String, ProductDocument> productMap =
        getProductsBulk(new ArrayList<>(union(missingIds, wrongIds)));

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
        .ndcg(ndcg)
        .relevantCount(relevantDocs.size())
        .retrievedCount(retrievedDocs.size())
        .correctCount(correctDocs.size())
        .missingDocuments(missingDocs)
        .wrongDocuments(wrongDocs)
        .build();
  }

  private double computeNdcg(String query, List<String> retrievedOrder, Set<String> relevantSet) {
    if (retrievedOrder == null || retrievedOrder.isEmpty()) return 0.0;
    // relevance: 2 if all query tokens in product name; 1 if any query token in specs; else 0
    List<String> tokens = java.util.Arrays.asList(query.toLowerCase().split("\\s+"));
    double dcg = 0.0;
    for (int i = 0; i < retrievedOrder.size(); i++) {
      String pid = retrievedOrder.get(i);
      int rel = 0;
      try {
        ProductDocument p = getProductsBulk(java.util.List.of(pid)).get(pid);
        if (p != null) {
          String name = p.getNameRaw() == null ? "" : p.getNameRaw().toLowerCase();
          String specs = p.getSpecsRaw() == null ? "" : p.getSpecsRaw().toLowerCase();
          boolean allInTitle = tokens.stream().allMatch(t -> !t.isBlank() && name.contains(t));
          boolean anyInSpecs = tokens.stream().anyMatch(t -> !t.isBlank() && specs.contains(t));
          rel = allInTitle ? 2 : (anyInSpecs ? 1 : 0);
        }
      } catch (Exception ignore) {
      }
      if (rel > 0) dcg += (Math.pow(2.0, rel) - 1.0) / (Math.log(i + 2) / Math.log(2));
    }
    // ideal order: sort by relevance descending
    java.util.List<Integer> ideal = new java.util.ArrayList<>();
    for (String pid : retrievedOrder) {
      int rel = 0;
      try {
        ProductDocument p = getProductsBulk(java.util.List.of(pid)).get(pid);
        if (p != null) {
          String name = p.getNameRaw() == null ? "" : p.getNameRaw().toLowerCase();
          String specs = p.getSpecsRaw() == null ? "" : p.getSpecsRaw().toLowerCase();
          boolean allInTitle = tokens.stream().allMatch(t -> !t.isBlank() && name.contains(t));
          boolean anyInSpecs = tokens.stream().anyMatch(t -> !t.isBlank() && specs.contains(t));
          rel = allInTitle ? 2 : (anyInSpecs ? 1 : 0);
        }
      } catch (Exception ignore) {
      }
      ideal.add(rel);
    }
    ideal.sort(java.util.Comparator.reverseOrder());
    double idcg = 0.0;
    for (int i = 0; i < ideal.size(); i++) {
      int rel = ideal.get(i);
      if (rel > 0) idcg += (Math.pow(2.0, rel) - 1.0) / (Math.log(i + 2) / Math.log(2));
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
        queryProductMappingRepository.findByEvaluationQueryAndRelevanceStatus(
            evaluationQueryOpt.get(), RelevanceStatus.RELEVANT);
    return mappings.stream().map(QueryProductMapping::getProductId).collect(Collectors.toSet());
  }

  private Set<String> getRetrievedDocuments(String query, Integer retrievalSize) {
    try {
      log.info("🔍 DEV 환경으로 검색 API 호출: {}, 검색 결과 개수: {}", query, retrievalSize);

      // 실제 검색 API와 동일하되, DEV 환경으로 고정하여 검색 요청 생성
      SearchSimulationRequest searchRequest = new SearchSimulationRequest();
      searchRequest.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
      searchRequest.setExplain(false);
      searchRequest.setQuery(query);
      // 평가 시 검색 결과는 1페이지(0-index)부터 수집해야 상위 결과와 비교가 됨
      searchRequest.setPage(0);
      searchRequest.setSize(retrievalSize); // 설정된 개수만큼 결과 조회 (최대 300개)

      // 검색 API 호출 (DEV)
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
  private List<String> getRetrievedDocumentsOrdered(String query, Integer retrievalSize) {
    try {
      log.info("🔍 DEV 환경으로 검색 API 호출(ordered): {}, 검색 결과 개수: {}", query, retrievalSize);

      SearchSimulationRequest searchRequest = new SearchSimulationRequest();
      searchRequest.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
      searchRequest.setExplain(false);
      searchRequest.setQuery(query);
      searchRequest.setPage(0);
      searchRequest.setSize(retrievalSize);

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
      String reportName,
      int totalQueries,
      double averageNdcg,
      int totalRelevantDocs,
      int totalRetrievedDocs,
      int totalCorrectDocs,
      List<PersistedQueryEvaluationDetail> queryDetails) {
    try {
      // JSON은 더 이상 저장하지 않음 (대용량 방지)
      EvaluationReport report =
          EvaluationReport.builder()
              .reportName(reportName)
              .totalQueries(totalQueries)
              .averageNdcg(averageNdcg)
              .totalRelevantDocuments(totalRelevantDocs)
              .totalRetrievedDocuments(totalRetrievedDocs)
              .totalCorrectDocuments(totalCorrectDocs)
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

    // 상세/문서 테이블에서 조회한 뒤 응답 조립 (JSON 미사용)
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

    // 순서 비교용 데이터 구성: 검색결과 순위, 정답셋 점수순
    // rank/gain 계산을 위해 제품 상세가 필요하므로 필요한 범위에서만 벌크 조회
    java.util.Map<String, java.util.List<EvaluationReportDetailResponse.RetrievedDocument>>
        retrievedByQuery = new java.util.HashMap<>();
    java.util.Map<String, java.util.List<EvaluationReportDetailResponse.GroundTruthDocument>>
        groundTruthByQuery = new java.util.HashMap<>();

    for (var r : rows) {
      String q = r.getQuery();

      // 검색 결과 순서 수집 (당시 수집 개수에 맞춰 조회 시도)
      int sizeHint = r.getRetrievedCount() != null ? r.getRetrievedCount() : 50;
      java.util.List<String> retrievedOrdered =
          getRetrievedDocumentsOrdered(q, Math.max(1, sizeHint));
      java.util.List<String> unionIds = new java.util.ArrayList<>(retrievedOrdered);

      // 정답셋 수집 및 점수 조회를 위해 매핑 엔티티 조회
      java.util.List<QueryProductMapping> relevantMappings = new java.util.ArrayList<>();
      var eqOpt = evaluationQueryService.findByQuery(q);
      if (eqOpt.isPresent()) {
        relevantMappings =
            queryProductMappingRepository.findByEvaluationQueryAndRelevanceStatus(
                eqOpt.get(), RelevanceStatus.RELEVANT);
        for (var m : relevantMappings) {
          if (!unionIds.contains(m.getProductId())) unionIds.add(m.getProductId());
        }
      }

      // 제품 벌크 조회
      Map<String, ProductDocument> productMap = getProductsBulk(unionIds);

      // retrievedDocuments 구성 (rank/gain)
      java.util.List<EvaluationReportDetailResponse.RetrievedDocument> retrievedDocs =
          new java.util.ArrayList<>();
      java.util.List<String> tokens = java.util.Arrays.asList(q.toLowerCase().split("\\s+"));
      for (int i = 0; i < retrievedOrdered.size(); i++) {
        String pid = retrievedOrdered.get(i);
        ProductDocument p = productMap.get(pid);
        String name = p != null && p.getNameRaw() != null ? p.getNameRaw().toLowerCase() : "";
        String specs = p != null && p.getSpecsRaw() != null ? p.getSpecsRaw().toLowerCase() : "";
        boolean allInTitle = tokens.stream().allMatch(t -> !t.isBlank() && name.contains(t));
        boolean anyInSpecs = tokens.stream().anyMatch(t -> !t.isBlank() && specs.contains(t));
        int gain = allInTitle ? 2 : (anyInSpecs ? 1 : 0);
        retrievedDocs.add(
            EvaluationReportDetailResponse.RetrievedDocument.builder()
                .rank(i + 1)
                .productId(pid)
                .productName(p != null ? p.getNameRaw() : null)
                .productSpecs(p != null ? p.getSpecsRaw() : null)
                .gain(gain)
                .isRelevant(gain > 0)
                .build());
      }
      retrievedByQuery.put(q, retrievedDocs);

      // groundTruthDocuments 구성 (정답셋 점수순)
      java.util.List<EvaluationReportDetailResponse.GroundTruthDocument> gtDocs =
          relevantMappings.stream()
              .map(
                  m -> {
                    ProductDocument p = productMap.get(m.getProductId());
                    return EvaluationReportDetailResponse.GroundTruthDocument.builder()
                        .productId(m.getProductId())
                        .productName(p != null ? p.getNameRaw() : null)
                        .productSpecs(p != null ? p.getSpecsRaw() : null)
                        .score(m.getRelevanceScore())
                        .build();
                  })
              .sorted(
                  java.util.Comparator.comparing(
                          EvaluationReportDetailResponse.GroundTruthDocument::getScore)
                      .reversed())
              .toList();
      groundTruthByQuery.put(q, gtDocs);
    }

    List<EvaluationReportDetailResponse.QueryDetail> details = new ArrayList<>();
    for (var r : rows) {
      details.add(
          EvaluationReportDetailResponse.QueryDetail.builder()
              .query(r.getQuery())
              .ndcg(r.getNdcg())
              .relevantCount(r.getRelevantCount())
              .retrievedCount(r.getRetrievedCount())
              .correctCount(r.getCorrectCount())
              .retrievedDocuments(retrievedByQuery.getOrDefault(r.getQuery(), java.util.List.of()))
              .groundTruthDocuments(
                  groundTruthByQuery.getOrDefault(r.getQuery(), java.util.List.of()))
              .missingDocuments(missingByQuery.getOrDefault(r.getQuery(), List.of()))
              .wrongDocuments(wrongByQuery.getOrDefault(r.getQuery(), List.of()))
              .build());
    }

    return EvaluationReportDetailResponse.builder()
        .id(report.getId())
        .reportName(report.getReportName())
        .totalQueries(report.getTotalQueries())
        .averageNdcg(report.getAverageNdcg())
        .totalRelevantDocuments(report.getTotalRelevantDocuments())
        .totalRetrievedDocuments(report.getTotalRetrievedDocuments())
        .totalCorrectDocuments(report.getTotalCorrectDocuments())
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
        Set<String> retrieved = getRetrievedDocuments(q.getQuery(), retrievalSize);
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

        // nDCG 계산
        double ndcg = computeNdcg(q.getQuery(), new ArrayList<>(retrieved), relevant);

        out.add(
            PersistedQueryEvaluationDetail.builder()
                .query(q.getQuery())
                .ndcg(ndcg)
                .relevantCount(relevant.size())
                .retrievedCount(retrieved.size())
                .correctCount(correct.size())
                .missingDocuments(missingDocs)
                .wrongDocuments(wrongDocs)
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
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private List<PersistedDocumentInfo> missingDocuments;
    private List<PersistedDocumentInfo> wrongDocuments;
  }
}
