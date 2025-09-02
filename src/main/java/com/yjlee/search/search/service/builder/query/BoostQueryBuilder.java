package com.yjlee.search.search.service.builder.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.index.util.UnitExtractor;
import com.yjlee.search.search.constants.SearchBoostConstants;
import com.yjlee.search.search.service.category.CategoryRankingService;
import java.util.*;
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

    // multiMatch 쿼리로 name과 specs 필드에 phrase 검색
    Query multiMatchQuery =
        Query.of(
            q ->
                q.multiMatch(
                    m ->
                        m.query(query)
                            .fields(List.of(ESFields.NAME, ESFields.SPECS))
                            .type(TextQueryType.Phrase)
                            .minimumShouldMatch("2")
                            .boost(SearchBoostConstants.NAME_PHRASE_BOOST)));

    return List.of(multiMatchQuery);
  }

  public List<Query> buildModelBoostQueries(List<String> models) {
    if (models == null || models.isEmpty()) {
      return List.of();
    }

    return models.stream()
        .map(
            model -> buildMatchQuery(ESFields.MODEL, model, SearchBoostConstants.MODEL_MATCH_BOOST))
        .toList();
  }

  public List<Query> buildUnitBoostQueries(
      List<String> units, Map<String, Set<String>> expandedUnitsMap) {
    if (units == null || units.isEmpty()) {
      return List.of();
    }

    List<Query> unitBoostQueries = new ArrayList<>();

    for (String unit : units) {
      if (!unit.isEmpty()) {
        // context에서 이미 확장된 단위를 사용
        Set<String> expandedUnits =
            expandedUnitsMap != null
                ? expandedUnitsMap.get(unit)
                : UnitExtractor.expandUnitSynonyms(unit);

        if (expandedUnits != null && !expandedUnits.isEmpty()) {
          List<Query> synonymQueries = buildUnitSynonymQueries(expandedUnits);

          if (synonymQueries.size() == 1) {
            unitBoostQueries.add(synonymQueries.get(0));
          } else if (!synonymQueries.isEmpty()) {
            unitBoostQueries.add(
                buildBoolShouldQuery(synonymQueries, SearchBoostConstants.UNIT_MATCH_BOOST));
          }
        }
      }
    }

    return unitBoostQueries;
  }

  // 기존 메서드를 유지하면서 새 메서드를 호출하도록 변경 (하위 호환성)
  public List<Query> buildUnitBoostQueries(List<String> units) {
    return buildUnitBoostQueries(units, null);
  }

  public List<Query> buildCategoryBoostQueries(String query) {
    if (query == null || query.trim().isEmpty()) {
      return List.of();
    }

    Map<String, Integer> categoryWeights = categoryRankingService.getCategoryWeights(query);

    if (categoryWeights.isEmpty()) {
      return List.of();
    }

    return categoryWeights.entrySet().stream()
        .map(entry -> buildCategoryBoostQuery(entry.getKey(), entry.getValue()))
        .toList();
  }

  private Query buildPhraseQuery(String field, String query, float boost) {
    return Query.of(q -> q.matchPhrase(mp -> mp.field(field).query(query).boost(boost)));
  }

  private Query buildMatchQuery(String field, String query, float boost) {
    return Query.of(q -> q.match(m -> m.field(field).query(query).boost(boost)));
  }

  private List<Query> buildUnitSynonymQueries(Set<String> expandedUnits) {
    List<Query> synonymQueries = new ArrayList<>();
    for (String expandedUnit : expandedUnits) {
      synonymQueries.add(
          buildMatchQuery(ESFields.UNIT, expandedUnit, SearchBoostConstants.UNIT_MATCH_BOOST));
    }
    return synonymQueries;
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
