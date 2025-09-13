package com.yjlee.search.search.service.builder.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.search.constants.SearchBoostConstants;
import com.yjlee.search.search.service.category.CategoryRankingService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BoostQueryBuilder {

  private final CategoryRankingService categoryRankingService;

  public List<Query> buildPhraseBoostQueries(String query) {
    if (query == null || query.trim().isEmpty()) {
      return List.of();
    }

    // multiMatch 쿼리로 name, specs, category 필드에 phrase 검색
    Query multiMatchQuery =
        Query.of(
            q ->
                q.multiMatch(
                    m ->
                        m.query(query)
                            .fields(List.of(ESFields.NAME, ESFields.SPECS, ESFields.CATEGORY))
                            .type(TextQueryType.Phrase)
                            .slop(10)
                            .minimumShouldMatch("2")
                            .boost(SearchBoostConstants.NAME_PHRASE_BOOST)));

    return List.of(multiMatchQuery);
  }

  // 일반 검색용 - PROD 환경 고정
  public List<Query> buildCategoryBoostQueries(String query) {
    return buildCategoryBoostQueries(query, EnvironmentType.PROD);
  }

  // 시뮬레이션 검색용 - 환경 지정 가능
  public List<Query> buildCategoryBoostQueries(String query, EnvironmentType environment) {
    if (query == null || query.trim().isEmpty()) {
      return List.of();
    }

    Map<String, Integer> categoryWeights =
        categoryRankingService.getCategoryWeights(query, environment);

    if (categoryWeights.isEmpty()) {
      return List.of();
    }

    return categoryWeights.entrySet().stream()
        .map(entry -> buildCategoryBoostQuery(entry.getKey(), entry.getValue()))
        .toList();
  }

  private Query buildPhraseQuery(String field, String query, float boost) {
    return Query.of(q -> q.matchPhrase(mp -> mp.field(field).query(query).slop(10).boost(boost)));
  }

  private Query buildMatchQuery(String field, String query, float boost) {
    return Query.of(q -> q.match(m -> m.field(field).query(query).boost(boost)));
  }

  private Query buildBoolShouldQuery(List<Query> queries, float boost) {
    return Query.of(q -> q.bool(b -> b.should(queries).minimumShouldMatch("1").boost(boost)));
  }

  private Query buildCategoryBoostQuery(String category, Integer weight) {
    return Query.of(
        q ->
            q.constantScore(
                cs ->
                    cs.filter(f -> f.term(t -> t.field(ESFields.CATEGORY_NAME).value(category)))
                        .boost(weight.floatValue())));
  }
}
