package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.evaluation.dto.CategoryListResponse;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.service.IndexResolver;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexResolver indexResolver;

  public CategoryListResponse listCategories(int size) {
    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);

      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(0)
                      .aggregations(
                          "categories",
                          Aggregation.of(
                              a ->
                                  a.terms(
                                      t ->
                                          t.field(ESFields.CATEGORY)
                                              .size(size <= 0 ? 100 : size)))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);

      var terms = response.aggregations().get("categories").sterms();
      List<CategoryListResponse.CategoryItem> items = new ArrayList<>();
      if (terms != null) {
        for (var b : terms.buckets().array()) {
          Long count = b.docCount();
          items.add(
              CategoryListResponse.CategoryItem.builder()
                  .name(b.key().stringValue())
                  .docCount(count == null ? 0L : count)
                  .build());
        }
      }

      return CategoryListResponse.builder().categories(items).build();
    } catch (Exception e) {
      log.error("카테고리 리스트 조회 실패", e);
      return CategoryListResponse.builder().categories(List.of()).build();
    }
  }
}
