package com.yjlee.search.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchItem;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.search.converter.ProductDtoConverter;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchMode;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.builder.QueryBuilder;
import com.yjlee.search.search.service.builder.QueryResponseBuilder;
import com.yjlee.search.search.service.builder.SearchRequestBuilder;
import com.yjlee.search.search.service.builder.query.FilterQueryBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HybridSearchService 테스트")
class HybridSearchServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;

  @Mock private QueryBuilder queryBuilder;

  @Mock private SearchRequestBuilder searchRequestBuilder;

  @Mock private QueryResponseBuilder responseBuilder;

  @Mock private SearchQueryExecutor queryExecutor;

  @Mock private VectorSearchService vectorSearchService;

  @Mock private RRFScorer rrfScorer;

  @Mock private ProductDtoConverter productDtoConverter;

  @Mock private FilterQueryBuilder filterQueryBuilder;

  @Mock private MsearchResponse<JsonNode> msearchResponse;

  private HybridSearchService hybridSearchService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    hybridSearchService =
        new HybridSearchService(
            elasticsearchClient,
            queryBuilder,
            searchRequestBuilder,
            responseBuilder,
            queryExecutor,
            vectorSearchService,
            rrfScorer,
            objectMapper,
            productDtoConverter,
            filterQueryBuilder);

    // vectorSearchService mock 설정
    float[] mockEmbedding = new float[] {0.1f, 0.2f, 0.3f};
    when(vectorSearchService.getQueryEmbedding(anyString())).thenReturn(mockEmbedding);
    when(vectorSearchService.getDefaultVectorMinScore()).thenReturn(0.5);

    // queryBuilder mock 설정
    co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery mockBoolQuery =
        co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.of(b -> b);
    when(queryBuilder.buildBoolQuery(any(), any())).thenReturn(mockBoolQuery);

    // productDtoConverter mock 설정
    when(productDtoConverter.convert(anyString(), anyDouble(), any()))
        .thenAnswer(
            invocation -> {
              String id = invocation.getArgument(0);
              return ProductDto.builder().id(id).name("Product " + id).build();
            });
  }

  @Test
  @DisplayName("하이브리드 검색 - 정상 케이스")
  void testHybridSearchSuccess() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(60);
    request.setHybridTopK(100);
    request.setVectorMinScore(0.6);
    request.setBm25Weight(0.7);
    request.setPage(0);
    request.setSize(20);

    List<Hit<JsonNode>> bm25Hits = createMockHits("doc1", "doc2", "doc3");
    List<Hit<JsonNode>> vectorHits = createMockHits("doc2", "doc3", "doc4");

    mockMultiSearchResponse(bm25Hits, vectorHits);

    List<RRFScorer.RRFResult> rrfResults = createRRFResults("doc2", "doc3", "doc1");
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), anyInt(), anyInt(), anyDouble()))
        .thenReturn(rrfResults);

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, false);

    // then
    assertThat(response).isNotNull();
    verify(elasticsearchClient).msearch(any(MsearchRequest.class), eq(JsonNode.class));
    verify(rrfScorer).mergeWithRRF(eq(bm25Hits), eq(vectorHits), eq(60), eq(100), eq(0.7));
  }

  @Test
  @DisplayName("하이브리드 검색 - 시뮬레이션 모드")
  void testHybridSearchSimulation() throws Exception {
    // given
    String indexName = "products-dev";
    SearchSimulationRequest request = new SearchSimulationRequest();
    request.setQuery("태블릿");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setEnvironmentType(EnvironmentType.DEV);
    request.setRrfK(60);
    request.setHybridTopK(50);
    request.setVectorMinScore(0.65);
    request.setBm25Weight(0.5);
    request.setPage(0);
    request.setSize(20);

    List<Hit<JsonNode>> bm25Hits = createMockHits("doc1");
    List<Hit<JsonNode>> vectorHits = createMockHits("doc1");

    mockMultiSearchResponse(bm25Hits, vectorHits);

    List<RRFScorer.RRFResult> rrfResults = createRRFResults("doc1");
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), anyInt(), anyInt(), anyDouble()))
        .thenReturn(rrfResults);

    // buildHybridResponse는 private 메소드이므로 mock 불필요

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, true);

    // then
    assertThat(response).isNotNull();
    verify(elasticsearchClient).msearch(any(MsearchRequest.class), eq(JsonNode.class));
  }

  @Test
  @DisplayName("하이브리드 검색 - BM25 결과만 있는 경우")
  void testHybridSearchOnlyBM25Results() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("특수제품");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(60);
    request.setHybridTopK(100);
    request.setVectorMinScore(0.8); // 높은 임계값
    request.setBm25Weight(0.7);
    request.setPage(0);
    request.setSize(20);

    List<Hit<JsonNode>> bm25Hits = createMockHits("doc1", "doc2");
    List<Hit<JsonNode>> vectorHits = new ArrayList<>(); // 빈 벡터 결과

    mockMultiSearchResponse(bm25Hits, vectorHits);

    List<RRFScorer.RRFResult> rrfResults = createRRFResults("doc1", "doc2");
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), anyInt(), anyInt(), anyDouble()))
        .thenReturn(rrfResults);

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, false);

    // then
    assertThat(response).isNotNull();
    verify(rrfScorer).mergeWithRRF(eq(bm25Hits), eq(vectorHits), eq(60), eq(100), eq(0.7));
  }

  @Test
  @DisplayName("하이브리드 검색 - Vector 결과만 있는 경우")
  void testHybridSearchOnlyVectorResults() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("이미지검색");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(60);
    request.setHybridTopK(100);
    request.setVectorMinScore(0.5);
    request.setBm25Weight(0.7);
    request.setPage(0);
    request.setSize(20);

    List<Hit<JsonNode>> bm25Hits = new ArrayList<>(); // 빈 BM25 결과
    List<Hit<JsonNode>> vectorHits = createMockHits("doc3", "doc4");

    mockMultiSearchResponse(bm25Hits, vectorHits);

    List<RRFScorer.RRFResult> rrfResults = createRRFResults("doc3", "doc4");
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), anyInt(), anyInt(), anyDouble()))
        .thenReturn(rrfResults);

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, false);

    // then
    assertThat(response).isNotNull();
    verify(rrfScorer).mergeWithRRF(eq(bm25Hits), eq(vectorHits), eq(60), eq(100), eq(0.7));
  }

  @Test
  @DisplayName("하이브리드 검색 - 결과가 없는 경우")
  void testHybridSearchNoResults() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("존재하지않는상품");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(60);
    request.setHybridTopK(100);
    request.setVectorMinScore(0.6);
    request.setBm25Weight(0.7);
    request.setPage(0);
    request.setSize(20);

    List<Hit<JsonNode>> emptyHits = new ArrayList<>();
    mockMultiSearchResponse(emptyHits, emptyHits);

    List<RRFScorer.RRFResult> emptyRrfResults = new ArrayList<>();
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), anyInt(), anyInt(), anyDouble()))
        .thenReturn(emptyRrfResults);

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, false);

    // then
    assertThat(response).isNotNull();
    verify(rrfScorer).mergeWithRRF(eq(emptyHits), eq(emptyHits), eq(60), eq(100), eq(0.7));
  }

  @Test
  @DisplayName("하이브리드 검색 - IOException 발생")
  void testHybridSearchIOException() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(60);
    request.setHybridTopK(100);
    request.setPage(0);
    request.setSize(20);

    when(elasticsearchClient.msearch(any(MsearchRequest.class), eq(JsonNode.class)))
        .thenThrow(new IOException("Connection failed"));

    // when & then
    assertThatThrownBy(() -> hybridSearchService.hybridSearch(indexName, request, false))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Hybrid search failed")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("하이브리드 검색 - explain 모드")
  void testHybridSearchWithExplain() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(60);
    request.setHybridTopK(100);
    request.setVectorMinScore(0.6);
    request.setBm25Weight(0.7);
    request.setPage(0);
    request.setSize(20);

    List<Hit<JsonNode>> bm25Hits = createMockHits("doc1");
    List<Hit<JsonNode>> vectorHits = createMockHits("doc1");

    mockMultiSearchResponse(bm25Hits, vectorHits);

    List<RRFScorer.RRFResult> rrfResults = createRRFResults("doc1");
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), anyInt(), anyInt(), anyDouble()))
        .thenReturn(rrfResults);

    // buildHybridResponse는 private 메소드이므로 mock 불필요

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, true);

    // then
    assertThat(response).isNotNull();
    // buildHybridResponse는 내부 메소드이므로 verify 불필요
  }

  @Test
  @DisplayName("하이브리드 검색 - MsearchResponse가 불완전한 경우")
  void testHybridSearchIncompleteResponse() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(60);
    request.setHybridTopK(100);
    request.setVectorMinScore(0.6);
    request.setBm25Weight(0.7);
    request.setPage(0);
    request.setSize(20);

    // 응답이 하나만 있는 경우
    List<MultiSearchResponseItem<JsonNode>> responses = new ArrayList<>();
    MultiSearchResponseItem<JsonNode> item1 = createMockResponseItem(createMockHits("doc1"));
    responses.add(item1);

    when(msearchResponse.responses()).thenReturn(responses);
    when(elasticsearchClient.msearch(any(MsearchRequest.class), eq(JsonNode.class)))
        .thenReturn(msearchResponse);

    List<RRFScorer.RRFResult> rrfResults = createRRFResults("doc1");
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), anyInt(), anyInt(), anyDouble()))
        .thenReturn(rrfResults);

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, false);

    // then
    assertThat(response).isNotNull();
  }

  @Test
  @DisplayName("하이브리드 검색 - 다양한 가중치 설정")
  void testHybridSearchWithDifferentWeights() throws Exception {
    // given
    String indexName = "products";
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery("노트북");
    request.setSearchMode(SearchMode.HYBRID_RRF);
    request.setRrfK(30); // 다른 K 값
    request.setHybridTopK(200); // 더 큰 TopK
    request.setVectorMinScore(0.4);
    request.setBm25Weight(0.3); // 벡터에 더 높은 가중치
    request.setPage(0);
    request.setSize(20);

    List<Hit<JsonNode>> bm25Hits = createMockHits("doc1", "doc2");
    List<Hit<JsonNode>> vectorHits = createMockHits("doc3", "doc4");

    mockMultiSearchResponse(bm25Hits, vectorHits);

    List<RRFScorer.RRFResult> rrfResults = createRRFResults("doc3", "doc4", "doc1", "doc2");
    when(rrfScorer.mergeWithRRF(anyList(), anyList(), eq(30), eq(200), eq(0.3)))
        .thenReturn(rrfResults);

    // when
    SearchExecuteResponse response = hybridSearchService.hybridSearch(indexName, request, false);

    // then
    assertThat(response).isNotNull();
    verify(rrfScorer).mergeWithRRF(anyList(), anyList(), eq(30), eq(200), eq(0.3));
  }

  private void mockMultiSearchResponse(List<Hit<JsonNode>> bm25Hits, List<Hit<JsonNode>> vectorHits)
      throws IOException {
    List<MultiSearchResponseItem<JsonNode>> responses = new ArrayList<>();
    responses.add(createMockResponseItem(bm25Hits));
    responses.add(createMockResponseItem(vectorHits));

    when(msearchResponse.responses()).thenReturn(responses);
    when(elasticsearchClient.msearch(any(MsearchRequest.class), eq(JsonNode.class)))
        .thenReturn(msearchResponse);
  }

  private MultiSearchResponseItem<JsonNode> createMockResponseItem(List<Hit<JsonNode>> hits) {
    @SuppressWarnings("unchecked")
    MultiSearchResponseItem<JsonNode> item = mock(MultiSearchResponseItem.class);
    @SuppressWarnings("unchecked")
    MultiSearchItem<JsonNode> multiSearchItem = mock(MultiSearchItem.class);
    @SuppressWarnings("unchecked")
    HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);

    when(item.isResult()).thenReturn(true);
    when(item.result()).thenReturn(multiSearchItem);
    // MultiSearchItem이 직접 hits() 메서드를 가짐
    when(multiSearchItem.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(hits);

    return item;
  }

  private List<Hit<JsonNode>> createMockHits(String... ids) {
    List<Hit<JsonNode>> hits = new ArrayList<>();
    for (String id : ids) {
      @SuppressWarnings("unchecked")
      Hit<JsonNode> hit = mock(Hit.class);
      when(hit.id()).thenReturn(id);

      JsonNode node = objectMapper.createObjectNode().put("id", id);
      when(hit.source()).thenReturn(node);

      hits.add(hit);
    }
    return hits;
  }

  private List<RRFScorer.RRFResult> createRRFResults(String... ids) {
    List<RRFScorer.RRFResult> results = new ArrayList<>();
    for (String id : ids) {
      @SuppressWarnings("unchecked")
      Hit<JsonNode> hit = mock(Hit.class);
      when(hit.id()).thenReturn(id);

      RRFScorer.RRFResult result = new RRFScorer.RRFResult(id, hit, 0.7, 0.3);
      results.add(result);
    }
    return results;
  }
}
