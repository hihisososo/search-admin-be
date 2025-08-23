package com.yjlee.search.search.service.builder;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.util.ModelExtractor;
import com.yjlee.search.index.util.UnitExtractor;
import com.yjlee.search.search.dto.PriceRangeDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.service.category.CategoryRankingService;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryBuilder {

  private final TypoCorrectionService typoCorrectionService;
  private final CategoryRankingService categoryRankingService;

  public BoolQuery buildBoolQuery(SearchExecuteRequest request) {
    // 원본 쿼리 보관
    String originalQuery = request.getQuery();

    // 모델명 추출 (검색용 - 확장 없음)
    List<String> models = ModelExtractor.extractModelsForSearch(originalQuery);

    // 단위 추출 (검색용 - 확장 없음)
    List<String> units = UnitExtractor.extractUnitsForSearch(originalQuery);

    // 모델명과 단위를 제거한 쿼리 생성
    String queryWithoutModels = TextPreprocessor.removeModels(originalQuery, models);
    String queryWithoutUnits = removeUnitsFromQuery(queryWithoutModels, units);

    // 처리된 쿼리
    String processedQuery = processQuery(queryWithoutUnits, request.getApplyTypoCorrection());

    List<Query> mustMatchQueries =
        buildMustMatchQueries(processedQuery, originalQuery, units, models);
    List<Query> filterQueries = buildFilterQueries(request);
    List<Query> shouldBoostQueries = buildCategoryBoostQueries(originalQuery);

    return BoolQuery.of(
        b -> {
          if (!mustMatchQueries.isEmpty()) {
            b.must(
                Query.of(
                    q ->
                        q.bool(nested -> nested.should(mustMatchQueries).minimumShouldMatch("1"))));
          }
          if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
          }
          if (!shouldBoostQueries.isEmpty()) {
            b.should(shouldBoostQueries);
          }
          return b;
        });
  }

  private List<Query> buildMustMatchQueries(
      String query, String originalQuery, List<String> units, List<String> models) {
    // 각 쿼리 생성 (없으면 match_all)
    Query mainFieldQuery =
        (query != null && !query.trim().isEmpty())
            ? Query.of(
                q ->
                    q.multiMatch(
                        m ->
                            m.query(query)
                                .fields(ESFields.CROSS_FIELDS_MAIN)
                                .type(TextQueryType.CrossFields)
                                .operator(
                                    co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                .boost(10.0f)))
            : Query.of(q -> q.matchAll(m -> m));

    // CROSS_FIELDS_BIGRAM에는 원본 쿼리를 그대로 사용
    Query bigramFieldQuery =
        (originalQuery != null && !originalQuery.trim().isEmpty())
            ? Query.of(
                q ->
                    q.multiMatch(
                        m ->
                            m.query(originalQuery)
                                .fields(ESFields.CROSS_FIELDS_BIGRAM)
                                .type(TextQueryType.CrossFields)
                                .operator(
                                    co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                .boost(5.0f)))
            : Query.of(q -> q.matchAll(m -> m));

    final Query modelQuery =
        Optional.ofNullable(buildModelQuery(models)).orElse(Query.of(q -> q.matchAll(m -> m)));

    final Query unitQuery =
        Optional.ofNullable(buildUnitQuery(units)).orElse(Query.of(q -> q.matchAll(m -> m)));

    // 메인 그룹: 한국어 필드 AND 모델 AND 단위
    Query mainGroup =
        Query.of(q -> q.bool(b -> b.must(mainFieldQuery).must(modelQuery).must(unitQuery)));

    // 바이그램 그룹: 바이그램 필드 AND 단위
    Query bigramGroup = Query.of(q -> q.bool(b -> b.must(bigramFieldQuery).must(unitQuery)));

    // 최종: 메인 그룹 OR 바이그램 그룹
    return List.of(
        Query.of(
            q -> q.bool(b -> b.should(mainGroup).should(bigramGroup).minimumShouldMatch("1"))));
  }

  private List<Query> buildFilterQueries(SearchExecuteRequest request) {
    return Optional.ofNullable(request.getFilters())
        .map(
            filters -> {
              List<Query> filterQueries = new ArrayList<>();
              addTermsFilter(filterQueries, ESFields.BRAND_NAME, filters.getBrand());
              addTermsFilter(filterQueries, ESFields.CATEGORY_NAME, filters.getCategory());
              addPriceRangeFilter(filterQueries, filters.getPriceRange());
              return filterQueries;
            })
        .orElse(new ArrayList<>());
  }

  private void addTermsFilter(List<Query> filterQueries, String field, List<String> values) {
    Optional.ofNullable(values)
        .filter(list -> !list.isEmpty())
        .ifPresent(
            list ->
                filterQueries.add(
                    Query.of(
                        q ->
                            q.terms(
                                t ->
                                    t.field(field)
                                        .terms(
                                            terms ->
                                                terms.value(
                                                    list.stream()
                                                        .map(FieldValue::of)
                                                        .toList()))))));
  }

  private void addPriceRangeFilter(List<Query> filterQueries, PriceRangeDto priceRange) {
    Optional.ofNullable(priceRange)
        .filter(pr -> pr.getFrom() != null || pr.getTo() != null)
        .ifPresent(
            pr ->
                filterQueries.add(
                    Query.of(
                        q ->
                            q.range(
                                r ->
                                    r.number(
                                        n ->
                                            n.field(ESFields.PRICE)
                                                .gte(
                                                    Optional.ofNullable(pr.getFrom())
                                                        .map(Number::doubleValue)
                                                        .orElse(null))
                                                .lte(
                                                    Optional.ofNullable(pr.getTo())
                                                        .map(Number::doubleValue)
                                                        .orElse(null)))))));
  }

  private String processQuery(String query, Boolean applyTypoCorrection) {
    String normalizedQuery = TextPreprocessor.normalizeUnits(query);
    String processedQuery = TextPreprocessor.preprocess(normalizedQuery);

    if (shouldApplyTypoCorrection(applyTypoCorrection)) {
      String correctedQuery = typoCorrectionService.applyTypoCorrection(processedQuery);
      if (!correctedQuery.equals(processedQuery)) {
        log.info("오타교정 적용 - 원본: '{}', 교정: '{}'", processedQuery, correctedQuery);
      }
      return correctedQuery;
    }

    return processedQuery;
  }

  private boolean shouldApplyTypoCorrection(Boolean applyTypoCorrection) {
    return Optional.ofNullable(applyTypoCorrection).filter(Boolean::booleanValue).isPresent();
  }

  private Query buildUnitQuery(List<String> units) {
    if (units == null || units.isEmpty()) {
      return null;
    }

    List<Query> unitQueries = new ArrayList<>();

    for (String unit : units) {
      if (!unit.isEmpty()) {
        // units 필드에서 매칭
        unitQueries.add(
            Query.of(
                q -> q.match(m -> m.field(ESFields.UNITS).query(unit))));
      }
    }

    if (unitQueries.isEmpty()) {
      return null;
    }

    // 여러 단위가 있으면 모두 AND 조건
    if (unitQueries.size() == 1) {
      return unitQueries.get(0);
    } else {
      return Query.of(q -> q.bool(b -> b.must(unitQueries)));
    }
  }

  private String removeUnitsFromQuery(String query, List<String> units) {
    if (units == null || units.isEmpty()) {
      return query;
    }

    String result = query;

    for (String unit : units) {
      if (!unit.isEmpty()) {
        // 단위를 쿼리에서 제거 (대소문자 무시)
        result = result.replaceAll("(?i)\\b" + Pattern.quote(unit) + "\\b", "");
      }
    }

    // 여러 공백을 하나로 정리
    return result.replaceAll("\\s+", " ").trim();
  }

  private Query buildModelQuery(List<String> models) {
    if (models == null || models.isEmpty()) {
      return null;
    }

    // 스트림으로 모델 쿼리 생성 (최적화)
    List<Query> modelQueries =
        models.stream()
            .map(
                model ->
                    Query.of(q -> q.match(m -> m.field(ESFields.MODEL).query(model).boost(3.0f))))
            .toList();

    // 단일 모델은 바로 반환, 여러 모델은 AND 조건으로
    return modelQueries.size() == 1
        ? modelQueries.get(0)
        : Query.of(q -> q.bool(b -> b.must(modelQueries)));
  }

  private List<Query> buildCategoryBoostQueries(String query) {
    Map<String, Integer> categoryWeights = categoryRankingService.getCategoryWeights(query);

    if (categoryWeights.isEmpty()) {
      return List.of();
    }

    List<Query> boostQueries = new ArrayList<>();

    for (Map.Entry<String, Integer> entry : categoryWeights.entrySet()) {
      String category = entry.getKey();
      Integer weight = entry.getValue();

      // constant_score 쿼리로 카테고리에 고정 점수 부여
      Query categoryBoostQuery =
          Query.of(
              q ->
                  q.constantScore(
                      cs ->
                          cs.filter(
                                  f -> f.term(t -> t.field(ESFields.CATEGORY_NAME).value(category)))
                              .boost(weight.floatValue())));

      boostQueries.add(categoryBoostQuery);
    }

    return boostQueries;
  }
}
