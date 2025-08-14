package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
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
import lombok.Setter;
import lombok.RequiredArgsConstructor;
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
  private final SearchService searchService;
  private final ObjectMapper objectMapper;
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

    double totalPrecision = 0.0;
    double totalRecall = 0.0;
    double totalF1Score = 0.0;
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
      totalPrecision += detail.getPrecision();
      totalRecall += detail.getRecall();
      totalF1Score += detail.getF1Score();
      totalRelevantDocuments += detail.getRelevantCount();
      totalRetrievedDocuments += detail.getRetrievedCount();
      totalCorrectDocuments += detail.getCorrectCount();
    }

    double averagePrecision = queries.isEmpty() ? 0.0 : totalPrecision / queries.size();
    double averageRecall = queries.isEmpty() ? 0.0 : totalRecall / queries.size();
    double averageF1Score = queries.isEmpty() ? 0.0 : totalF1Score / queries.size();

    // 저장용 상세: 상품명/스펙 포함
    List<PersistedQueryEvaluationDetail> persistedDetails =
        buildPersistedDetails(queries, retrievalSize);

    EvaluationReport report =
        saveEvaluationReport(
            reportName,
            queries.size(),
            averagePrecision,
            averageRecall,
            averageF1Score,
            totalRelevantDocuments,
            totalRetrievedDocuments,
            totalCorrectDocuments,
            persistedDetails);

    log.info(
        "✅ 평가 실행 완료: Precision={:.3f}, Recall={:.3f}, F1={:.3f}",
        averagePrecision,
        averageRecall,
        averageF1Score);

    return EvaluationExecuteResponse.builder()
        .reportId(report.getId())
        .reportName(reportName)
        .averagePrecision(averagePrecision)
        .averageRecall(averageRecall)
        .averageF1Score(averageF1Score)
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
    Set<String> retrievedDocs = getRetrievedDocuments(query, retrievalSize);
    Set<String> correctDocs = getIntersection(relevantDocs, retrievedDocs);

    double precision =
        retrievedDocs.isEmpty() ? 0.0 : (double) correctDocs.size() / retrievedDocs.size();
    double recall =
        relevantDocs.isEmpty() ? 0.0 : (double) correctDocs.size() / relevantDocs.size();
    double f1Score =
        (precision + recall) == 0.0 ? 0.0 : 2 * precision * recall / (precision + recall);

    List<String> missingIds =
        relevantDocs.stream().filter(doc -> !retrievedDocs.contains(doc)).collect(Collectors.toList());

    List<String> wrongIds =
        retrievedDocs.stream().filter(doc -> !relevantDocs.contains(doc)).collect(Collectors.toList());

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
        .precision(precision)
        .recall(recall)
        .f1Score(f1Score)
        .relevantCount(relevantDocs.size())
        .retrievedCount(retrievedDocs.size())
        .correctCount(correctDocs.size())
        .missingDocuments(missingDocs)
        .wrongDocuments(wrongDocs)
        .build();
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
      log.info("🔍 실제 검색 API 호출: {}, 검색 결과 개수: {}", query, retrievalSize);

      // 실제 검색 API와 동일한 조건으로 검색 요청 생성
      SearchExecuteRequest searchRequest = new SearchExecuteRequest();
      searchRequest.setQuery(query);
      searchRequest.setPage(1);
      searchRequest.setSize(retrievalSize); // 설정된 개수만큼 결과 조회 (최대 300개)
      searchRequest.setApplyTypoCorrection(true);

      // 실제 검색 API 호출
      SearchExecuteResponse searchResponse = searchService.searchProducts(searchRequest);

      // 검색 결과에서 상품 ID 추출
      Set<String> retrievedProductIds =
          searchResponse.getHits().getData().stream()
              .map(product -> product.getId())
              .collect(Collectors.toSet());

      log.info("✅ 검색 결과: {} 개 상품 조회", retrievedProductIds.size());
      return retrievedProductIds;

    } catch (Exception e) {
      log.error("❌ 검색 API 호출 실패: {}", query, e);
      // 검색 실패 시 빈 셋 반환
      return new HashSet<>();
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
      double avgPrecision,
      double avgRecall,
      double avgF1Score,
      int totalRelevantDocs,
      int totalRetrievedDocs,
      int totalCorrectDocs,
      List<PersistedQueryEvaluationDetail> queryDetails) {
    try {
      // JSON 직렬화 전 데이터 검증
      log.info("📝 리포트 저장 시작: {}, 쿼리 세부사항 {}개", reportName, queryDetails.size());

      // 각 쿼리 세부사항 검증
      for (int i = 0; i < queryDetails.size(); i++) {
        PersistedQueryEvaluationDetail detail = queryDetails.get(i);
        if (detail == null) {
          log.warn("⚠️ null 쿼리 세부사항 발견: index {}", i);
          continue;
        }
        if (detail.getQuery() == null || detail.getQuery().trim().isEmpty()) {
          log.warn("⚠️ 빈 쿼리 발견: index {}", i);
        }
      }

      String detailedResultsJson = objectMapper.writeValueAsString(queryDetails);

      // JSON 유효성 검증
      try {
        objectMapper.readTree(detailedResultsJson);
        log.info("✅ JSON 유효성 검증 완료");
      } catch (Exception e) {
        log.error("❌ 생성된 JSON이 유효하지 않음", e);
        throw new RuntimeException("생성된 JSON이 유효하지 않습니다", e);
      }

      EvaluationReport report =
          EvaluationReport.builder()
              .reportName(reportName)
              .totalQueries(totalQueries)
              .averagePrecision(avgPrecision)
              .averageRecall(avgRecall)
              .averageF1Score(avgF1Score)
              .totalRelevantDocuments(totalRelevantDocs)
              .totalRetrievedDocuments(totalRetrievedDocs)
              .totalCorrectDocuments(totalCorrectDocs)
              .detailedResults(detailedResultsJson)
              .build();

      EvaluationReport savedReport = evaluationReportRepository.save(report);
      log.info("✅ 리포트 저장 완료: ID {}", savedReport.getId());
      return savedReport;

    } catch (JsonProcessingException e) {
      log.error("❌ 리포트 저장 실패: JSON 처리 오류", e);
      throw new RuntimeException("리포트 저장 중 JSON 처리 오류가 발생했습니다", e);
    } catch (Exception e) {
      log.error("❌ 리포트 저장 실패: 일반 오류", e);
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
    return evaluationReportRepository
        .findByReportNameContainingIgnoreCaseOrderByCreatedAtDesc(keyword.trim());
  }

  public EvaluationReport getReportById(Long reportId) {
    return evaluationReportRepository.findById(reportId).orElse(null);
  }

  public EvaluationReportDetailResponse getReportDetail(Long reportId) {
    EvaluationReport report = evaluationReportRepository.findById(reportId).orElse(null);
    if (report == null) return null;

    List<EvaluationReportDetailResponse.QueryDetail> details = new ArrayList<>();
    try {
      var type =
          objectMapper.getTypeFactory()
              .constructCollectionType(List.class, PersistedQueryEvaluationDetail.class);
      List<PersistedQueryEvaluationDetail> raw =
          objectMapper.readValue(report.getDetailedResults(), type);
      for (PersistedQueryEvaluationDetail r : raw) {
        List<EvaluationReportDetailResponse.DocumentInfo> missing = new ArrayList<>();
        if (r.getMissingDocuments() != null) {
          for (PersistedDocumentInfo d : r.getMissingDocuments()) {
            missing.add(
                EvaluationReportDetailResponse.DocumentInfo.builder()
                    .productId(d.getProductId())
                    .productName(d.getProductName())
                    .productSpecs(d.getProductSpecs())
                    .build());
          }
        }
        List<EvaluationReportDetailResponse.DocumentInfo> wrong = new ArrayList<>();
        if (r.getWrongDocuments() != null) {
          for (PersistedDocumentInfo d : r.getWrongDocuments()) {
            wrong.add(
                EvaluationReportDetailResponse.DocumentInfo.builder()
                    .productId(d.getProductId())
                    .productName(d.getProductName())
                    .productSpecs(d.getProductSpecs())
                    .build());
          }
        }

        details.add(
            EvaluationReportDetailResponse.QueryDetail.builder()
                .query(r.getQuery())
                .precision(r.getPrecision())
                .recall(r.getRecall())
                .f1Score(r.getF1Score())
                .relevantCount(r.getRelevantCount())
                .retrievedCount(r.getRetrievedCount())
                .correctCount(r.getCorrectCount())
                .missingDocuments(missing)
                .wrongDocuments(wrong)
                .build());
      }
    } catch (Exception e) {
      log.error("상세 JSON 파싱 실패: reportId={}", reportId, e);
      throw new RuntimeException("리포트 상세를 파싱할 수 없습니다", e);
    }

    return EvaluationReportDetailResponse.builder()
        .id(report.getId())
        .reportName(report.getReportName())
        .totalQueries(report.getTotalQueries())
        .averagePrecision(report.getAveragePrecision())
        .averageRecall(report.getAverageRecall())
        .averageF1Score(report.getAverageF1Score())
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
    evaluationReportRepository.deleteById(reportId);
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

        // 점수 계산 재사용
        double precision = retrieved.isEmpty() ? 0.0 : (double) correct.size() / retrieved.size();
        double recall = relevant.isEmpty() ? 0.0 : (double) correct.size() / relevant.size();
        double f1 =
            (precision + recall) == 0.0 ? 0.0 : 2 * precision * recall / (precision + recall);

        out.add(
            PersistedQueryEvaluationDetail.builder()
                .query(q.getQuery())
                .precision(precision)
                .recall(recall)
                .f1Score(f1)
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
    private Double precision;
    private Double recall;
    private Double f1Score;
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private List<PersistedDocumentInfo> missingDocuments;
    private List<PersistedDocumentInfo> wrongDocuments;
  }
}
