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
      SearchRequest searchRequest = buildPopularKeywordsSearchRequest(from, to, limit);
      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return parseKeywordStatsResponse(response, from, to);

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
      LocalDateTime from, LocalDateTime to, int limit) {
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
                    .must(Query.of(q -> q.term(t -> t.field("is_error").value(false)))));

    Map<String, Aggregation> aggregations =
        Map.of(
            "keywords",
            Aggregation.of(a -> a.terms(t -> t.field("search_keyword.keyword").size(limit))));

    return SearchRequest.of(
        s ->
            s.index("search-logs-*")
                .size(0)
                .query(Query.of(q -> q.bool(boolQuery)))
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
                                    .timeZone("Asia/Seoul"))
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

  private List<KeywordStats> parseKeywordStatsResponse(
      SearchResponse<Void> response, LocalDateTime from, LocalDateTime to) {
    var aggs = response.aggregations();
    var keywordsAgg = aggs.get("keywords");

    List<KeywordStats> keywords = new ArrayList<>();

    if (keywordsAgg != null && keywordsAgg._kind().jsonValue().equals("sterms")) {
      try {
        var termsAgg = keywordsAgg.sterms();
        var buckets = termsAgg.buckets().array();
        long totalCount = buckets.stream().mapToLong(bucket -> bucket.docCount()).sum();

        for (int i = 0; i < buckets.size(); i++) {
          var bucket = buckets.get(i);
          String keyword = bucket.key().stringValue();
          long searchCount = bucket.docCount();
          long clickCount = getClickCountForKeyword(keyword, from, to);
          long searchesWithClicksForKeyword = getSearchesWithClicksForKeyword(keyword, from, to);
          double percentage = totalCount > 0 ? (double) searchCount / totalCount * 100 : 0.0;
          // CTR: 해당 키워드로 검색 후 클릭한 비율 (최대 100%)
          double ctr =
              searchCount > 0 ? (double) searchesWithClicksForKeyword / searchCount * 100 : 0.0;

          keywords.add(
              KeywordStats.builder()
                  .keyword(keyword)
                  .searchCount(searchCount)
                  .clickCount(clickCount)
                  .clickThroughRate(Math.round(ctr * 100.0) / 100.0)
                  .percentage(Math.round(percentage * 100.0) / 100.0)
                  .rank(i + 1)
                  .build());
        }
      } catch (Exception e) {
        log.warn("키워드 통계 파싱 실패: {}", e.getMessage());
      }
    }

    return keywords;
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
