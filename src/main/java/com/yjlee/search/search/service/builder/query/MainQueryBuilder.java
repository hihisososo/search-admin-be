package com.yjlee.search.search.service.builder.query;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.constants.SearchBoostConstants;
import com.yjlee.search.search.service.builder.model.QueryContext;
import java.util.List;
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

    if (context.hasModels()) {
      return buildModelQuery(context);
    }

    return buildStandardQuery(context);
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
    List<Query> unitBoostQueries = boostQueryBuilder.buildUnitBoostQueries(context.getUnits());

    return Query.of(
        q ->
            q.bool(
                b -> {
                  b.must(Query.of(mq -> mq.matchAll(m -> m)));
                  if (!unitBoostQueries.isEmpty()) {
                    b.should(unitBoostQueries);
                  }
                  return b;
                }));
  }

  private Query buildUnitsAndModelsQuery(QueryContext context) {
    // 단위와 모델명만 있는 경우: 전체 검색 + 모델명 부스트 + 단위 부스트
    List<Query> modelBoostQueries = boostQueryBuilder.buildModelBoostQueries(context.getModels());
    List<Query> unitBoostQueries = boostQueryBuilder.buildUnitBoostQueries(context.getUnits());

    return Query.of(
        q ->
            q.bool(
                b -> {
                  b.must(Query.of(mq -> mq.matchAll(m -> m)));
                  if (!modelBoostQueries.isEmpty()) {
                    b.should(modelBoostQueries);
                  }
                  if (!unitBoostQueries.isEmpty()) {
                    b.should(unitBoostQueries);
                  }
                  return b;
                }));
  }

  private Query buildModelQuery(QueryContext context) {
    // 모델명을 쿼리에서 빼지 않고 원본 쿼리 그대로 사용
    Query mainFieldQuery = buildCrossFieldsQuery(context.getProcessedQuery());

    // 모델명은 부스트 쿼리로만 추가
    return combineWithBoostQueries(mainFieldQuery, context);
  }

  private Query buildStandardQuery(QueryContext context) {
    Query mainFieldQuery = buildCrossFieldsQuery(context.getProcessedQuery());
    return combineWithBoostQueries(mainFieldQuery, context);
  }

  private Query combineWithBoostQueries(Query mainFieldQuery, QueryContext context) {
    List<Query> phraseBoostQueries =
        boostQueryBuilder.buildPhraseBoostQueries(context.getProcessedQuery());
    List<Query> modelBoostQueries = boostQueryBuilder.buildModelBoostQueries(context.getModels());
    List<Query> unitBoostQueries = boostQueryBuilder.buildUnitBoostQueries(context.getUnits());

    BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
    boolBuilder.must(mainFieldQuery);

    if (!phraseBoostQueries.isEmpty()) {
      boolBuilder.should(phraseBoostQueries);
    }
    if (!modelBoostQueries.isEmpty()) {
      boolBuilder.should(modelBoostQueries);
    }
    if (!unitBoostQueries.isEmpty()) {
      boolBuilder.should(unitBoostQueries);
    }

    return Query.of(q -> q.bool(boolBuilder.build()));
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

    // 모델명만 있는 경우: 전체 검색 + 모델명 부스트
    List<Query> modelBoostQueries =
        models.stream()
            .map(
                model ->
                    Query.of(
                        q ->
                            q.match(
                                m ->
                                    m.field(ESFields.MODEL)
                                        .query(model)
                                        .boost(SearchBoostConstants.MODEL_MATCH_BOOST))))
            .toList();

    return Query.of(
        q ->
            q.bool(
                b -> {
                  b.must(Query.of(mq -> mq.matchAll(m -> m)));
                  if (!modelBoostQueries.isEmpty()) {
                    b.should(modelBoostQueries);
                  }
                  return b;
                }));
  }
}
