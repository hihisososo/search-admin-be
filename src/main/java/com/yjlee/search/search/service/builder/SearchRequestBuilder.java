package com.yjlee.search.search.service.builder;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.dto.*;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SearchRequestBuilder {

  public SearchRequest buildProductSearchRequest(
      String indexName,
      SearchExecuteRequest request,
      BoolQuery boolQuery,
      Map<String, Aggregation> aggregations,
      boolean withExplain) {

    ProductSortType sortType =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortType)
            .orElse(ProductSortType.SCORE);

    SortOrder sortOrder =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortOrder)
            .map(ProductSortOrder::getSortOrder)
            .orElse(SortOrder.Desc);

    int from = (request.getPage() - 1) * request.getSize();

    SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(indexName)
            .query(Query.of(q -> q.bool(boolQuery)))
            .aggregations(aggregations)
            .from(from)
            .size(request.getSize())
            .explain(withExplain);

    sortType.applySorting(searchBuilder, sortOrder);
    return searchBuilder.build();
  }

  public SearchRequest buildAutocompleteSearchRequest(String indexName, String keyword) {
    return SearchRequest.of(
        s ->
            s.index(indexName)
                .query(
                    q ->
                        q.match(
                            m -> m.field(ESFields.NAME_ICU).operator(Operator.And).query(keyword)))
                .size(10));
  }

  public Map<String, Aggregation> buildAggregations() {
    return Map.of(
        ESFields.BRAND_NAME,
        Aggregation.of(a -> a.terms(t -> t.field(ESFields.BRAND_NAME).size(50))),
        ESFields.CATEGORY_NAME,
        Aggregation.of(a -> a.terms(t -> t.field(ESFields.CATEGORY_NAME).size(50))));
  }
}
