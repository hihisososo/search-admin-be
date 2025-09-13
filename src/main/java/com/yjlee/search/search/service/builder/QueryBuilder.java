package com.yjlee.search.search.service.builder;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.search.dto.SearchExecuteRequest;
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

  // 일반 검색용 - PROD 환경 사용
  public BoolQuery buildBoolQuery(SearchExecuteRequest request) {
    return buildBoolQuery(request, EnvironmentType.PROD);
  }

  // 시뮬레이션 검색용 - 환경 지정 가능
  public BoolQuery buildBoolQuery(SearchExecuteRequest request, EnvironmentType environment) {
    String originalQuery = request.getQuery();

    // 쿼리가 없는 경우 필터만 적용
    if (originalQuery == null || originalQuery.trim().isEmpty()) {
      return buildFilterOnlyQuery(request);
    }

    // 통합 쿼리 분석 - 모든 분석을 한 번에 수행
    QueryContext context =
        queryProcessor.analyzeQuery(originalQuery, request.getApplyTypoCorrection());

    // 쿼리 구성 요소 빌드
    Query mainQuery = mainQueryBuilder.buildMainQuery(context);
    List<Query> filterQueries = filterQueryBuilder.buildFilterQueries(request.getFilters());
    List<Query> categoryBoostQueries =
        boostQueryBuilder.buildCategoryBoostQueries(originalQuery, environment);

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
