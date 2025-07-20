package com.yjlee.search.stats.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.dto.TrendResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

  private final ElasticsearchClient elasticsearchClient;

  public StatsResponse getStats(LocalDateTime from, LocalDateTime to) {
    try {
      SearchRequest searchRequest = buildStatsSearchRequest(from, to);
      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return parseStatsResponse(response, from, to);

    } catch (Exception e) {
      log.error("대시보드 통계 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("대시보드 통계 조회 실패", e);
    }
  }

  public PopularKeywordResponse getPopularKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    try {
      // 현재 기간의 인기 검색어 조회
      PopularKeywordResponse currentKeywords = getPopularKeywordsBase(from, to, limit);

      // 이전 기간 계산 (동일한 기간만큼 이전)
      long periodDays = java.time.Duration.between(from, to).toDays();
      LocalDateTime previousFrom = from.minusDays(periodDays);
      LocalDateTime previousTo = from;

      // 이전 기간의 인기 검색어 조회
      PopularKeywordResponse previousKeywords =
          getPopularKeywordsBase(previousFrom, previousTo, limit * 2);

      // 변동폭 계산
      List<PopularKeywordResponse.KeywordStats> keywordsWithRankChange =
          calculateRankChangesForStats(
              currentKeywords.getKeywords(), previousKeywords.getKeywords());

      return PopularKeywordResponse.builder()
          .keywords(keywordsWithRankChange)
          .period(currentKeywords.getPeriod())
          .build();

    } catch (Exception e) {
      log.error("인기검색어 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("인기검색어 조회 실패", e);
    }
  }

  /** 기본 인기 검색어 조회 (변동폭 계산 없이) */
  private PopularKeywordResponse getPopularKeywordsBase(
      LocalDateTime from, LocalDateTime to, int limit) {
    try {
      SearchRequest searchRequest = buildPopularKeywordsSearchRequest(from, to, limit);
      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return parsePopularKeywordsResponse(response, from, to);
    } catch (Exception e) {
      log.warn("기본 인기검색어 조회 실패 (기간: {} ~ {}): {}", from, to, e.getMessage());
      return PopularKeywordResponse.builder()
          .keywords(new ArrayList<>())
          .period(from.toLocalDate() + " ~ " + to.toLocalDate())
          .build();
    }
  }

  /** Stats용 순위 변동 계산 */
  private List<PopularKeywordResponse.KeywordStats> calculateRankChangesForStats(
      List<PopularKeywordResponse.KeywordStats> currentKeywords,
      List<PopularKeywordResponse.KeywordStats> previousKeywords) {

    // 이전 기간 키워드 순위 맵 생성
    Map<String, Integer> previousRankMap =
        previousKeywords.stream()
            .collect(
                Collectors.toMap(
                    PopularKeywordResponse.KeywordStats::getKeyword,
                    PopularKeywordResponse.KeywordStats::getRank,
                    (existing, replacement) -> existing));

    return currentKeywords.stream()
        .map(
            current -> {
              Integer previousRank = previousRankMap.get(current.getKeyword());
              Integer rankChange = null;
              PopularKeywordResponse.RankChangeStatus changeStatus = null;

              if (previousRank == null) {
                // 신규 진입
                changeStatus = PopularKeywordResponse.RankChangeStatus.NEW;
              } else {
                // 순위 변동 계산 (이전 순위 - 현재 순위, 양수면 상승)
                rankChange = previousRank - current.getRank();

                if (rankChange > 0) {
                  changeStatus = PopularKeywordResponse.RankChangeStatus.UP;
                } else if (rankChange < 0) {
                  changeStatus = PopularKeywordResponse.RankChangeStatus.DOWN;
                } else {
                  changeStatus = PopularKeywordResponse.RankChangeStatus.SAME;
                }
              }

              return PopularKeywordResponse.KeywordStats.builder()
                  .keyword(current.getKeyword())
                  .count(current.getCount())
                  .percentage(current.getPercentage())
                  .rank(current.getRank())
                  .previousRank(previousRank)
                  .rankChange(rankChange)
                  .changeStatus(changeStatus)
                  .build();
            })
        .collect(Collectors.toList());
  }

  public TrendResponse getTrends(LocalDateTime from, LocalDateTime to, String interval) {
    try {
      SearchRequest searchRequest = buildTrendsSearchRequest(from, to, interval);
      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return parseTrendsResponse(response, from, to, interval);

    } catch (Exception e) {
      log.error("추이 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("추이 조회 실패", e);
    }
  }

  public PopularKeywordResponse getTrendingKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    try {
      LocalDateTime previousFrom =
          from.minusDays(to.toLocalDate().toEpochDay() - from.toLocalDate().toEpochDay());
      LocalDateTime previousTo = from;

      PopularKeywordResponse currentKeywords = getPopularKeywords(from, to, limit * 2);
      PopularKeywordResponse previousKeywords =
          getPopularKeywords(previousFrom, previousTo, limit * 2);

      // 이전 기간의 순위를 Map으로 저장
      Map<String, Integer> previousRanks =
          previousKeywords.getKeywords().stream()
              .collect(
                  Collectors.toMap(
                      PopularKeywordResponse.KeywordStats::getKeyword,
                      PopularKeywordResponse.KeywordStats::getRank));

      // 이전 기간 마지막 순위 계산 (새로운 키워드의 기준 순위)
      int lastPreviousRank =
          previousKeywords.getKeywords().isEmpty()
              ? limit * 2 + 1
              : previousKeywords.getKeywords().size() + 1;

      List<PopularKeywordResponse.KeywordStats> trendingKeywords =
          currentKeywords.getKeywords().stream()
              .map(
                  current -> {
                    // 이전 순위 - 현재 순위 = 순위 변동량 (양수가 상승)
                    int previousRank =
                        previousRanks.getOrDefault(current.getKeyword(), lastPreviousRank);
                    int rankChange = previousRank - current.getRank();

                    return new RankChangeKeyword(current, rankChange);
                  })
              .filter(item -> item.getRankChange() > 0) // 순위가 상승한 키워드만
              .sorted((a, b) -> Integer.compare(b.getRankChange(), a.getRankChange())) // 순위 변동량 큰 순
              .limit(limit)
              .map(
                  item ->
                      PopularKeywordResponse.KeywordStats.builder()
                          .keyword(item.getKeywordStats().getKeyword())
                          .count(item.getKeywordStats().getCount())
                          .percentage(item.getKeywordStats().getPercentage())
                          .rank(item.getKeywordStats().getRank())
                          .build())
              .collect(Collectors.toList());

      return PopularKeywordResponse.builder()
          .keywords(trendingKeywords)
          .period(from.toLocalDate() + " ~ " + to.toLocalDate())
          .build();

    } catch (Exception e) {
      log.error("급등검색어 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("급등검색어 조회 실패", e);
    }
  }

  // 순위 변동 정보를 담는 내부 클래스
  private static class RankChangeKeyword {
    private final PopularKeywordResponse.KeywordStats keywordStats;
    private final int rankChange;

    public RankChangeKeyword(PopularKeywordResponse.KeywordStats keywordStats, int rankChange) {
      this.keywordStats = keywordStats;
      this.rankChange = rankChange;
    }

    public PopularKeywordResponse.KeywordStats getKeywordStats() {
      return keywordStats;
    }

    public int getRankChange() {
      return rankChange;
    }
  }

  private SearchRequest buildStatsSearchRequest(LocalDateTime from, LocalDateTime to) {
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
                                                .lte(to.toString()))))));

    Map<String, Aggregation> aggregations =
        Map.of(
            "total_searches", Aggregation.of(a -> a.valueCount(v -> v.field("timestamp"))),
            "total_documents", Aggregation.of(a -> a.sum(s -> s.field("resultCount"))),
            "search_failures",
                Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("resultCount").value(0)))),
            "errors",
                Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("isError").value(true)))),
            "successful_searches",
                Aggregation.of(a -> a.filter(f -> f.term(t -> t.field("isError").value(false)))),
            "avg_response_time", Aggregation.of(a -> a.avg(avg -> avg.field("responseTimeMs"))));

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
                    .must(Query.of(q -> q.term(t -> t.field("isError").value(false)))));

    Map<String, Aggregation> aggregations =
        Map.of(
            "keywords",
            Aggregation.of(a -> a.terms(t -> t.field("searchKeyword.keyword").size(limit))));

    return SearchRequest.of(
        s ->
            s.index("search-logs-*")
                .size(0)
                .query(Query.of(q -> q.bool(boolQuery)))
                .aggregations(aggregations));
  }

  private SearchRequest buildTrendsSearchRequest(
      LocalDateTime from, LocalDateTime to, String interval) {
    String dateHistogramInterval = "hour".equals(interval) ? "1h" : "1d";

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
                                                .lte(to.toString()))))));

    Map<String, Aggregation> subAggregations =
        Map.of(
            "search_count", Aggregation.of(a -> a.valueCount(v -> v.field("timestamp"))),
            "avg_response_time", Aggregation.of(a -> a.avg(avg -> avg.field("responseTimeMs"))));

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

  private StatsResponse parseStatsResponse(
      SearchResponse<Void> response, LocalDateTime from, LocalDateTime to) {

    var aggs = response.aggregations();

    long totalSearches = extractLongValue(aggs, "total_searches", "value");
    long totalDocuments = extractLongValue(aggs, "total_documents", "value");
    long searchFailures = extractLongValue(aggs, "search_failures", "doc_count");
    long errors = extractLongValue(aggs, "errors", "doc_count");
    long successfulSearches = extractLongValue(aggs, "successful_searches", "doc_count");
    double avgResponseTime = extractDoubleValue(aggs, "avg_response_time", "value");

    double searchFailureRate =
        totalSearches > 0 ? (double) searchFailures / totalSearches * 100 : 0.0;
    double successRate =
        totalSearches > 0 ? (double) successfulSearches / totalSearches * 100 : 0.0;

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return StatsResponse.builder()
        .totalSearchCount(totalSearches)
        .totalDocumentCount(totalDocuments)
        .searchFailureRate(Math.round(searchFailureRate * 100.0) / 100.0)
        .errorCount(errors)
        .averageResponseTimeMs(Math.round(avgResponseTime * 100.0) / 100.0)
        .successRate(Math.round(successRate * 100.0) / 100.0)
        .period(period)
        .build();
  }

  private PopularKeywordResponse parsePopularKeywordsResponse(
      SearchResponse<Void> response, LocalDateTime from, LocalDateTime to) {
    var aggs = response.aggregations();
    var keywordsAgg = aggs.get("keywords");

    List<PopularKeywordResponse.KeywordStats> keywords = new ArrayList<>();
    long totalCount = 0;

    if (keywordsAgg != null && keywordsAgg._kind().jsonValue().equals("sterms")) {
      try {
        var termsAgg = keywordsAgg.sterms();
        var buckets = termsAgg.buckets().array();
        totalCount = buckets.stream().mapToLong(bucket -> bucket.docCount()).sum();

        for (int i = 0; i < buckets.size(); i++) {
          var bucket = buckets.get(i);
          long count = bucket.docCount();
          double percentage = totalCount > 0 ? (double) count / totalCount * 100 : 0.0;

          keywords.add(
              PopularKeywordResponse.KeywordStats.builder()
                  .keyword(bucket.key().stringValue())
                  .count(count)
                  .percentage(Math.round(percentage * 100.0) / 100.0)
                  .rank(i + 1)
                  .build());
        }
      } catch (Exception e) {
        log.warn("인기검색어 집계 결과 파싱 실패: {}", e.getMessage());
      }
    }

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return PopularKeywordResponse.builder().keywords(keywords).period(period).build();
  }

  private TrendResponse parseTrendsResponse(
      SearchResponse<Void> response, LocalDateTime from, LocalDateTime to, String interval) {
    var aggs = response.aggregations();
    var timeBucketsAgg = aggs.get("time_buckets");

    List<TrendResponse.TrendData> trendData = new ArrayList<>();
    DateTimeFormatter formatter =
        "hour".equals(interval)
            ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")
            : DateTimeFormatter.ofPattern("yyyy-MM-dd");

    if (timeBucketsAgg != null && timeBucketsAgg._kind().jsonValue().equals("date_histogram")) {
      try {
        var dateHistAgg = timeBucketsAgg.dateHistogram();
        var buckets = dateHistAgg.buckets().array();

        for (var bucket : buckets) {
          LocalDateTime timestamp = LocalDateTime.parse(bucket.keyAsString().substring(0, 19));
          long searchCount = extractLongValue(bucket.aggregations(), "search_count", "value");
          double avgResponseTime =
              extractDoubleValue(bucket.aggregations(), "avg_response_time", "value");

          trendData.add(
              TrendResponse.TrendData.builder()
                  .timestamp(timestamp)
                  .searchCount(searchCount)
                  .averageResponseTime(Math.round(avgResponseTime * 100.0) / 100.0)
                  .label(timestamp.format(formatter))
                  .build());
        }
      } catch (Exception e) {
        log.warn("시계열 추이 집계 결과 파싱 실패: {}", e.getMessage());
      }
    }

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return TrendResponse.builder()
        .searchVolumeData(trendData)
        .responseTimeData(trendData)
        .period(period)
        .interval(interval)
        .build();
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
