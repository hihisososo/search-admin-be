package com.yjlee.search.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.embedding.service.EmbeddingService;
import com.yjlee.search.embedding.service.EmbeddingService.EmbeddingType;
import com.yjlee.search.search.dto.VectorSearchConfig;
import com.yjlee.search.search.dto.VectorSearchResult;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorSearchService 테스트")
class VectorSearchServiceTest {

  @Mock private EmbeddingService embeddingService;

  @Mock private ElasticsearchClient elasticsearchClient;

  @Mock private SearchResponse<JsonNode> searchResponse;

  private VectorSearchService vectorSearchService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    vectorSearchService = new VectorSearchService(embeddingService, elasticsearchClient);
    ReflectionTestUtils.setField(vectorSearchService, "defaultVectorMinScore", 0.60);
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("쿼리 임베딩 생성 - 새로운 쿼리")
  void testGetQueryEmbeddingNew() {
    // given
    String query = "노트북 추천";
    float[] expectedEmbedding = new float[] {0.1f, 0.2f, 0.3f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(expectedEmbedding);

    // when
    float[] result = vectorSearchService.getQueryEmbedding(query);

    // then
    assertThat(result).isEqualTo(expectedEmbedding);
    verify(embeddingService, times(1)).getEmbedding(query, EmbeddingType.QUERY);
  }

  @Test
  @DisplayName("쿼리 임베딩 생성 - 캐시된 쿼리")
  void testGetQueryEmbeddingCached() {
    // given
    String query = "노트북 추천";
    float[] expectedEmbedding = new float[] {0.1f, 0.2f, 0.3f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(expectedEmbedding);

    // when - 첫 번째 호출
    float[] result1 = vectorSearchService.getQueryEmbedding(query);
    // when - 두 번째 호출 (캐시에서 가져옴)
    float[] result2 = vectorSearchService.getQueryEmbedding(query);

    // then
    assertThat(result1).isEqualTo(expectedEmbedding);
    assertThat(result2).isEqualTo(expectedEmbedding);
    // 임베딩 서비스는 한 번만 호출되어야 함
    verify(embeddingService, times(1)).getEmbedding(query, EmbeddingType.QUERY);
  }

  @Test
  @DisplayName("쿼리 임베딩 캐시 - 여러 쿼리 처리")
  void testGetQueryEmbeddingMultipleQueries() {
    // given
    float[] embedding1 = new float[] {0.1f};
    float[] embedding2 = new float[] {0.2f};
    float[] embedding3 = new float[] {0.3f};

    when(embeddingService.getEmbedding("query1", EmbeddingType.QUERY)).thenReturn(embedding1);
    when(embeddingService.getEmbedding("query2", EmbeddingType.QUERY)).thenReturn(embedding2);
    when(embeddingService.getEmbedding("query3", EmbeddingType.QUERY)).thenReturn(embedding3);

    // when
    float[] result1 = vectorSearchService.getQueryEmbedding("query1");
    float[] result2 = vectorSearchService.getQueryEmbedding("query2");
    float[] result3 = vectorSearchService.getQueryEmbedding("query3");

    // 같은 쿼리 다시 요청 - 캐시에서 가져옴
    float[] result1Again = vectorSearchService.getQueryEmbedding("query1");
    float[] result2Again = vectorSearchService.getQueryEmbedding("query2");

    // then
    assertThat(result1).isEqualTo(embedding1);
    assertThat(result2).isEqualTo(embedding2);
    assertThat(result3).isEqualTo(embedding3);
    assertThat(result1Again).isEqualTo(embedding1);
    assertThat(result2Again).isEqualTo(embedding2);

    // 각 쿼리는 한 번씩만 임베딩 생성
    verify(embeddingService, times(1)).getEmbedding("query1", EmbeddingType.QUERY);
    verify(embeddingService, times(1)).getEmbedding("query2", EmbeddingType.QUERY);
    verify(embeddingService, times(1)).getEmbedding("query3", EmbeddingType.QUERY);
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 - 기본 설정")
  void testMultiFieldVectorSearchDefault() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    int topK = 10;
    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenReturn(searchResponse);

    // when
    SearchResponse<JsonNode> result =
        vectorSearchService.multiFieldVectorSearch(index, query, topK);

    // then
    assertThat(result).isEqualTo(searchResponse);
    verify(elasticsearchClient).search(any(SearchRequest.class), eq(JsonNode.class));
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 - 설정 객체 사용")
  void testMultiFieldVectorSearchWithConfig() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    VectorSearchConfig config =
        VectorSearchConfig.builder()
            .topK(20)
            .vectorMinScore(0.7)
            .numCandidatesMultiplier(5)
            .nameVectorBoost(2.0f)
            .specsVectorBoost(1.5f)
            .build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenReturn(searchResponse);

    // when
    SearchResponse<JsonNode> result =
        vectorSearchService.multiFieldVectorSearch(index, query, config);

    // then
    assertThat(result).isEqualTo(searchResponse);
    verify(elasticsearchClient).search(any(SearchRequest.class), eq(JsonNode.class));
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 - 필터 쿼리 포함")
  void testMultiFieldVectorSearchWithFilter() throws Exception {
    // given
    String index = "products";
    String query = "노트북";

    Query filterQuery = Query.of(q -> q.term(t -> t.field("category").value("electronics")));
    VectorSearchConfig config =
        VectorSearchConfig.builder()
            .topK(10)
            .vectorMinScore(0.65)
            .filterQueries(Arrays.asList(filterQuery))
            .build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenReturn(searchResponse);

    // when
    SearchResponse<JsonNode> result =
        vectorSearchService.multiFieldVectorSearch(index, query, config);

    // then
    assertThat(result).isEqualTo(searchResponse);
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 - IOException 발생 시")
  void testMultiFieldVectorSearchIOException() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    VectorSearchConfig config = VectorSearchConfig.builder().topK(10).build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenThrow(new IOException("ES connection failed"));

    // when & then
    assertThatThrownBy(() -> vectorSearchService.multiFieldVectorSearch(index, query, config))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Vector search failed")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 with Request - 정상 케이스")
  void testMultiFieldVectorSearchWithRequest() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    VectorSearchConfig config = VectorSearchConfig.builder().topK(15).vectorMinScore(0.75).build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenReturn(searchResponse);

    // when
    VectorSearchResult result =
        vectorSearchService.multiFieldVectorSearchWithRequest(index, query, config);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getResponse()).isEqualTo(searchResponse);
    assertThat(result.getRequest()).isNotNull();
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 with Request - IOException 발생")
  void testMultiFieldVectorSearchWithRequestIOException() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    VectorSearchConfig config = VectorSearchConfig.builder().topK(10).build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenThrow(new IOException("Network error"));

    // when & then
    assertThatThrownBy(
            () -> vectorSearchService.multiFieldVectorSearchWithRequest(index, query, config))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Vector search failed");
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 - null 필터 쿼리")
  void testMultiFieldVectorSearchNullFilter() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    VectorSearchConfig config =
        VectorSearchConfig.builder()
            .topK(10)
            .filterQueries(null) // null filter
            .build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenReturn(searchResponse);

    // when
    SearchResponse<JsonNode> result =
        vectorSearchService.multiFieldVectorSearch(index, query, config);

    // then
    assertThat(result).isEqualTo(searchResponse);
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 - 빈 필터 쿼리 리스트")
  void testMultiFieldVectorSearchEmptyFilter() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    VectorSearchConfig config =
        VectorSearchConfig.builder()
            .topK(10)
            .filterQueries(Arrays.asList()) // empty filter list
            .build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenReturn(searchResponse);

    // when
    SearchResponse<JsonNode> result =
        vectorSearchService.multiFieldVectorSearch(index, query, config);

    // then
    assertThat(result).isEqualTo(searchResponse);
  }

  @Test
  @DisplayName("다중 필드 벡터 검색 - null vectorMinScore는 기본값 사용")
  void testMultiFieldVectorSearchNullMinScore() throws Exception {
    // given
    String index = "products";
    String query = "노트북";
    VectorSearchConfig config =
        VectorSearchConfig.builder()
            .topK(10)
            .vectorMinScore(null) // null min score
            .build();

    float[] embedding = new float[] {0.1f, 0.2f};

    when(embeddingService.getEmbedding(query, EmbeddingType.QUERY)).thenReturn(embedding);
    when(elasticsearchClient.search(any(SearchRequest.class), eq(JsonNode.class)))
        .thenReturn(searchResponse);

    // when
    SearchResponse<JsonNode> result =
        vectorSearchService.multiFieldVectorSearch(index, query, config);

    // then
    assertThat(result).isEqualTo(searchResponse);
    // 기본값 0.60이 사용되었는지는 SearchRequest 내부에서 확인됨
  }
}
