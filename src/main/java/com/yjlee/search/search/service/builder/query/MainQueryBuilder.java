package com.yjlee.search.search.service.builder.query;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.constants.SearchBoostConstants;
import com.yjlee.search.search.service.builder.model.QueryContext;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MainQueryBuilder {

  private final QueryProcessor queryProcessor;
  private final BoostQueryBuilder boostQueryBuilder;

  public Query buildMainQuery(QueryContext context) {
    if (context.isEmpty()) {
      return buildEmptyQueryCase(context);
    }

    // 3phase 검색 로직
    return buildThreePhaseQuery(context);
  }

  private Query buildEmptyQueryCase(QueryContext context) {
    if (context.hasOnlyUnits()) {
      return buildUnitOnlyQuery(context);
    }

    if (context.hasOnlyModels()) {
      return buildModelOnlyQuery(context.getModels());
    }

    if (context.hasUnitsAndModels()) {
      return buildUnitsAndModelsQuery(context);
    }

    return Query.of(q -> q.matchAll(m -> m));
  }

  private Query buildUnitOnlyQuery(QueryContext context) {
    // 단위만 있는 경우: 전체 검색
    return Query.of(q -> q.matchAll(m -> m));
  }

  private Query buildUnitsAndModelsQuery(QueryContext context) {
    // 단위와 모델명만 있는 경우: 전체 검색
    return Query.of(q -> q.matchAll(m -> m));
  }

  private Query buildThreePhaseQuery(QueryContext context) {
    // Phase 1: 원본 쿼리 그대로 CROSS_FIELDS
    Query originalQuery = buildCrossFieldsQuery(context.getProcessedQuery());

    // Phase 2: 단위/모델명 제외한 쿼리 (context에서 이미 계산됨)
    String queryWithoutTerms = context.getQueryWithoutTerms();

    Query phaseTwo = null;
    if (context.hasQueryWithoutTerms() || context.hasUnits() || context.hasModels()) {
      BoolQuery.Builder phaseTwoBuilder = new BoolQuery.Builder();

      // 단위/모델 제외한 쿼리가 있으면 CROSS_FIELDS로 검색
      if (context.hasQueryWithoutTerms()) {
        phaseTwoBuilder.must(buildCrossFieldsQuery(queryWithoutTerms));
      }

      // 단위가 있으면 확장된 단위들로 검색
      if (context.hasUnits() && context.hasExpandedUnits()) {
        for (String unit : context.getUnits()) {
          Set<String> expandedUnits = context.getExpandedUnits().get(unit);

          if (expandedUnits != null && !expandedUnits.isEmpty()) {
            if (expandedUnits.size() == 1) {
              // 확장 결과가 하나면 그대로 must
              phaseTwoBuilder.must(Query.of(q -> q.match(m -> m.field(ESFields.UNIT).query(unit))));
            } else {
              // 확장 결과가 여러개면 should로 묶어서 must (OR 조건)
              List<Query> unitQueries =
                  expandedUnits.stream()
                      .map(
                          expandedUnit ->
                              Query.of(
                                  q -> q.match(m -> m.field(ESFields.UNIT).query(expandedUnit))))
                      .toList();
              phaseTwoBuilder.must(
                  Query.of(q -> q.bool(b -> b.should(unitQueries).minimumShouldMatch("1"))));
            }
          }
        }
      }

      // 모델명이 있으면 bigram analyzer로 검색 (AND 조건)
      if (context.hasModels()) {
        for (String model : context.getModels()) {
          phaseTwoBuilder.must(
              Query.of(
                  q -> q.match(m -> m.field(ESFields.MODEL).query(model).operator(Operator.And))));
        }
      }

      phaseTwo = Query.of(q -> q.bool(phaseTwoBuilder.build()));
    }

    // Phase 1과 Phase 2를 OR로 연결
    BoolQuery.Builder mainQueryBuilder = new BoolQuery.Builder();
    if (phaseTwo != null) {
      mainQueryBuilder.should(originalQuery);
      mainQueryBuilder.should(phaseTwo);
      mainQueryBuilder.minimumShouldMatch("1");
    } else {
      mainQueryBuilder.must(originalQuery);
    }

    // Phrase 부스팅만 추가
    List<Query> phraseBoostQueries =
        boostQueryBuilder.buildPhraseBoostQueries(context.getProcessedQuery());
    if (!phraseBoostQueries.isEmpty()) {
      mainQueryBuilder.should(phraseBoostQueries);
    }

    return Query.of(q -> q.bool(mainQueryBuilder.build()));
  }

  private Query buildCrossFieldsQuery(String query) {
    return Query.of(
        q ->
            q.multiMatch(
                m ->
                    m.query(query)
                        .fields(ESFields.CROSS_FIELDS_MAIN)
                        .type(TextQueryType.CrossFields)
                        .operator(Operator.And)
                        .boost(SearchBoostConstants.CROSS_FIELDS_BOOST)
                        .autoGenerateSynonymsPhraseQuery(false)));
  }

  private Query buildModelOnlyQuery(List<String> models) {
    if (models == null || models.isEmpty()) {
      return null;
    }

    // 모델명만 있는 경우: 전체 검색
    return Query.of(q -> q.matchAll(m -> m));
  }
}
