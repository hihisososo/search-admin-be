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

    // 단위 추출 (검색용 - 확장 없음) - 먼저 추출
    List<String> units = UnitExtractor.extractUnitsForSearch(originalQuery);

    // 단위를 제외한 후 모델명 추출 (단위와 중복 방지)
    List<String> models = ModelExtractor.extractModelsExcludingUnits(originalQuery, units);

    // 단위만 제거한 쿼리 생성 (모델명은 유지)
    String queryWithoutUnits = removeUnitsFromQuery(originalQuery, units);

    // 처리된 쿼리
    String processedQuery = processQuery(queryWithoutUnits, request.getApplyTypoCorrection());

    Query mainQuery = buildMainQuery(processedQuery, originalQuery, units, models);
    List<Query> filterQueries = buildFilterQueries(request);
    List<Query> shouldBoostQueries = buildCategoryBoostQueries(originalQuery);

    return BoolQuery.of(
        b -> {
          if (mainQuery != null) {
            b.must(mainQuery);
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

  private Query buildMainQuery(
      String query, String originalQuery, List<String> units, List<String> models) {
    // 기본 쿼리가 없으면 match_all
    if (query == null || query.trim().isEmpty()) {
      return Query.of(q -> q.matchAll(m -> m));
    }

    // 모델명이 있는 경우 분리 쿼리 전략 사용
    Query mainFieldQuery;
    if (models != null && !models.isEmpty()) {
      // 모델명 제외한 쿼리 생성
      String queryWithoutModels = removeModelsFromQuery(query, models);
      
      // OR 조건으로 두 쿼리 결합
      List<Query> orQueries = new ArrayList<>();
      
      // 1. 원본쿼리로 CROSS_FIELDS (model.bigram 제외)
      orQueries.add(
          Query.of(
              q ->
                  q.multiMatch(
                      m ->
                          m.query(query)
                              .fields(ESFields.CROSS_FIELDS_WITHOUT_MODEL)
                              .type(TextQueryType.CrossFields)
                              .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                              .boost(1.0f))));
      
      // 2. 모델명제외쿼리 + 모델명 별도 AND 검색
      if (queryWithoutModels != null && !queryWithoutModels.trim().isEmpty()) {
        Query modelFieldsQuery = Query.of(
            q ->
                q.multiMatch(
                    m ->
                        m.query(queryWithoutModels)
                            .fields(ESFields.CROSS_FIELDS_WITHOUT_MODEL)
                            .type(TextQueryType.CrossFields)
                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                            .boost(1.0f)));
        
        // 모델명들을 model.edge_ngram에서 검색 (AND 조건)
        List<Query> modelQueries = models.stream()
            .map(model -> 
                Query.of(q -> q.match(m -> m.field(ESFields.MODEL_EDGE_NGRAM).query(model).boost(1.0f))))
            .toList();
        
        Query combinedModelQuery = modelQueries.size() == 1 
            ? modelQueries.get(0) 
            : Query.of(q -> q.bool(b -> b.must(modelQueries)));
        
        // 모델명제외쿼리와 모델쿼리를 AND로 결합
        orQueries.add(
            Query.of(q -> q.bool(b -> b.must(modelFieldsQuery).must(combinedModelQuery))));
      }
      
      // OR 조건으로 결합
      mainFieldQuery = Query.of(q -> q.bool(b -> b.should(orQueries).minimumShouldMatch("1")));
    } else {
      // 모델명이 없는 경우 기존 방식 (model.bigram 제외)
      mainFieldQuery = Query.of(
          q ->
              q.multiMatch(
                  m ->
                      m.query(query)
                          .fields(ESFields.CROSS_FIELDS_WITHOUT_MODEL)
                          .type(TextQueryType.CrossFields)
                          .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                          .boost(1.0f)));
    }

    // Phrase matching 쿼리 생성
    List<Query> phraseBoostQueries = buildPhraseBoostQueries(query);

    // 모델 쿼리를 부가 점수로 변경
    List<Query> modelBoostQueries = buildModelBoostQueries(models);

    final Query unitQuery =
        Optional.ofNullable(buildUnitQuery(units)).orElse(Query.of(q -> q.matchAll(m -> m)));

    // 메인 쿼리: 한국어 필드 AND 단위 + phrase(부가 점수) + 모델(부가 점수)
    return Query.of(
        q -> {
          BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
          boolBuilder.must(mainFieldQuery).must(unitQuery);

          // phrase와 모델 쿼리를 should로 추가
          if (!phraseBoostQueries.isEmpty()) {
            boolBuilder.should(phraseBoostQueries);
          }
          if (!modelBoostQueries.isEmpty()) {
            boolBuilder.should(modelBoostQueries);
          }
          return q.bool(boolBuilder.build());
        });
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
        unitQueries.add(Query.of(q -> q.match(m -> m.field(ESFields.UNITS).query(unit))));
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

  private String removeModelsFromQuery(String query, List<String> models) {
    if (models == null || models.isEmpty()) {
      return query;
    }

    String result = query;

    for (String model : models) {
      if (!model.isEmpty()) {
        // 모델명을 쿼리에서 제거 (대소문자 무시)
        result = result.replaceAll("(?i)\\b" + Pattern.quote(model) + "\\b", "");
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

  private List<Query> buildModelBoostQueries(List<String> models) {
    if (models == null || models.isEmpty()) {
      return List.of();
    }

    // 모델별로 부가 점수 쿼리 생성 (OR 조건)
    return models.stream()
        .map(
            model ->
                Query.of(
                    q ->
                        q.match(m -> m.field(ESFields.MODEL).query(model).boost(8.0f)))) // 부스트 값 상향
        .toList();
  }

  private List<Query> buildPhraseBoostQueries(String query) {
    if (query == null || query.trim().isEmpty()) {
      return List.of();
    }

    List<Query> phraseQueries = new ArrayList<>();

    // name 필드에 대한 phrase matching (가장 높은 boost)
    phraseQueries.add(
        Query.of(q -> q.matchPhrase(mp -> mp.field(ESFields.NAME).query(query).boost(5.0f))));

    // specs 필드에 대한 phrase matching
    phraseQueries.add(
        Query.of(q -> q.matchPhrase(mp -> mp.field(ESFields.SPECS).query(query).boost(2.0f))));

    // model.edge_ngram 필드에 대한 phrase matching
    phraseQueries.add(
        Query.of(
            q -> q.matchPhrase(mp -> mp.field(ESFields.MODEL_EDGE_NGRAM).query(query).boost(3.0f))));

    // category 필드에 대한 phrase matching
    phraseQueries.add(
        Query.of(q -> q.matchPhrase(mp -> mp.field("category").query(query).boost(1.0f))));

    return phraseQueries;
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
