package com.yjlee.search.search.service.builder.query;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.service.builder.model.QueryContext;
import java.util.ArrayList;
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
    Query modelQuery = buildModelOnlyQuery(context.getModels());
    List<Query> unitBoostQueries = boostQueryBuilder.buildUnitBoostQueries(context.getUnits());

    return Query.of(
        q ->
            q.bool(
                b -> {
                  b.must(Query.of(mq -> mq.matchAll(m -> m)));
                  b.must(modelQuery);
                  if (!unitBoostQueries.isEmpty()) {
                    b.should(unitBoostQueries);
                  }
                  return b;
                }));
  }

  private Query buildModelQuery(QueryContext context) {
    String queryWithoutModels =
        queryProcessor.removeModelsFromQuery(context.getProcessedQuery(), context.getModels());

    List<Query> orQueries = new ArrayList<>();

    orQueries.add(buildCrossFieldsQuery(context.getProcessedQuery()));

    if (queryWithoutModels != null && !queryWithoutModels.trim().isEmpty()) {
      Query fieldsQuery = buildCrossFieldsQuery(queryWithoutModels);
      Query modelQueries = buildModelMatchQueries(context.getModels());

      orQueries.add(Query.of(q -> q.bool(b -> b.must(fieldsQuery).must(modelQueries))));
    }

    Query mainFieldQuery = Query.of(q -> q.bool(b -> b.should(orQueries).minimumShouldMatch("1")));

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
                        .boost(1.0f)));
  }

  private Query buildModelOnlyQuery(List<String> models) {
    if (models == null || models.isEmpty()) {
      return null;
    }

    List<Query> modelQueries =
        models.stream()
            .map(
                model ->
                    Query.of(q -> q.match(m -> m.field(ESFields.MODEL).query(model).boost(3.0f))))
            .toList();

    return modelQueries.size() == 1
        ? modelQueries.get(0)
        : Query.of(q -> q.bool(b -> b.must(modelQueries)));
  }

  private Query buildModelMatchQueries(List<String> models) {
    List<Query> modelQueries =
        models.stream()
            .map(
                model ->
                    Query.of(
                        q ->
                            q.match(
                                m -> m.field(ESFields.MODEL_EDGE_NGRAM).query(model).boost(1.0f))))
            .toList();

    return modelQueries.size() == 1
        ? modelQueries.get(0)
        : Query.of(q -> q.bool(b -> b.must(modelQueries)));
  }
}
