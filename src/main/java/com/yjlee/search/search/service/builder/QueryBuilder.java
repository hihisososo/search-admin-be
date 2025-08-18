package com.yjlee.search.search.service.builder;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.search.dto.PriceRangeDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryBuilder {

  private final TypoCorrectionService typoCorrectionService;

  public BoolQuery buildBoolQuery(SearchExecuteRequest request) {
    String query = processQuery(request.getQuery(), request.getApplyTypoCorrection());

    List<Query> mustMatchQueries = buildMustMatchQueries(query);
    List<Query> boostingQueries = buildBoostingQueries(query);
    List<Query> filterQueries = buildFilterQueries(request);

    return BoolQuery.of(
        b -> {
          if (!mustMatchQueries.isEmpty()) {
            b.must(
                Query.of(
                    q ->
                        q.bool(nested -> nested.should(mustMatchQueries).minimumShouldMatch("1"))));
          }
          if (!boostingQueries.isEmpty()) {
            b.should(boostingQueries);
          }
          if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
          }
          return b;
        });
  }

  private List<Query> buildMustMatchQueries(String query) {
    if (query == null || query.trim().isEmpty()) {
      return List.of(Query.of(q -> q.matchAll(m -> m)));
    }

    String[] terms = query.split("\\s+");

    // 메인 필드 그룹 - 모든 term이 AND 조건
    List<Query> mainFieldMustQueries = new ArrayList<>();
    for (String term : terms) {
      Query mainFieldQuery =
          Query.of(
              q ->
                  q.multiMatch(
                      m ->
                          m.query(term)
                              .fields(ESFields.CROSS_FIELDS_MAIN)
                              .type(TextQueryType.Phrase)
                              .boost(10.0f)));
      mainFieldMustQueries.add(mainFieldQuery);
    }

    // 바이그램 필드 그룹 - 모든 term이 AND 조건
    List<Query> bigramFieldMustQueries = new ArrayList<>();
    for (String term : terms) {
      Query bigramFieldQuery =
          Query.of(
              q ->
                  q.multiMatch(
                      m ->
                          m.query(term)
                              .fields(ESFields.CROSS_FIELDS_BIGRAM)
                              .type(TextQueryType.Phrase)
                              .boost(5.0f)));
      bigramFieldMustQueries.add(bigramFieldQuery);
    }

    // 메인 필드 그룹과 바이그램 필드 그룹을 OR 조건으로 결합
    List<Query> shouldQueries = new ArrayList<>();

    // 메인 필드 그룹 (모든 term AND)
    shouldQueries.add(Query.of(q -> q.bool(b -> b.must(mainFieldMustQueries))));

    // 바이그램 필드 그룹 (모든 term AND)
    shouldQueries.add(Query.of(q -> q.bool(b -> b.must(bigramFieldMustQueries))));

    return List.of(Query.of(q -> q.bool(b -> b.should(shouldQueries).minimumShouldMatch("1"))));
  }

  private List<Query> buildBoostingQueries(String query) {
    if (query == null || query.trim().isEmpty()) {
      return new ArrayList<>();
    }

    String[] queryWords = query.split("\\s+");

    return Arrays.stream(queryWords)
        .filter(word -> !word.trim().isEmpty())
        .flatMap(
            word ->
                ESFields.BOOST_FIELDS.stream()
                    .map(field -> createTermQuery(field, word.trim(), 3.0f)))
        .toList();
  }

  private Query createTermQuery(String field, String value, float boost) {
    return Query.of(q -> q.term(t -> t.field(field).value(value).boost(boost)));
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
}
