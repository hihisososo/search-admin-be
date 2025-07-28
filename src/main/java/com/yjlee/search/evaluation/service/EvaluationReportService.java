package com.yjlee.search.evaluation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.model.RelevanceStatus;
import com.yjlee.search.evaluation.repository.EvaluationReportRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.service.SearchService;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
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
            queryDetails);

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

    List<String> missingDocs =
        relevantDocs.stream()
            .filter(doc -> !retrievedDocs.contains(doc))
            .collect(Collectors.toList());

    List<String> wrongDocs =
        retrievedDocs.stream()
            .filter(doc -> !relevantDocs.contains(doc))
            .collect(Collectors.toList());

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
      List<EvaluationExecuteResponse.QueryEvaluationDetail> queryDetails) {
    try {
      // JSON 직렬화 전 데이터 검증
      log.info("📝 리포트 저장 시작: {}, 쿼리 세부사항 {}개", reportName, queryDetails.size());

      // 각 쿼리 세부사항 검증
      for (int i = 0; i < queryDetails.size(); i++) {
        EvaluationExecuteResponse.QueryEvaluationDetail detail = queryDetails.get(i);
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

  public EvaluationReport getReportById(Long reportId) {
    return evaluationReportRepository.findById(reportId).orElse(null);
  }
}
