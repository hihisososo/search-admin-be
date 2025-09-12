package com.yjlee.search.common.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.constants.VectorSearchConstants;
import com.yjlee.search.search.service.IndexResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductBulkFetchService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexResolver indexResolver;

  public Map<String, ProductDocument> fetchBulk(List<String> productIds) {
    return fetchBulk(productIds, IndexEnvironment.EnvironmentType.DEV);
  }

  public Map<String, ProductDocument> fetchBulk(
      List<String> productIds, IndexEnvironment.EnvironmentType environmentType) {

    if (ObjectUtils.isEmpty(productIds)) {
      return new HashMap<>();
    }

    try {

      String indexName = indexResolver.resolveProductIndex(environmentType);
      MgetRequest.Builder requestBuilder =
          new MgetRequest.Builder()
              .index(indexName)
              .sourceExcludes(VectorSearchConstants.getVectorFieldsToExclude());

      for (String productId : productIds) {
        requestBuilder.ids(productId);
      }

      MgetRequest request = requestBuilder.build();
      MgetResponse<ProductDocument> response =
          elasticsearchClient.mget(request, ProductDocument.class);

      Map<String, ProductDocument> productMap = new HashMap<>();
      for (MultiGetResponseItem<ProductDocument> item : response.docs()) {
        if (item.result() != null && item.result().found()) {
          String productId = item.result().id();
          ProductDocument product = item.result().source();
          if (product != null) {
            productMap.put(productId, product);
          }
        }
      }

      return productMap;

    } catch (Exception e) {
      log.error("ES 벌크 조회 실패", e);
      return fetchIndividually(productIds, environmentType);
    }
  }

  private Map<String, ProductDocument> fetchIndividually(
      List<String> productIds, IndexEnvironment.EnvironmentType environmentType) {

    Map<String, ProductDocument> productMap = new HashMap<>();

    for (String productId : productIds) {
      Optional<ProductDocument> product = fetchSingle(productId, environmentType);
      product.ifPresent(doc -> productMap.put(productId, doc));
    }

    return productMap;
  }

  public Optional<ProductDocument> fetchSingle(String productId) {
    return fetchSingle(productId, IndexEnvironment.EnvironmentType.DEV);
  }

  public Optional<ProductDocument> fetchSingle(
      String productId, IndexEnvironment.EnvironmentType environmentType) {

    try {
      String indexName = indexResolver.resolveProductIndex(environmentType);

      GetRequest request =
          GetRequest.of(
              g ->
                  g.index(indexName)
                      .id(productId)
                      .sourceExcludes(VectorSearchConstants.getVectorFieldsToExclude()));

      GetResponse<ProductDocument> response =
          elasticsearchClient.get(request, ProductDocument.class);
      return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();

    } catch (Exception e) {
      log.warn("ES에서 상품 {} 조회 실패", productId, e);
      return Optional.empty();
    }
  }
}
