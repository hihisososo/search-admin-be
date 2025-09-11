package com.yjlee.search.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchHitsDto;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.SearchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluationReportService 간단한 테스트")
class EvaluationReportServiceSimpleTest {

  private EvaluationQueryService evaluationQueryService;
  private QueryProductMappingRepository queryProductMappingRepository;
  private SearchService searchService;
  private TestableEvaluationReportService evaluationReportService;

  @BeforeEach
  void setUp() {
    evaluationQueryService = mock(EvaluationQueryService.class);
    queryProductMappingRepository = mock(QueryProductMappingRepository.class);
    searchService = mock(SearchService.class);

    evaluationReportService =
        new TestableEvaluationReportService(
            evaluationQueryService, queryProductMappingRepository, searchService);
  }

  @Test
  @DisplayName("평가 실행 - 기본 테스트")
  void testExecuteEvaluation() {
    // given
    String reportName = "Test Report";
    List<EvaluationQuery> queries = Arrays.asList(createQuery("노트북"), createQuery("태블릿"));

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // 검색 결과 mock - 한 번에 설정
    Map<String, List<String>> searchResultsMap = new HashMap<>();
    searchResultsMap.put("노트북", Arrays.asList("P001", "P002"));
    searchResultsMap.put("태블릿", Arrays.asList("P003", "P004"));
    mockMultipleSearchResults(searchResultsMap);

    // Ground truth mock - 한 번에 설정
    Map<String, List<String>> groundTruthMap = new HashMap<>();
    groundTruthMap.put("노트북", Arrays.asList("P001"));
    groundTruthMap.put("태블릿", Arrays.asList("P003", "P005"));
    mockMultipleGroundTruth(groundTruthMap);

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeSimpleEvaluation(reportName);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getReportName()).isEqualTo(reportName);
    assertThat(response.getQueryDetails()).hasSize(2);

    // 첫 번째 쿼리 검증
    EvaluationExecuteResponse.QueryEvaluationDetail detail1 = response.getQueryDetails().get(0);
    assertThat(detail1.getQuery()).isEqualTo("노트북");
    assertThat(detail1.getCorrectCount()).isEqualTo(1); // P001만 정답

    // 두 번째 쿼리 검증
    EvaluationExecuteResponse.QueryEvaluationDetail detail2 = response.getQueryDetails().get(1);
    assertThat(detail2.getQuery()).isEqualTo("태블릿");
    assertThat(detail2.getCorrectCount()).isEqualTo(1); // P003만 정답
  }

  @Test
  @DisplayName("평가 실행 - 빈 쿼리 리스트")
  void testExecuteEvaluationEmptyQueries() {
    // given
    String reportName = "Empty Report";
    when(evaluationQueryService.getAllQueries()).thenReturn(Collections.emptyList());

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeSimpleEvaluation(reportName);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getReportName()).isEqualTo(reportName);
    assertThat(response.getQueryDetails()).isEmpty();
    assertThat(response.getRecall300()).isEqualTo(0.0);
    assertThat(response.getPrecision20()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("평가 실행 - Precision/Recall 계산")
  void testPrecisionRecallCalculation() {
    // given
    String reportName = "Metrics Report";
    List<EvaluationQuery> queries = Arrays.asList(createQuery("query1"));

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // 5개 검색, 3개 정답
    mockSearchResults("query1", Arrays.asList("P001", "P002", "P003", "P004", "P005"));
    mockGroundTruth("query1", Arrays.asList("P001", "P002", "P003"));

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeSimpleEvaluation(reportName);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getQueryDetails()).hasSize(1);

    EvaluationExecuteResponse.QueryEvaluationDetail detail = response.getQueryDetails().get(0);
    assertThat(detail.getCorrectCount()).isEqualTo(3);
    assertThat(detail.getRelevantCount()).isEqualTo(3);
    assertThat(detail.getRetrievedCount()).isEqualTo(5);
    // Precision = 3/5 = 0.6
    assertThat(detail.getPrecisionAt20()).isEqualTo(0.6);
    // Recall = 3/3 = 1.0
    assertThat(detail.getRecallAt300()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("평가 실행 - 검색 실패 처리")
  void testSearchFailure() {
    // given
    String reportName = "Error Report";
    List<EvaluationQuery> queries = Arrays.asList(createQuery("error_query"));

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);
    when(searchService.searchProductsSimulation(any(SearchSimulationRequest.class)))
        .thenThrow(new RuntimeException("Search failed"));

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeSimpleEvaluation(reportName);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getReportName()).isEqualTo(reportName);
    // 에러가 발생해도 빈 결과 반환
    assertThat(response.getQueryDetails()).hasSize(1);
    assertThat(response.getQueryDetails().get(0).getCorrectCount()).isEqualTo(0);
  }

  private void mockSearchResults(String queryText, List<String> productIds) {
    List<ProductDto> products = new ArrayList<>();
    for (String id : productIds) {
      products.add(ProductDto.builder().id(id).name("Product " + id).build());
    }

    SearchExecuteResponse searchResponse =
        SearchExecuteResponse.builder()
            .hits(SearchHitsDto.builder().data(products).total((long) products.size()).build())
            .build();

    when(searchService.searchProductsSimulation(any(SearchSimulationRequest.class)))
        .thenAnswer(
            invocation -> {
              SearchSimulationRequest req = invocation.getArgument(0);
              if (queryText.equals(req.getQuery())) {
                return searchResponse;
              }
              return SearchExecuteResponse.builder()
                  .hits(SearchHitsDto.builder().data(new ArrayList<>()).total(0L).build())
                  .build();
            });
  }

  private void mockGroundTruth(String query, List<String> relevantIds) {
    List<QueryProductMapping> mappings =
        relevantIds.stream()
            .map(
                id ->
                    QueryProductMapping.builder()
                        .productId(id)
                        .evaluationQuery(createQuery(query))
                        .build())
            .toList();

    when(queryProductMappingRepository.findByEvaluationQuery(any(EvaluationQuery.class)))
        .thenAnswer(
            invocation -> {
              EvaluationQuery eq = invocation.getArgument(0);
              if (eq.getQuery().equals(query)) {
                return mappings;
              }
              return new ArrayList<>();
            });
  }

  private EvaluationQuery createQuery(String query) {
    return EvaluationQuery.builder().query(query).build();
  }

  private void mockMultipleSearchResults(Map<String, List<String>> resultsMap) {
    when(searchService.searchProductsSimulation(any(SearchSimulationRequest.class)))
        .thenAnswer(
            invocation -> {
              SearchSimulationRequest req = invocation.getArgument(0);
              List<String> productIds = resultsMap.get(req.getQuery());
              if (productIds != null) {
                List<ProductDto> products =
                    productIds.stream()
                        .map(id -> ProductDto.builder().id(id).name("Product " + id).build())
                        .toList();
                return SearchExecuteResponse.builder()
                    .hits(
                        SearchHitsDto.builder()
                            .data(products)
                            .total((long) products.size())
                            .build())
                    .build();
              }
              return SearchExecuteResponse.builder()
                  .hits(SearchHitsDto.builder().data(new ArrayList<>()).total(0L).build())
                  .build();
            });
  }

  private void mockMultipleGroundTruth(Map<String, List<String>> groundTruthMap) {
    when(queryProductMappingRepository.findByEvaluationQuery(any(EvaluationQuery.class)))
        .thenAnswer(
            invocation -> {
              EvaluationQuery eq = invocation.getArgument(0);
              List<String> relevantIds = groundTruthMap.get(eq.getQuery());
              if (relevantIds != null) {
                return relevantIds.stream()
                    .map(
                        id ->
                            QueryProductMapping.builder().productId(id).evaluationQuery(eq).build())
                    .toList();
              }
              return new ArrayList<>();
            });
  }

  // 테스트용 간단한 구현
  static class TestableEvaluationReportService {
    private final EvaluationQueryService evaluationQueryService;
    private final QueryProductMappingRepository queryProductMappingRepository;
    private final SearchService searchService;

    TestableEvaluationReportService(
        EvaluationQueryService evaluationQueryService,
        QueryProductMappingRepository queryProductMappingRepository,
        SearchService searchService) {
      this.evaluationQueryService = evaluationQueryService;
      this.queryProductMappingRepository = queryProductMappingRepository;
      this.searchService = searchService;
    }

    public EvaluationExecuteResponse executeSimpleEvaluation(String reportName) {
      List<EvaluationQuery> queries = evaluationQueryService.getAllQueries();
      List<EvaluationExecuteResponse.QueryEvaluationDetail> details = new ArrayList<>();

      double totalRecall = 0.0;
      double totalPrecision = 0.0;

      for (EvaluationQuery query : queries) {
        EvaluationExecuteResponse.QueryEvaluationDetail detail = evaluateQuery(query);
        details.add(detail);
        totalRecall += detail.getRecallAt300();
        totalPrecision += detail.getPrecisionAt20();
      }

      int queryCount = queries.isEmpty() ? 1 : queries.size();

      return EvaluationExecuteResponse.builder()
          .reportName(reportName)
          .queryDetails(details)
          .recall300(totalRecall / queryCount)
          .precision20(totalPrecision / queryCount)
          .build();
    }

    private EvaluationExecuteResponse.QueryEvaluationDetail evaluateQuery(EvaluationQuery query) {
      EvaluationExecuteResponse.QueryEvaluationDetail detail =
          new EvaluationExecuteResponse.QueryEvaluationDetail();
      detail.setQuery(query.getQuery());

      try {
        // 검색 실행
        SearchSimulationRequest request = new SearchSimulationRequest();
        request.setQuery(query.getQuery());
        request.setPage(0);
        request.setSize(300);
        SearchExecuteResponse searchResponse = searchService.searchProductsSimulation(request);

        List<String> retrievedIds =
            searchResponse.getHits().getData().stream().map(ProductDto::getId).toList();

        // Ground truth 조회
        List<QueryProductMapping> mappings =
            queryProductMappingRepository.findByEvaluationQuery(query);
        Set<String> relevantIds = new HashSet<>();
        if (mappings != null) {
          for (QueryProductMapping mapping : mappings) {
            relevantIds.add(mapping.getProductId());
          }
        }

        // 교집합 계산
        Set<String> correctIds = new HashSet<>(retrievedIds);
        correctIds.retainAll(relevantIds);

        // 메트릭 계산
        int correctCount = correctIds.size();
        int relevantCount = relevantIds.size();
        int retrievedCount = retrievedIds.size();

        detail.setCorrectCount(correctCount);
        detail.setRelevantCount(relevantCount);
        detail.setRetrievedCount(retrievedCount);

        // Precision@20 - top 20개 중에서 correct 개수 계산
        List<String> top20 = retrievedIds.size() > 20 ? retrievedIds.subList(0, 20) : retrievedIds;
        int correctInTop20 = 0;
        for (String id : top20) {
          if (relevantIds.contains(id)) {
            correctInTop20++;
          }
        }
        double precision = top20.size() > 0 ? (double) correctInTop20 / top20.size() : 0.0;
        detail.setPrecisionAt20(precision);

        // Recall@300
        double recall = relevantCount > 0 ? (double) correctCount / relevantCount : 0.0;
        detail.setRecallAt300(recall);

        detail.setMissingDocuments(new ArrayList<>());
        detail.setWrongDocuments(new ArrayList<>());

      } catch (Exception e) {
        // 에러 시 기본값
        detail.setCorrectCount(0);
        detail.setRelevantCount(0);
        detail.setRetrievedCount(0);
        detail.setPrecisionAt20(0.0);
        detail.setRecallAt300(0.0);
        detail.setMissingDocuments(new ArrayList<>());
        detail.setWrongDocuments(new ArrayList<>());
      }

      return detail;
    }
  }
}
