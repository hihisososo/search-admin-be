package com.yjlee.search.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.evaluation.dto.CategoryListResponse;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.service.IndexResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService 테스트")
class CategoryServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;

  @Mock private IndexResolver indexResolver;

  @Mock private SearchResponse<ProductDocument> searchResponse;

  private CategoryService categoryService;

  @BeforeEach
  void setUp() {
    categoryService = new CategoryService(elasticsearchClient, indexResolver);
  }

  @Test
  @DisplayName("카테고리 목록 조회 - 정상 케이스")
  void testListCategories() throws Exception {
    // given
    String indexName = "products-search";
    when(indexResolver.resolveProductIndex()).thenReturn(indexName);

    // Mock aggregation response
    mockSearchResponse();
    when(elasticsearchClient.search(any(SearchRequest.class), eq(ProductDocument.class)))
        .thenReturn(searchResponse);

    // when
    CategoryListResponse response = categoryService.listCategories(100);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).hasSize(3);

    // 가나다순 정렬 확인
    assertThat(response.getCategories().get(0).getName()).isEqualTo("가전");
    assertThat(response.getCategories().get(1).getName()).isEqualTo("의류");
    assertThat(response.getCategories().get(2).getName()).isEqualTo("전자제품");

    // 문서 수 확인
    assertThat(response.getCategories().get(0).getDocCount()).isEqualTo(50L);
    assertThat(response.getCategories().get(1).getDocCount()).isEqualTo(30L);
    assertThat(response.getCategories().get(2).getDocCount()).isEqualTo(100L);
  }

  @Test
  @DisplayName("카테고리 목록 조회 - 예외 발생 시 빈 리스트 반환")
  void testListCategoriesWithException() throws Exception {
    // given
    when(indexResolver.resolveProductIndex()).thenThrow(new RuntimeException("Index not found"));

    // when
    CategoryListResponse response = categoryService.listCategories(100);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).isEmpty();
  }

  @Test
  @DisplayName("환경별 카테고리 목록 조회 - DEV")
  void testListCategoriesForEnvironmentDev() throws Exception {
    // given
    String indexName = "products-dev";
    EnvironmentType envType = EnvironmentType.DEV;
    when(indexResolver.resolveProductIndex(envType)).thenReturn(indexName);

    mockSearchResponse();
    when(elasticsearchClient.search(any(SearchRequest.class), eq(ProductDocument.class)))
        .thenReturn(searchResponse);

    // when
    CategoryListResponse response = categoryService.listCategoriesForEnvironment(envType, 100);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).hasSize(3);
    verify(indexResolver).resolveProductIndex(envType);
  }

  @Test
  @DisplayName("환경별 카테고리 목록 조회 - PROD")
  void testListCategoriesForEnvironmentProd() throws Exception {
    // given
    String indexName = "products-prod";
    EnvironmentType envType = EnvironmentType.PROD;
    when(indexResolver.resolveProductIndex(envType)).thenReturn(indexName);

    mockSearchResponse();
    when(elasticsearchClient.search(any(SearchRequest.class), eq(ProductDocument.class)))
        .thenReturn(searchResponse);

    // when
    CategoryListResponse response = categoryService.listCategoriesForEnvironment(envType, 100);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).hasSize(3);
    verify(indexResolver).resolveProductIndex(envType);
  }

  @Test
  @DisplayName("환경별 카테고리 목록 조회 - 예외 발생 시 빈 리스트 반환")
  void testListCategoriesForEnvironmentWithException() {
    // given
    EnvironmentType envType = EnvironmentType.DEV;
    when(indexResolver.resolveProductIndex(envType))
        .thenThrow(new RuntimeException("Environment not ready"));

    // when
    CategoryListResponse response = categoryService.listCategoriesForEnvironment(envType, 100);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).isEmpty();
  }

  @Test
  @DisplayName("카테고리 목록 조회 - size 0일 때 기본값 10000 사용")
  void testListCategoriesWithZeroSize() throws Exception {
    // given
    String indexName = "products-search";
    when(indexResolver.resolveProductIndex()).thenReturn(indexName);

    mockSearchResponse();
    when(elasticsearchClient.search(any(SearchRequest.class), eq(ProductDocument.class)))
        .thenReturn(searchResponse);

    // when
    CategoryListResponse response = categoryService.listCategories(0);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).hasSize(3);
  }

  @Test
  @DisplayName("카테고리 목록 조회 - 음수 size일 때 기본값 10000 사용")
  void testListCategoriesWithNegativeSize() throws Exception {
    // given
    String indexName = "products-search";
    when(indexResolver.resolveProductIndex()).thenReturn(indexName);

    mockSearchResponse();
    when(elasticsearchClient.search(any(SearchRequest.class), eq(ProductDocument.class)))
        .thenReturn(searchResponse);

    // when
    CategoryListResponse response = categoryService.listCategories(-1);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).hasSize(3);
  }

  @Test
  @DisplayName("카테고리 목록 조회 - aggregation이 null인 경우")
  void testListCategoriesWithNullAggregation() throws Exception {
    // given
    String indexName = "products-search";
    when(indexResolver.resolveProductIndex()).thenReturn(indexName);

    // Mock response with empty aggregation
    when(searchResponse.aggregations()).thenReturn(Map.of());

    when(elasticsearchClient.search(any(SearchRequest.class), eq(ProductDocument.class)))
        .thenReturn(searchResponse);

    // when
    CategoryListResponse response = categoryService.listCategories(100);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).isEmpty();
  }

  @Test
  @DisplayName("카테고리 목록 조회 - 특수문자 포함 카테고리명 처리")
  void testListCategoriesWithSpecialCharacters() throws Exception {
    // given
    String indexName = "products-search";
    when(indexResolver.resolveProductIndex()).thenReturn(indexName);

    // Mock response with special characters
    mockSearchResponseWithSpecialCharacters();
    when(elasticsearchClient.search(any(SearchRequest.class), eq(ProductDocument.class)))
        .thenReturn(searchResponse);

    // when
    CategoryListResponse response = categoryService.listCategories(100);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getCategories()).hasSize(2);
    assertThat(response.getCategories().get(0).getName()).isEqualTo("TV/모니터");
    assertThat(response.getCategories().get(1).getName()).isEqualTo("노트북&PC");
  }

  private void mockSearchResponse() {
    // Create mock buckets
    StringTermsBucket bucket1 = mock(StringTermsBucket.class);
    when(bucket1.key()).thenReturn(FieldValue.of("전자제품"));
    when(bucket1.docCount()).thenReturn(100L);

    StringTermsBucket bucket2 = mock(StringTermsBucket.class);
    when(bucket2.key()).thenReturn(FieldValue.of("의류"));
    when(bucket2.docCount()).thenReturn(30L);

    StringTermsBucket bucket3 = mock(StringTermsBucket.class);
    when(bucket3.key()).thenReturn(FieldValue.of("가전"));
    when(bucket3.docCount()).thenReturn(50L);

    // Create mock aggregate
    StringTermsAggregate termsAggregate = mock(StringTermsAggregate.class);
    when(termsAggregate.buckets())
        .thenReturn(mock(co.elastic.clients.elasticsearch._types.aggregations.Buckets.class));
    when(termsAggregate.buckets().array()).thenReturn(List.of(bucket1, bucket2, bucket3));

    Aggregate aggregate = mock(Aggregate.class);
    when(aggregate.sterms()).thenReturn(termsAggregate);

    // Setup response
    when(searchResponse.aggregations()).thenReturn(Map.of("categories", aggregate));
  }

  private void mockSearchResponseWithSpecialCharacters() {
    // Create mock buckets with special characters
    StringTermsBucket bucket1 = mock(StringTermsBucket.class);
    when(bucket1.key()).thenReturn(FieldValue.of("TV/모니터"));
    when(bucket1.docCount()).thenReturn(75L);

    StringTermsBucket bucket2 = mock(StringTermsBucket.class);
    when(bucket2.key()).thenReturn(FieldValue.of("노트북&PC"));
    when(bucket2.docCount()).thenReturn(125L);

    // Create mock aggregate
    StringTermsAggregate termsAggregate = mock(StringTermsAggregate.class);
    when(termsAggregate.buckets())
        .thenReturn(mock(co.elastic.clients.elasticsearch._types.aggregations.Buckets.class));
    when(termsAggregate.buckets().array()).thenReturn(List.of(bucket1, bucket2));

    Aggregate aggregate = mock(Aggregate.class);
    when(aggregate.sterms()).thenReturn(termsAggregate);

    // Setup response
    when(searchResponse.aggregations()).thenReturn(Map.of("categories", aggregate));
  }
}
