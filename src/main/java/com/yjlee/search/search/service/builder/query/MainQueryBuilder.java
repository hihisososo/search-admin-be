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

    // 3phase 검색 로직
    return buildThreePhaseQuery(context);
  }

  private Query buildEmptyQueryCase(QueryContext context) {
    return Query.of(q -> q.matchAll(m -> m));
  }

  private Query buildThreePhaseQuery(QueryContext context) {
    // 원본 쿼리 그대로 CROSS_FIELDS
    Query originalQuery = buildCrossFieldsQuery(context.getProcessedQuery());

    BoolQuery.Builder mainQueryBuilder = new BoolQuery.Builder();
    mainQueryBuilder.must(originalQuery);

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
}
