package com.yjlee.search.search.service.builder;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.util.KoreanTextUtils;
import com.yjlee.search.search.constants.VectorSearchConstants;
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

        ProductSortType sortType = Optional.ofNullable(request.getSort())
                .map(ProductSortDto::getSortType)
                .orElse(ProductSortType.SCORE);

        SortOrder sortOrder = Optional.ofNullable(request.getSort())
                .map(ProductSortDto::getSortOrder)
                .map(ProductSortOrder::getSortOrder)
                .orElse(SortOrder.Desc);

        int from = request.getPage() * request.getSize();

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(indexName)
                .query(Query.of(q -> q.bool(boolQuery)))
                .source(
                        s -> s.filter(f -> f.excludes(VectorSearchConstants.getVectorFieldsToExclude())))
                .aggregations(aggregations)
                .from(from)
                .size(request.getSize())
                .trackTotalHits(t -> t.enabled(true))
                .explain(withExplain);

        sortType.applySorting(searchBuilder, sortOrder);
        return searchBuilder.build();
    }

    public SearchRequest buildAutocompleteSearchRequest(String indexName, String keyword) {
        String keywordLower = keyword.toLowerCase();
        String keywordNoSpace = keyword.replaceAll("\\s+", "");
        String keywordNoSpaceLower = keywordNoSpace.toLowerCase();

        // 색인할 때와 똑같이 변환
        String keywordJamo = KoreanTextUtils.decomposeHangul(keywordLower);
        String keywordJamoNoSpace = KoreanTextUtils.decomposeHangul(keywordNoSpaceLower);
        // 초성 검색은 검색어 그대로 사용 (초성 추출하지 않음)
        String keywordChosung = keywordLower;

        return SearchRequest.of(
                s -> s.index(indexName)
                        .query(
                                q -> q.bool(
                                        b -> b.should(
                                                should -> should.match(
                                                        m -> m.field("name_jamo")
                                                                .query(keywordJamo)
                                                                .operator(Operator.And)
                                                                .boost(3.0f)))
                                                .should(
                                                        should -> should.match(
                                                                m -> m.field("name_jamo_no_space")
                                                                        .query(keywordJamoNoSpace)
                                                                        .operator(Operator.And)
                                                                        .boost(3.0f)))
                                                .should(
                                                        should -> should.match(
                                                                m -> m.field("name_chosung")
                                                                        .query(keywordChosung)
                                                                        .operator(Operator.And)
                                                                        .boost(2.0f)))
                                                .should(
                                                        should -> should.match(
                                                                m -> m.field("name_nori")
                                                                        .query(keywordLower)
                                                                        .operator(Operator.And)
                                                                        .boost(1.0f)))
                                                .minimumShouldMatch("1")))
                        .size(10)
                        .sort(sort -> sort.score(sc -> sc.order(SortOrder.Desc)))
                        .sort(sort -> sort.field(f -> f.field("name").order(SortOrder.Asc))));
    }

    public Map<String, Aggregation> buildAggregations() {
        return Map.of(
                ESFields.BRAND_NAME,
                Aggregation.of(a -> a.terms(t -> t.field(ESFields.BRAND_NAME).size(50))),
                ESFields.CATEGORY_NAME,
                Aggregation.of(a -> a.terms(t -> t.field(ESFields.CATEGORY_NAME).size(50))));
    }
}
