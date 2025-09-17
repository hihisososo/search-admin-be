package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import com.yjlee.search.evaluation.dto.CategoryListResponse;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.provider.IndexNameProvider;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentService environmentService;
  private final IndexNameProvider indexNameProvider;

  public CategoryListResponse listCategories(int size) {
    // 기본값으로 현재 운영 중인 alias 사용
    try {
      String indexName = indexNameProvider.getProductsSearchAlias();
      return listCategoriesFromIndex(indexName, size);
    } catch (Exception e) {
      log.error("카테고리 리스트 조회 실패", e);
      return CategoryListResponse.builder().categories(List.of()).build();
    }
  }

  public CategoryListResponse listCategoriesForEnvironment(
      EnvironmentType environmentType, int size) {
    // 특정 환경의 카테고리 조회
    try {
      IndexEnvironment indexEnvironment = environmentService.getEnvironment(environmentType);
      String indexName = indexEnvironment.getIndexName();
      return listCategoriesFromIndex(indexName, size);
    } catch (Exception e) {
      log.error("카테고리 리스트 조회 실패 - 환경: {}", environmentType, e);
      return CategoryListResponse.builder().categories(List.of()).build();
    }
  }

  private CategoryListResponse listCategoriesFromIndex(String indexName, int size)
      throws Exception {
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
                                        t.field(ESFields.CATEGORY_NAME)
                                            .size(size <= 0 ? 10000 : size)))));

    SearchResponse<ProductDocument> response =
        elasticsearchClient.search(request, ProductDocument.class);

    StringTermsAggregate terms = response.aggregations().get("categories").sterms();
    List<CategoryListResponse.CategoryItem> items = new ArrayList<>();
    if (terms != null) {
      for (StringTermsBucket b : terms.buckets().array()) {
        Long count = b.docCount();
        items.add(
            CategoryListResponse.CategoryItem.builder()
                .name(b.key().stringValue())
                .docCount(count == null ? 0L : count)
                .build());
      }
    }

    // 가나다순 정렬 (한국어 Collator 사용)
    Collator koreanCollator = Collator.getInstance(Locale.KOREAN);
    items.sort(Comparator.comparing(CategoryListResponse.CategoryItem::getName, koreanCollator));

    return CategoryListResponse.builder().categories(items).build();
  }
}
