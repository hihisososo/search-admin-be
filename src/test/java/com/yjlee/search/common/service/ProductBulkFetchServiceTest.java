package com.yjlee.search.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.get.GetResult;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.service.IndexResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductBulkFetchService 테스트")
class ProductBulkFetchServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;

  @Mock private IndexResolver indexResolver;

  @Mock private MgetResponse<ProductDocument> mgetResponse;

  @Mock private GetResponse<ProductDocument> getResponse;

  private ProductBulkFetchService productBulkFetchService;

  @BeforeEach
  void setUp() {
    productBulkFetchService = new ProductBulkFetchService(elasticsearchClient, indexResolver);
  }

  @Test
  @DisplayName("벌크 조회 - 정상 케이스")
  void testFetchBulkSuccess() throws Exception {
    // given
    List<String> productIds = Arrays.asList("P001", "P002", "P003");
    String indexName = "products-dev";
    when(indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(indexName);

    ProductDocument doc1 = createProductDocument("P001", "상품1");
    ProductDocument doc2 = createProductDocument("P002", "상품2");
    ProductDocument doc3 = createProductDocument("P003", "상품3");

    List<MultiGetResponseItem<ProductDocument>> items =
        Arrays.asList(
            createResponseItem("P001", doc1, true),
            createResponseItem("P002", doc2, true),
            createResponseItem("P003", doc3, true));

    when(mgetResponse.docs()).thenReturn(items);
    when(elasticsearchClient.mget(any(MgetRequest.class), eq(ProductDocument.class)))
        .thenReturn(mgetResponse);

    // when
    Map<String, ProductDocument> result = productBulkFetchService.fetchBulk(productIds);

    // then
    assertThat(result).hasSize(3);
    assertThat(result.get("P001").getName()).isEqualTo("상품1");
    assertThat(result.get("P002").getName()).isEqualTo("상품2");
    assertThat(result.get("P003").getName()).isEqualTo("상품3");
  }

  @Test
  @DisplayName("벌크 조회 - 일부 문서만 존재하는 경우")
  void testFetchBulkPartialFound() throws Exception {
    // given
    List<String> productIds = Arrays.asList("P001", "P002", "P003");
    String indexName = "products-dev";
    when(indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(indexName);

    ProductDocument doc1 = createProductDocument("P001", "상품1");
    ProductDocument doc3 = createProductDocument("P003", "상품3");

    List<MultiGetResponseItem<ProductDocument>> items =
        Arrays.asList(
            createResponseItem("P001", doc1, true),
            createResponseItem("P002", null, false),
            createResponseItem("P003", doc3, true));

    when(mgetResponse.docs()).thenReturn(items);
    when(elasticsearchClient.mget(any(MgetRequest.class), eq(ProductDocument.class)))
        .thenReturn(mgetResponse);

    // when
    Map<String, ProductDocument> result = productBulkFetchService.fetchBulk(productIds);

    // then
    assertThat(result).hasSize(2);
    assertThat(result).containsKeys("P001", "P003");
    assertThat(result).doesNotContainKey("P002");
  }

  @Test
  @DisplayName("벌크 조회 - 빈 productIds 리스트")
  void testFetchBulkEmptyList() {
    // given
    List<String> productIds = Arrays.asList();

    // when
    Map<String, ProductDocument> result = productBulkFetchService.fetchBulk(productIds);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("벌크 조회 - null productIds")
  void testFetchBulkNullList() {
    // when
    Map<String, ProductDocument> result = productBulkFetchService.fetchBulk(null);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("벌크 조회 실패 시 개별 조회로 폴백")
  void testFetchBulkFallbackToIndividual() throws Exception {
    // given
    List<String> productIds = Arrays.asList("P001", "P002");
    String indexName = "products-dev";
    when(indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(indexName);

    // 벌크 조회 실패
    when(elasticsearchClient.mget(any(MgetRequest.class), eq(ProductDocument.class)))
        .thenThrow(new RuntimeException("ES 연결 실패"));

    // 개별 조회 설정
    ProductDocument doc1 = createProductDocument("P001", "상품1");
    ProductDocument doc2 = createProductDocument("P002", "상품2");

    GetResponse<ProductDocument> response1 = mock(GetResponse.class);
    when(response1.found()).thenReturn(true);
    when(response1.source()).thenReturn(doc1);

    GetResponse<ProductDocument> response2 = mock(GetResponse.class);
    when(response2.found()).thenReturn(true);
    when(response2.source()).thenReturn(doc2);

    when(elasticsearchClient.get(any(GetRequest.class), eq(ProductDocument.class)))
        .thenReturn(response1, response2);

    // when
    Map<String, ProductDocument> result = productBulkFetchService.fetchBulk(productIds);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get("P001").getName()).isEqualTo("상품1");
    assertThat(result.get("P002").getName()).isEqualTo("상품2");
    verify(elasticsearchClient, times(2)).get(any(GetRequest.class), eq(ProductDocument.class));
  }

  @Test
  @DisplayName("단일 조회 - 정상 케이스")
  void testFetchSingleSuccess() throws Exception {
    // given
    String productId = "P001";
    String indexName = "products-dev";
    when(indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(indexName);

    ProductDocument doc = createProductDocument("P001", "상품1");
    when(getResponse.found()).thenReturn(true);
    when(getResponse.source()).thenReturn(doc);
    when(elasticsearchClient.get(any(GetRequest.class), eq(ProductDocument.class)))
        .thenReturn(getResponse);

    // when
    Optional<ProductDocument> result = productBulkFetchService.fetchSingle(productId);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("상품1");
  }

  @Test
  @DisplayName("단일 조회 - 문서가 없는 경우")
  void testFetchSingleNotFound() throws Exception {
    // given
    String productId = "P999";
    String indexName = "products-dev";
    when(indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(indexName);

    when(getResponse.found()).thenReturn(false);
    when(elasticsearchClient.get(any(GetRequest.class), eq(ProductDocument.class)))
        .thenReturn(getResponse);

    // when
    Optional<ProductDocument> result = productBulkFetchService.fetchSingle(productId);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("단일 조회 - 예외 발생 시 빈 Optional 반환")
  void testFetchSingleException() throws Exception {
    // given
    String productId = "P001";
    String indexName = "products-dev";
    when(indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(indexName);

    when(elasticsearchClient.get(any(GetRequest.class), eq(ProductDocument.class)))
        .thenThrow(new RuntimeException("ES 오류"));

    // when
    Optional<ProductDocument> result = productBulkFetchService.fetchSingle(productId);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("환경별 벌크 조회 - PROD")
  void testFetchBulkWithProdEnvironment() throws Exception {
    // given
    List<String> productIds = Arrays.asList("P001");
    String indexName = "products-prod";
    IndexEnvironment.EnvironmentType envType = IndexEnvironment.EnvironmentType.PROD;

    when(indexResolver.resolveProductIndex(envType)).thenReturn(indexName);

    ProductDocument doc = createProductDocument("P001", "상품1");
    List<MultiGetResponseItem<ProductDocument>> items =
        Arrays.asList(createResponseItem("P001", doc, true));

    when(mgetResponse.docs()).thenReturn(items);
    when(elasticsearchClient.mget(any(MgetRequest.class), eq(ProductDocument.class)))
        .thenReturn(mgetResponse);

    // when
    Map<String, ProductDocument> result = productBulkFetchService.fetchBulk(productIds, envType);

    // then
    assertThat(result).hasSize(1);
    verify(indexResolver).resolveProductIndex(envType);
  }

  @Test
  @DisplayName("환경별 단일 조회 - DEV")
  void testFetchSingleWithDevEnvironment() throws Exception {
    // given
    String productId = "P001";
    String indexName = "products-dev";
    IndexEnvironment.EnvironmentType envType = IndexEnvironment.EnvironmentType.DEV;

    when(indexResolver.resolveProductIndex(envType)).thenReturn(indexName);

    ProductDocument doc = createProductDocument("P001", "상품1");
    when(getResponse.found()).thenReturn(true);
    when(getResponse.source()).thenReturn(doc);
    when(elasticsearchClient.get(any(GetRequest.class), eq(ProductDocument.class)))
        .thenReturn(getResponse);

    // when
    Optional<ProductDocument> result = productBulkFetchService.fetchSingle(productId, envType);

    // then
    assertThat(result).isPresent();
    verify(indexResolver).resolveProductIndex(envType);
  }

  @Test
  @DisplayName("벌크 조회 - null source 처리")
  void testFetchBulkWithNullSource() throws Exception {
    // given
    List<String> productIds = Arrays.asList("P001", "P002");
    String indexName = "products-dev";
    when(indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(indexName);

    ProductDocument doc1 = createProductDocument("P001", "상품1");

    List<MultiGetResponseItem<ProductDocument>> items =
        Arrays.asList(
            createResponseItem("P001", doc1, true),
            createResponseItem("P002", null, true) // found but source is null
            );

    when(mgetResponse.docs()).thenReturn(items);
    when(elasticsearchClient.mget(any(MgetRequest.class), eq(ProductDocument.class)))
        .thenReturn(mgetResponse);

    // when
    Map<String, ProductDocument> result = productBulkFetchService.fetchBulk(productIds);

    // then
    assertThat(result).hasSize(1);
    assertThat(result).containsKey("P001");
    assertThat(result).doesNotContainKey("P002");
  }

  private ProductDocument createProductDocument(String id, String name) {
    return ProductDocument.builder().id(id).name(name).build();
  }

  private MultiGetResponseItem<ProductDocument> createResponseItem(
      String id, ProductDocument doc, boolean found) {
    @SuppressWarnings("unchecked")
    MultiGetResponseItem<ProductDocument> item = mock(MultiGetResponseItem.class);
    @SuppressWarnings("unchecked")
    GetResult<ProductDocument> result = mock(GetResult.class);

    when(item.result()).thenReturn(result);
    when(result.found()).thenReturn(found);
    if (found) {
      when(result.id()).thenReturn(id);
      when(result.source()).thenReturn(doc);
    }

    return item;
  }
}
