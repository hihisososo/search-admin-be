package com.yjlee.search.stats.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.stats.domain.KeywordStats;
import com.yjlee.search.stats.domain.SearchStats;
import com.yjlee.search.stats.domain.TrendData;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ElasticsearchStatsRepository implements StatsRepository {

  private final ElasticsearchClient elasticsearchClient;

  @Override
  public SearchStats getSearchStats(LocalDateTime from, LocalDateTime to) {
    try {
      SearchRequest searchRequest = buildStatsSearchRequest(from, to);
      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      long clickCount = getClickCount(from, to);
      long searchesWithClicks = getSearchesWithClicks(from, to);
      return parseStatsResponse(response, clickCount, searchesWithClicks);

    } catch (Exception e) {
      log.error("통계 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("통계 조회 실패", e);
    }
  }

  @Override
  public List<KeywordStats> getPopularKeywords(LocalDateTime from, LocalDateTime to, int limit) {
    try {
      // 이전 주기 계산
      long periodDays = java.time.Duration.between(from, to).toDays();
      LocalDateTime previousFrom = from.minusDays(periodDays);
      LocalDateTime previousTo = from;

      SearchRequest searchRequest =
          buildPopularKeywordsSearchRequest(from, to, previousFrom, previousTo);
      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return parsePopularKeywordsResponse(response, limit);

    } catch (Exception e) {
      log.error("인기검색어 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("인기검색어 조회 실패", e);
    }
  }

  @Override
  public List<TrendData> getTrends(LocalDateTime from, LocalDateTime to, String interval) {
    try {
      // interval은 day만 지원 (고정)
      SearchRequest searchRequest = buildTrendsSearchRequest(from, to, "day");
      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return parseTrendsResponse(response, "day");

    } catch (Exception e) {
      log.error("추이 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("추이 조회 실패", e);
    }
  }

  @Override
  public long getTotalSearchCount(LocalDateTime from, LocalDateTime to) {
    try {
      BoolQuery boolQuery = buildDateRangeQuery(from, to, "search-logs-*");

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("search-logs-*")
                      .size(0)
                      .query(Query.of(q -> q.bool(boolQuery)))
                      .aggregations(
                          "total", Aggregation.of(a -> a.valueCount(v -> v.field("timestamp")))));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return extractLongValue(response.aggregations(), "total", "value");

    } catch (Exception e) {
      log.error("검색 횟수 조회 실패: {}", e.getMessage(), e);
      return 0L;
    }
  }

  @Override
  public long getClickCount(LocalDateTime from, LocalDateTime to) {
    try {
      BoolQuery boolQuery = buildDateRangeQuery(from, to, "click-logs-*");

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("click-logs-*")
                      .size(0)
                      .query(Query.of(q -> q.bool(boolQuery)))
                      .aggregations(
                          "total", Aggregation.of(a -> a.valueCount(v -> v.field("timestamp")))));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return extractLongValue(response.aggregations(), "total", "value");

    } catch (Exception e) {
      log.error("클릭 횟수 조회 실패: {}", e.getMessage(), e);
      return 0L;
    }
  }

  // 클릭이 발생한 고유 검색 세션 수 조회
  private long getSearchesWithClicks(LocalDateTime from, LocalDateTime to) {
    try {
      BoolQuery boolQuery = buildDateRangeQuery(from, to, "click-logs-*");

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("click-logs-*")
                      .size(0)
                      .query(Query.of(q -> q.bool(boolQuery)))
                      .aggregations(
                          "unique_sessions",
                          Aggregation.of(a -> a.cardinality(c -> c.field("session_id")))));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      var cardinalityAgg = response.aggregations().get("unique_sessions");
      if (cardinalityAgg != null && cardinalityAgg.isCardinality()) {
        return (long) cardinalityAgg.cardinality().value();
      }
      return 0L;

    } catch (Exception e) {
      log.error("클릭 발생 세션 수 조회 실패: {}", e.getMessage(), e);
      return 0L;
    }
  }

  @Override
  public long getClickCountForKeyword(String keyword, LocalDateTime from, LocalDateTime to) {
    try {
      BoolQuery boolQuery =
          BoolQuery.of(
              b ->
                  b.must(
                          Query.of(
                              q ->
                                  q.range(
                                      r ->
                                          r.date(
                                              d ->
                                                  d.field("timestamp")
                                                      .gte(from.toString())
                                                      .lte(to.toString())))))
                      .must(
                          Query.of(
                              q -> q.term(t -> t.field("searchKeyword.keyword").value(keyword)))));

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("click-logs-*")
                      .size(0)
                      .query(Query.of(q -> q.bool(boolQuery)))
                      .aggregations(
                          "total", Aggregation.of(a -> a.valueCount(v -> v.field("timestamp")))));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return extractLongValue(response.aggregations(), "total", "value");

    } catch (Exception e) {
      log.error("키워드별 클릭 횟수 조회 실패: {}", e.getMessage(), e);
      return 0L;
    }
  }

  // 특정 키워드로 검색 후 클릭이 발생한 세션 수
  private long getSearchesWithClicksForKeyword(
      String keyword, LocalDateTime from, LocalDateTime to) {
    try {
      BoolQuery boolQuery =
          BoolQuery.of(
              b ->
                  b.must(
                          Query.of(
                              q ->
                                  q.range(
                                      r ->
                                          r.date(
                                              d ->
                                                  d.field("timestamp")
                                                      .gte(from.toString())
                                                      .lte(to.toString())))))
                      .must(
                          Query.of(
                              q -> q.term(t -> t.field("searchKeyword.keyword").value(keyword)))));

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("click-logs-*")
                      .size(0)
                      .query(Query.of(q -> q.bool(boolQuery)))
                      .aggregations(
                          "unique_sessions",
                          Aggregation.of(a -> a.cardinality(c -> c.field("session_id")))));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      var cardinalityAgg = response.aggregations().get("unique_sessions");
      if (cardinalityAgg != null && cardinalityAgg.isCardinality()) {
        return (long) cardinalityAgg.cardinality().value();
      }
      return 0L;

    } catch (Exception e) {
      log.error("키워드별 클릭 세션 수 조회 실패 - 키워드: {}", keyword, e);
      return 0L;
    }
  }

  @Override
  public Map<String, Long> getClickCountsForKeywords(
      List<String> keywords, LocalDateTime from, LocalDateTime to) {
    try {
      BoolQuery boolQuery =
          BoolQuery.of(
              b ->
                  b.must(
                          Query.of(
                              q ->
                                  q.range(
                                      r ->
                                          r.date(
                                              d ->
                                                  d.field("timestamp")
                                                      .gte(from.toString())
                                                      .lte(to.toString())))))
                      .must(
                          Query.of(
                              q ->
                                  q.terms(
                                      t ->
                                          t.field("searchKeyword.keyword")
                                              .terms(
                                                  tv ->
                                                      tv.value(
                                                          keywords.stream()
                                                              .map(
                                                                  co.elastic.clients.elasticsearch
                                                                          ._types.FieldValue
                                                                      ::of)
                                                              .collect(Collectors.toList())))))));

      Map<String, Aggregation> aggregations =
          Map.of(
              "keywords",
              Aggregation.of(
                  a -> a.terms(t -> t.field("searchKeyword.keyword").size(keywords.size()))));

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("click-logs-*")
                      .size(0)
                      .query(Query.of(q -> q.bool(boolQuery)))
                      .aggregations(aggregations));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      Map<String, Long> clickCounts = new HashMap<>();
      var keywordsAgg = response.aggregations().get("keywords");

      if (keywordsAgg != null && keywordsAgg._kind().jsonValue().equals("sterms")) {
        var termsAgg = keywordsAgg.sterms();
        var buckets = termsAgg.buckets().array();

        for (var bucket : buckets) {
          String keyword = bucket.key().stringValue();
          long count = bucket.docCount();
          clickCounts.put(keyword, count);
        }
      }

      // 결과에 없는 키워드는 0으로 설정
      for (String keyword : keywords) {
        clickCounts.putIfAbsent(keyword, 0L);
      }

      return clickCounts;

    } catch (Exception e) {
      // 인덱스가 없거나 데이터가 없는 경우는 debug 레벨로 기록
      if (e.getMessage() != null && e.getMessage().contains("index_not_found")) {
        log.debug("클릭 로그 인덱스가 없음 - 기간: {} ~ {}", from, to);
      } else {
        log.error("키워드별 클릭 횟수 일괄 조회 중 에러 발생: {}", e.getMessage(), e);
      }
      // 모든 키워드에 대해 0을 반환
      return keywords.stream().collect(Collectors.toMap(k -> k, k -> 0L));
    }
  }

  @Override
  public Map<String, Long> getSearchesWithClicksForKeywords(
      List<String> keywords, LocalDateTime from, LocalDateTime to) {
    try {
      BoolQuery boolQuery =
          BoolQuery.of(
              b ->
                  b.must(
                          Query.of(
                              q ->
                                  q.range(
                                      r ->
                                          r.date(
                                              d ->
                                                  d.field("timestamp")
                                                      .gte(from.toString())
                                                      .lte(to.toString())))))
                      .must(
                          Query.of(
                              q ->
                                  q.terms(
                                      t ->
                                          t.field("searchKeyword.keyword")
                                              .terms(
                                                  tv ->
                                                      tv.value(
                                                          keywords.stream()
                                                              .map(
                                                                  co.elastic.clients.elasticsearch
                                                                          ._types.FieldValue
                                                                      ::of)
                                                              .collect(Collectors.toList())))))));

      Map<String, Aggregation> subAggregations =
          Map.of("unique_sessions", Aggregation.of(a -> a.cardinality(c -> c.field("session_id"))));

      Map<String, Aggregation> aggregations =
          Map.of(
              "keywords",
              Aggregation.of(
                  a ->
                      a.terms(t -> t.field("searchKeyword.keyword").size(keywords.size()))
                          .aggregations(subAggregations)));

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("click-logs-*")
                      .size(0)
                      .query(Query.of(q -> q.bool(boolQuery)))
                      .aggregations(aggregations));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      Map<String, Long> searchesWithClicks = new HashMap<>();
      var keywordsAgg = response.aggregations().get("keywords");

      if (keywordsAgg != null && keywordsAgg._kind().jsonValue().equals("sterms")) {
        var termsAgg = keywordsAgg.sterms();
        var buckets = termsAgg.buckets().array();

        for (var bucket : buckets) {
          String keyword = bucket.key().stringValue();
          var cardinalityAgg = bucket.aggregations().get("unique_sessions");
          if (cardinalityAgg != null && cardinalityAgg.isCardinality()) {
            long count = (long) cardinalityAgg.cardinality().value();
            searchesWithClicks.put(keyword, count);
          } else {
            searchesWithClicks.put(keyword, 0L);
          }
        }
      }

      // 결과에 없는 키워드는 0으로 설정
      for (String keyword : keywords) {
        searchesWithClicks.putIfAbsent(keyword, 0L);
      }

      return searchesWithClicks;

    } catch (Exception e) {
      // 인덱스가 없거나 데이터가 없는 경우는 debug 레벨로 기록
      if (e.getMessage() != null && e.getMessage().contains("index_not_found")) {
        log.debug("클릭 로그 인덱스가 없음 - 기간: {} ~ {}", from, to);
      } else {
        log.error("키워드별 클릭 세션수 일괄 조회 중 에러 발생: {}", e.getMessage(), e);
      }
      // 모든 키워드에 대해 0을 반환
      return keywords.stream().collect(Collectors.toMap(k -> k, k -> 0L));
    }
  }

  // (removed) getSearchCountForKeyword: 현재 미사용

  private BoolQuery buildDateRangeQuery(LocalDateTime from, LocalDateTime to, String index) {
    return BoolQuery.of(
        b ->
            b.must(
                Query.of(
                    q ->
                        q.range(
                            r ->
                                r.date(
                                    d ->
                                        d.field("timestamp")
                                            .gte(from.toString())
                                            .lte(to.toString()))))));
  }

  private SearchRequest buildStatsSearchRequest(LocalDateTime from, LocalDateTime to) {
    BoolQuery boolQuery = buildDateRangeQuery(from, to, "search-logs-*");

    Map<String, Aggregation> aggregations =
        Map.of(
            "total_searches", Aggregation.of(a -> a.valueCount(v -> v.field("timestamp"))),
            "total_documents", Aggregation.of(a -> a.sum(s -> s.field("result_count"))),
            "search_failures",
                Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("result_count").value(0)))),
            "errors",
                Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("is_error").value(true)))),
            "successful_searches",
                Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("is_error").value(false)))),
            "avg_response_time", Aggregation.of(a -> a.avg(avg -> avg.field("response_time_ms"))));

    return SearchRequest.of(
        s ->
            s.index("search-logs-*")
                .size(0)
                .query(Query.of(q -> q.bool(boolQuery)))
                .aggregations(aggregations));
  }

  private SearchRequest buildPopularKeywordsSearchRequest(
      LocalDateTime from, LocalDateTime to, LocalDateTime previousFrom, LocalDateTime previousTo) {

    Map<String, Aggregation> aggregations = new HashMap<>();

    // 전체 기간에 대한 날짜 범위 쿼리 (이전 주기부터 현재 주기까지)
    BoolQuery dateRangeQuery =
        BoolQuery.of(
            b ->
                b.must(
                        Query.of(
                            q ->
                                q.range(
                                    r ->
                                        r.date(
                                            d ->
                                                d.field("timestamp")
                                                    .gte(previousFrom.toString())
                                                    .lte(to.toString())))))
                    .must(Query.of(q -> q.term(t -> t.field("is_error").value(false)))));

    // 현재 주기 집계
    aggregations.put(
        "current_period",
        Aggregation.of(
            a ->
                a.filter(
                        f ->
                            f.bool(
                                b ->
                                    b.must(
                                            Query.of(
                                                q ->
                                                    q.range(
                                                        r ->
                                                            r.date(
                                                                d ->
                                                                    d.field("timestamp")
                                                                        .gte(from.toString())
                                                                        .lte(to.toString())))))
                                        .must(
                                            Query.of(
                                                q ->
                                                    q.term(
                                                        t -> t.field("is_error").value(false))))))
                    .aggregations(
                        "keywords",
                        Aggregation.of(
                            ag -> ag.terms(t -> t.field("search_keyword.keyword").size(50))))));

    // 이전 주기 집계
    aggregations.put(
        "previous_period",
        Aggregation.of(
            a ->
                a.filter(
                        f ->
                            f.bool(
                                b ->
                                    b.must(
                                            Query.of(
                                                q ->
                                                    q.range(
                                                        r ->
                                                            r.date(
                                                                d ->
                                                                    d.field("timestamp")
                                                                        .gte(
                                                                            previousFrom.toString())
                                                                        .lte(
                                                                            previousTo
                                                                                .toString())))))
                                        .must(
                                            Query.of(
                                                q ->
                                                    q.term(
                                                        t -> t.field("is_error").value(false))))))
                    .aggregations(
                        "keywords",
                        Aggregation.of(
                            ag -> ag.terms(t -> t.field("search_keyword.keyword").size(50))))));

    return SearchRequest.of(
        s ->
            s.index("search-logs-*")
                .size(0)
                .query(Query.of(q -> q.bool(dateRangeQuery)))
                .aggregations(aggregations));
  }

  private SearchRequest buildTrendsSearchRequest(
      LocalDateTime from, LocalDateTime to, String interval) {
    // day 전용
    String dateHistogramInterval = "1d";

    BoolQuery boolQuery = buildDateRangeQuery(from, to, "search-logs-*");

    Map<String, Aggregation> subAggregations =
        Map.of(
            "search_count", Aggregation.of(a -> a.valueCount(v -> v.field("timestamp"))),
            "error_count",
                Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("is_error").value(true)))),
            "avg_response_time", Aggregation.of(a -> a.avg(avg -> avg.field("response_time_ms"))));

    Map<String, Aggregation> aggregations =
        Map.of(
            "time_buckets",
            Aggregation.of(
                a ->
                    a.dateHistogram(
                            dh ->
                                dh.field("timestamp")
                                    .fixedInterval(fi -> fi.time(dateHistogramInterval))
                                    .timeZone("UTC"))
                        .aggregations(subAggregations)));

    return SearchRequest.of(
        s ->
            s.index("search-logs-*")
                .size(0)
                .query(Query.of(q -> q.bool(boolQuery)))
                .aggregations(aggregations));
  }

  private SearchStats parseStatsResponse(
      SearchResponse<Void> response, long clickCount, long searchesWithClicks) {
    var aggs = response.aggregations();

    long totalSearches = extractLongValue(aggs, "total_searches", "value");
    long totalDocuments = extractLongValue(aggs, "total_documents", "value");
    long searchFailures = extractLongValue(aggs, "search_failures", "doc_count");
    long errors = extractLongValue(aggs, "errors", "doc_count");
    long successfulSearches = extractLongValue(aggs, "successful_searches", "doc_count");
    double avgResponseTime = extractDoubleValue(aggs, "avg_response_time", "value");

    double zeroHitRate = totalSearches > 0 ? (double) searchFailures / totalSearches * 100 : 0.0;
    double successRate =
        totalSearches > 0 ? (double) successfulSearches / totalSearches * 100 : 0.0;
    // CTR: 클릭이 발생한 검색의 비율 (최대 100%)
    double clickThroughRate =
        totalSearches > 0 ? (double) searchesWithClicks / totalSearches * 100 : 0.0;

    return SearchStats.builder()
        .totalSearchCount(totalSearches)
        .totalDocumentCount(totalDocuments)
        .zeroHitRate(Math.round(zeroHitRate * 100.0) / 100.0)
        .errorCount(errors)
        .averageResponseTimeMs(Math.round(avgResponseTime * 100.0) / 100.0)
        .successRate(Math.round(successRate * 100.0) / 100.0)
        .clickCount(clickCount)
        .clickThroughRate(Math.round(clickThroughRate * 100.0) / 100.0)
        .build();
  }

  private List<KeywordStats> parsePopularKeywordsResponse(
      SearchResponse<Void> response, int limit) {
    var aggs = response.aggregations();

    // 현재 주기 데이터 추출
    var currentPeriodAgg = aggs.get("current_period");
    Map<String, Long> currentPeriodData = new HashMap<>();

    if (currentPeriodAgg != null && currentPeriodAgg.isFilter()) {
      var currentKeywordsAgg = currentPeriodAgg.filter().aggregations().get("keywords");
      if (currentKeywordsAgg != null && currentKeywordsAgg._kind().jsonValue().equals("sterms")) {
        var termsAgg = currentKeywordsAgg.sterms();
        var buckets = termsAgg.buckets().array();

        for (var bucket : buckets) {
          currentPeriodData.put(bucket.key().stringValue(), bucket.docCount());
        }
      }
    }

    // 이전 주기 순위 맵 생성
    Map<String, Integer> previousRanks = new HashMap<>();
    var previousPeriodAgg = aggs.get("previous_period");
    if (previousPeriodAgg != null && previousPeriodAgg.isFilter()) {
      var previousKeywordsAgg = previousPeriodAgg.filter().aggregations().get("keywords");
      if (previousKeywordsAgg != null && previousKeywordsAgg._kind().jsonValue().equals("sterms")) {
        var termsAgg = previousKeywordsAgg.sterms();
        var buckets = termsAgg.buckets().array();

        int prevRank = 1;
        for (var bucket : buckets) {
          previousRanks.put(bucket.key().stringValue(), prevRank++);
        }
      }
    }

    // 결과 생성 (순위 변동 정보 포함)
    List<KeywordStats> result = new ArrayList<>();
    long totalCount = currentPeriodData.values().stream().mapToLong(Long::longValue).sum();
    int rank = 1;

    // 현재 주기 키워드를 count 기준 정렬
    List<Map.Entry<String, Long>> sortedEntries = new ArrayList<>(currentPeriodData.entrySet());
    sortedEntries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

    for (Map.Entry<String, Long> entry : sortedEntries) {
      if (rank > limit) break; // limit 적용

      String keyword = entry.getKey();
      long searchCount = entry.getValue();
      double percentage = totalCount > 0 ? (double) searchCount / totalCount * 100 : 0.0;

      Integer previousRank = previousRanks.get(keyword);
      Integer rankChange = null;
      KeywordStats.RankChangeStatus changeStatus = null;

      if (previousRank == null) {
        changeStatus = KeywordStats.RankChangeStatus.NEW;
      } else {
        rankChange = previousRank - rank;
        if (rankChange > 0) {
          changeStatus = KeywordStats.RankChangeStatus.UP;
        } else if (rankChange < 0) {
          changeStatus = KeywordStats.RankChangeStatus.DOWN;
        } else {
          changeStatus = KeywordStats.RankChangeStatus.SAME;
        }
      }

      result.add(
          KeywordStats.builder()
              .keyword(keyword)
              .searchCount(searchCount)
              .clickCount(0L) // 클릭수는 일단 0으로 (필요시 추가 개선)
              .clickThroughRate(0.0)
              .percentage(Math.round(percentage * 100.0) / 100.0)
              .rank(rank)
              .previousRank(previousRank)
              .rankChange(rankChange)
              .changeStatus(changeStatus)
              .build());

      rank++;
    }

    return result;
  }

  private List<TrendData> parseTrendsResponse(SearchResponse<Void> response, String interval) {
    var aggs = response.aggregations();
    var timeBucketsAgg = aggs.get("time_buckets");

    List<TrendData> trendData = new ArrayList<>();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    if (timeBucketsAgg != null && timeBucketsAgg._kind().jsonValue().equals("date_histogram")) {
      try {
        var dateHistAgg = timeBucketsAgg.dateHistogram();
        var buckets = dateHistAgg.buckets().array();

        for (var bucket : buckets) {
          String keyAsString = bucket.keyAsString();
          if (keyAsString == null) {
            continue;
          }
          LocalDateTime timestamp;
          if (keyAsString.contains("+") || keyAsString.contains("-")) {
            // 시간대 정보가 있으면 LocalDateTime으로 변환
            timestamp = ZonedDateTime.parse(keyAsString).toLocalDateTime();
          } else {
            timestamp = LocalDateTime.parse(keyAsString.substring(0, 19));
          }
          long searchCount = extractLongValue(bucket.aggregations(), "search_count", "value");
          double avgResponseTime =
              extractDoubleValue(bucket.aggregations(), "avg_response_time", "value");

          long errorCount = extractLongValue(bucket.aggregations(), "error_count", "doc_count");

          trendData.add(
              TrendData.builder()
                  .timestamp(timestamp)
                  .searchCount(searchCount)
                  .errorCount(errorCount)
                  .averageResponseTime(Math.round(avgResponseTime * 100.0) / 100.0)
                  .label(timestamp.format(formatter))
                  .build());
        }
      } catch (Exception e) {
        log.warn("시계열 추이 파싱 실패: {}", e.getMessage());
      }
    }

    return trendData;
  }

  private long extractLongValue(Map<String, Aggregate> aggs, String aggName, String field) {
    try {
      var aggResult = aggs.get(aggName);
      if (aggResult == null) return 0L;

      if ("value".equals(field)) {
        if (aggResult.isValueCount()) {
          return (long) aggResult.valueCount().value();
        } else if (aggResult.isSum()) {
          double sumValue = aggResult.sum().value();
          return (long) sumValue;
        }
      } else if ("doc_count".equals(field)) {
        if (aggResult.isFilter()) {
          return aggResult.filter().docCount();
        }
      }
    } catch (Exception e) {
      log.debug("집계 값 추출 실패: {}.{}", aggName, field);
    }
    return 0L;
  }

  private double extractDoubleValue(Map<String, Aggregate> aggs, String aggName, String field) {
    try {
      var aggResult = aggs.get(aggName);
      if (aggResult == null) return 0.0;

      if ("value".equals(field) && aggResult.isAvg()) {
        return aggResult.avg().value();
      }
    } catch (Exception e) {
      log.debug("집계 값 추출 실패: {}.{}", aggName, field);
    }
    return 0.0;
  }
}
