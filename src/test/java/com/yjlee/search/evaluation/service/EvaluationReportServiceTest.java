package com.yjlee.search.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.common.service.ProductBulkFetchService;
import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationReportDetailRepository;
import com.yjlee.search.evaluation.repository.EvaluationReportDocumentRepository;
import com.yjlee.search.evaluation.repository.EvaluationReportRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchHitsDto;
import com.yjlee.search.search.dto.SearchMode;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.IndexResolver;
import com.yjlee.search.search.service.SearchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluationReportService 테스트")
class EvaluationReportServiceTest {

  @Mock private EvaluationQueryService evaluationQueryService;

  @Mock private QueryProductMappingRepository queryProductMappingRepository;

  @Mock private EvaluationReportRepository evaluationReportRepository;

  @Mock private EvaluationReportDetailRepository reportDetailRepository;

  @Mock private EvaluationReportDocumentRepository reportDocumentRepository;

  @Mock private SearchService searchService;

  @Mock private ElasticsearchClient elasticsearchClient;

  @Mock private IndexResolver indexResolver;

  @Mock private EvaluationReportPersistenceService persistenceService;

  @Mock private ProductBulkFetchService productBulkFetchService;

  @Mock private ProgressCallback progressCallback;

  private EvaluationReportService evaluationReportService;

  @BeforeEach
  void setUp() {
    evaluationReportService =
        new EvaluationReportService(
            evaluationQueryService,
            queryProductMappingRepository,
            evaluationReportRepository,
            reportDetailRepository,
            reportDocumentRepository,
            searchService,
            elasticsearchClient,
            indexResolver,
            persistenceService,
            productBulkFetchService);
    // Spy로 만들기
    evaluationReportService = org.mockito.Mockito.spy(evaluationReportService);
  }

  @Test
  @DisplayName("평가 실행 - 기본 설정")
  void testExecuteEvaluationDefault() {
    // given
    String reportName = "Test Report";
    List<EvaluationQuery> queries = createMockQueries("노트북", "태블릿");

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // evaluateQuery 메서드 mock
    EvaluationExecuteResponse.QueryEvaluationDetail detail1 =
        createMockQueryDetail("노트북", 0.8, 0.6);
    EvaluationExecuteResponse.QueryEvaluationDetail detail2 =
        createMockQueryDetail("태블릿", 0.7, 0.5);

    doReturn(detail1)
        .when(evaluationReportService)
        .evaluateQuery(eq("노트북"), any(SearchMode.class), anyInt(), anyInt());
    doReturn(detail2)
        .when(evaluationReportService)
        .evaluateQuery(eq("태블릿"), any(SearchMode.class), anyInt(), anyInt());

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(reportName, progressCallback);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getReportName()).isEqualTo(reportName);
    assertThat(response.getQueryDetails()).hasSize(2);
    verify(evaluationQueryService).getAllQueries();
  }

  @Test
  @DisplayName("평가 실행 - 커스텀 설정")
  void testExecuteEvaluationCustomSettings() {
    // given
    String reportName = "Custom Report";
    SearchMode searchMode = SearchMode.HYBRID_RRF;
    Integer rrfK = 30;
    Integer hybridTopK = 200;

    List<EvaluationQuery> queries = createMockQueries("노트북");

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // evaluateQuery 메서드 mock
    EvaluationExecuteResponse.QueryEvaluationDetail detail = createMockQueryDetail("노트북", 0.9, 0.7);

    doReturn(detail)
        .when(evaluationReportService)
        .evaluateQuery(eq("노트북"), eq(searchMode), eq(rrfK), eq(hybridTopK));

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(
            reportName, searchMode, rrfK, hybridTopK, progressCallback);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getReportName()).isEqualTo(reportName);
    assertThat(response.getQueryDetails()).hasSize(1);
  }

  @Test
  @DisplayName("평가 실행 - 빈 쿼리 리스트")
  void testExecuteEvaluationEmptyQueries() {
    // given
    String reportName = "Empty Report";
    List<EvaluationQuery> emptyQueries = new ArrayList<>();

    when(evaluationQueryService.getAllQueries()).thenReturn(emptyQueries);

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(reportName, progressCallback);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getReportName()).isEqualTo(reportName);
    assertThat(response.getQueryDetails()).isEmpty();
    assertThat(response.getRecall300()).isEqualTo(0.0);
    assertThat(response.getPrecision20()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("평가 실행 - 진행률 콜백 호출")
  void testExecuteEvaluationProgressCallback() {
    // given
    String reportName = "Progress Report";
    List<EvaluationQuery> queries = createMockQueries("query1", "query2", "query3");

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // evaluateQuery 메서드 mock
    EvaluationExecuteResponse.QueryEvaluationDetail detail1 =
        createMockQueryDetail("query1", 0.8, 0.6);
    EvaluationExecuteResponse.QueryEvaluationDetail detail2 =
        createMockQueryDetail("query2", 0.7, 0.5);
    EvaluationExecuteResponse.QueryEvaluationDetail detail3 =
        createMockQueryDetail("query3", 0.9, 0.7);

    doReturn(detail1)
        .when(evaluationReportService)
        .evaluateQuery(eq("query1"), any(SearchMode.class), anyInt(), anyInt());
    doReturn(detail2)
        .when(evaluationReportService)
        .evaluateQuery(eq("query2"), any(SearchMode.class), anyInt(), anyInt());
    doReturn(detail3)
        .when(evaluationReportService)
        .evaluateQuery(eq("query3"), any(SearchMode.class), anyInt(), anyInt());

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(reportName, progressCallback);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getQueryDetails()).hasSize(3);
    // 진행률 콜백이 호출되었는지 확인 (최소 한 번은 호출됨)
    verify(progressCallback, times(3)).updateProgress(anyInt(), anyString());
  }

  @Test
  @DisplayName("평가 실행 - 검색 실패 처리")
  void testExecuteEvaluationSearchFailure() {
    // given
    String reportName = "Error Report";
    List<EvaluationQuery> queries = createMockQueries("error_query");

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // evaluateQuery에서 예외 발생 시뮬레이션
    when(evaluationReportService.evaluateQuery(
            eq("error_query"), any(SearchMode.class), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("Search failed"));

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(reportName, progressCallback);

    // then
    assertThat(response).isNotNull();
    // 예외가 발생해도 평가는 계속 진행
    assertThat(response.getReportName()).isEqualTo(reportName);
    assertThat(response.getQueryDetails()).isEmpty();
  }

  @Test
  @DisplayName("평가 실행 - Recall/Precision 계산")
  void testExecuteEvaluationMetricsCalculation() {
    // given
    String reportName = "Metrics Report";
    List<EvaluationQuery> queries = createMockQueries("query1");

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // evaluateQuery 메서드 mock - 직접 Recall/Precision 값 설정
    EvaluationExecuteResponse.QueryEvaluationDetail detail =
        createMockQueryDetail("query1", 1.0, 0.6);

    doReturn(detail)
        .when(evaluationReportService)
        .evaluateQuery(eq("query1"), any(SearchMode.class), anyInt(), anyInt());

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(reportName, progressCallback);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getQueryDetails()).hasSize(1);

    EvaluationExecuteResponse.QueryEvaluationDetail resultDetail =
        response.getQueryDetails().get(0);
    assertThat(resultDetail.getQuery()).isEqualTo("query1");
    // Recall@300 = 1.0
    assertThat(resultDetail.getRecallAt300()).isEqualTo(1.0);
    // Precision@20 = 0.6
    assertThat(resultDetail.getPrecisionAt20()).isEqualTo(0.6);
  }

  @Test
  @DisplayName("평가 실행 - 병렬 처리")
  void testExecuteEvaluationParallelProcessing() {
    // given
    String reportName = "Parallel Report";
    // 많은 쿼리로 병렬 처리 테스트
    List<EvaluationQuery> queries = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      queries.add(createMockQuery("query" + i));
    }

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // 각 쿼리에 대한 evaluateQuery mock
    for (int i = 0; i < 10; i++) {
      EvaluationExecuteResponse.QueryEvaluationDetail detail =
          createMockQueryDetail("query" + i, 0.7 + i * 0.01, 0.5 + i * 0.01);
      doReturn(detail)
          .when(evaluationReportService)
          .evaluateQuery(eq("query" + i), any(SearchMode.class), anyInt(), anyInt());
    }

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(
            reportName, SearchMode.KEYWORD_ONLY, 60, 100, progressCallback);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getQueryDetails()).hasSize(10);
  }

  @Test
  @DisplayName("평가 실행 - null 진행률 콜백")
  void testExecuteEvaluationNullProgressCallback() {
    // given
    String reportName = "No Progress Report";
    List<EvaluationQuery> queries = createMockQueries("query1");

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // evaluateQuery 메서드 mock
    EvaluationExecuteResponse.QueryEvaluationDetail detail =
        createMockQueryDetail("query1", 0.8, 0.6);

    doReturn(detail)
        .when(evaluationReportService)
        .evaluateQuery(eq("query1"), any(SearchMode.class), anyInt(), anyInt());

    // when - null 콜백으로 실행
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(reportName, null);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getReportName()).isEqualTo(reportName);
    assertThat(response.getQueryDetails()).hasSize(1);
  }

  @Test
  @DisplayName("평가 실행 - 제품 정보 조회")
  void testExecuteEvaluationWithProductFetch() {
    // given
    String reportName = "Product Fetch Report";
    List<EvaluationQuery> queries = createMockQueries("query1");

    when(evaluationQueryService.getAllQueries()).thenReturn(queries);

    // evaluateQuery 메서드 mock
    EvaluationExecuteResponse.QueryEvaluationDetail detail =
        createMockQueryDetail("query1", 0.5, 0.4);
    detail.setMissingDocuments(
        Arrays.asList(
            EvaluationExecuteResponse.DocumentInfo.builder()
                .productId("P001")
                .productName("노트북")
                .build()));

    doReturn(detail)
        .when(evaluationReportService)
        .evaluateQuery(eq("query1"), any(SearchMode.class), anyInt(), anyInt());

    // when
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(reportName, progressCallback);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getQueryDetails()).hasSize(1);
    assertThat(response.getQueryDetails().get(0).getMissingDocuments()).hasSize(1);
  }

  private List<EvaluationQuery> createMockQueries(String... queries) {
    List<EvaluationQuery> result = new ArrayList<>();
    for (String query : queries) {
      result.add(createMockQuery(query));
    }
    return result;
  }

  private EvaluationQuery createMockQuery(String query) {
    return EvaluationQuery.builder().query(query).build();
  }

  private void mockSearchResponses() {
    List<ProductDto> products = createMockProducts("P001", "P002");
    SearchExecuteResponse response =
        SearchExecuteResponse.builder()
            .hits(SearchHitsDto.builder().data(products).total(2L).build())
            .build();
    when(searchService.searchProductsSimulation(any(SearchSimulationRequest.class)))
        .thenReturn(response);
  }

  private void mockQueryProductMappings() {
    when(queryProductMappingRepository.findByEvaluationQuery(any(EvaluationQuery.class)))
        .thenReturn(new ArrayList<>());
  }

  private List<ProductDto> createMockProducts(String... ids) {
    List<ProductDto> products = new ArrayList<>();
    for (String id : ids) {
      ProductDto product = ProductDto.builder().id(id).name("Product " + id).build();
      products.add(product);
    }
    return products;
  }

  private QueryProductMapping createMapping(String query, String productId) {
    EvaluationQuery eq = createMockQuery(query);
    return QueryProductMapping.builder().evaluationQuery(eq).productId(productId).build();
  }

  private ProductDocument createProductDocument(String id, String name) {
    return ProductDocument.builder().id(id).name(name).build();
  }

  private EvaluationExecuteResponse.QueryEvaluationDetail createMockQueryDetail(
      String query, double recall, double precision) {
    EvaluationExecuteResponse.QueryEvaluationDetail detail =
        new EvaluationExecuteResponse.QueryEvaluationDetail();
    detail.setQuery(query);
    detail.setRecallAt300(recall);
    detail.setPrecisionAt20(precision);
    detail.setCorrectCount((int) (recall * 10)); // 임의 값
    detail.setRelevantCount(10);
    detail.setRetrievedCount(20);
    detail.setMissingDocuments(new ArrayList<>());
    detail.setWrongDocuments(new ArrayList<>());
    return detail;
  }
}
