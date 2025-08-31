package com.yjlee.search.search.service.builder;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.service.builder.model.ExtractedTerms;
import com.yjlee.search.search.service.builder.model.QueryContext;
import com.yjlee.search.search.service.builder.query.BoostQueryBuilder;
import com.yjlee.search.search.service.builder.query.FilterQueryBuilder;
import com.yjlee.search.search.service.builder.query.MainQueryBuilder;
import com.yjlee.search.search.service.builder.query.QueryProcessor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryBuilder {

  private final QueryProcessor queryProcessor;
  private final MainQueryBuilder mainQueryBuilder;
  private final FilterQueryBuilder filterQueryBuilder;
  private final BoostQueryBuilder boostQueryBuilder;

  public BoolQuery buildBoolQuery(SearchExecuteRequest request) {
    String originalQuery = request.getQuery();

    // 쿼리가 없는 경우 필터만 적용
    if (originalQuery == null || originalQuery.trim().isEmpty()) {
      return buildFilterOnlyQuery(request);
    }

    // 특수 용어 추출 및 쿼리 처리
    ExtractedTerms extractedTerms = queryProcessor.extractSpecialTerms(originalQuery);
    String queryWithoutUnits =
        queryProcessor.removeUnitsFromQuery(originalQuery, extractedTerms.getUnits());
    String processedQuery =
        queryProcessor
            .processQuery(queryWithoutUnits, request.getApplyTypoCorrection())
            .getFinalQuery();

    // QueryContext 생성
    QueryContext context =
        QueryContext.builder()
            .originalQuery(originalQuery)
            .processedQuery(processedQuery)
            .units(extractedTerms.getUnits())
            .models(extractedTerms.getModels())
            .applyTypoCorrection(request.getApplyTypoCorrection())
            .build();

    // 쿼리 구성 요소 빌드
    Query mainQuery = mainQueryBuilder.buildMainQuery(context);
    List<Query> filterQueries = filterQueryBuilder.buildFilterQueries(request.getFilters());
    List<Query> categoryBoostQueries = boostQueryBuilder.buildCategoryBoostQueries(originalQuery);

    // 최종 BoolQuery 조합
    return BoolQuery.of(
        b -> {
          if (mainQuery != null) {
            b.must(mainQuery);
          } else {
            b.must(Query.of(q -> q.matchAll(m -> m)));
          }

          if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
          }

          if (!categoryBoostQueries.isEmpty()) {
            b.should(categoryBoostQueries);
          }

          return b;
        });
  }

  private BoolQuery buildFilterOnlyQuery(SearchExecuteRequest request) {
    List<Query> filterQueries = filterQueryBuilder.buildFilterQueries(request.getFilters());

    return BoolQuery.of(
        b -> {
          b.must(Query.of(q -> q.matchAll(m -> m)));
          if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
          }
          return b;
        });
  }
}
