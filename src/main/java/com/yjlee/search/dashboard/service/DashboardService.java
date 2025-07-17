package com.yjlee.search.dashboard.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.dashboard.dto.DashboardStatsResponse;
import com.yjlee.search.dashboard.dto.IndexDistributionResponse;
import com.yjlee.search.dashboard.dto.IndexDistributionResponse.IndexStats;
import com.yjlee.search.dashboard.dto.PopularKeywordResponse;
import com.yjlee.search.dashboard.dto.TrendResponse;
import java.io.StringReader;
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
public class DashboardService {

  private final ElasticsearchClient elasticsearchClient;

  public DashboardStatsResponse getStats(LocalDateTime from, LocalDateTime to) {
    try {
      String query = buildStatsQuery(from, to);
      log.info("query: {}", query);
      SearchRequest searchRequest =
          SearchRequest.of(s -> s.index("search-logs-*").size(0).withJson(new StringReader(query)));

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
      String query = buildPopularKeywordsQuery(from, to, limit);

      SearchRequest searchRequest =
          SearchRequest.of(s -> s.index("search-logs-*").size(0).withJson(new StringReader(query)));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      return parsePopularKeywordsResponse(response, from, to);

    } catch (Exception e) {
      log.error("인기검색어 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("인기검색어 조회 실패", e);
    }
  }

  public TrendResponse getTrends(LocalDateTime from, LocalDateTime to, String interval) {
    try {
      String query = buildTrendsQuery(from, to, interval);

      SearchRequest searchRequest =
          SearchRequest.of(s -> s.index("search-logs-*").size(0).withJson(new StringReader(query)));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      return parseTrendsResponse(response, from, to, interval);

    } catch (Exception e) {
      log.error("추이 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("추이 조회 실패", e);
    }
  }

  public IndexDistributionResponse getIndexDistribution(LocalDateTime from, LocalDateTime to) {
    try {
      String query = buildIndexDistributionQuery(from, to);

      SearchRequest searchRequest =
          SearchRequest.of(s -> s.index("search-logs-*").size(0).withJson(new StringReader(query)));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

      return parseIndexDistributionResponse(response, from, to);

    } catch (Exception e) {
      log.error("인덱스별 분포 조회 실패: {}", e.getMessage(), e);
      throw new RuntimeException("인덱스별 분포 조회 실패", e);
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

      Map<String, Long> previousCounts =
          previousKeywords.getKeywords().stream()
              .collect(
                  Collectors.toMap(
                      PopularKeywordResponse.KeywordStats::getKeyword,
                      PopularKeywordResponse.KeywordStats::getCount));

      List<PopularKeywordResponse.KeywordStats> trendingKeywords =
          currentKeywords.getKeywords().stream()
              .filter(
                  current -> {
                    long previousCount = previousCounts.getOrDefault(current.getKeyword(), 0L);
                    double growthRate =
                        previousCount > 0
                            ? ((double) (current.getCount() - previousCount) / previousCount) * 100
                            : Double.MAX_VALUE;
                    return growthRate >= 50.0;
                  })
              .sorted(
                  (a, b) -> {
                    long prevA = previousCounts.getOrDefault(a.getKeyword(), 0L);
                    long prevB = previousCounts.getOrDefault(b.getKeyword(), 0L);
                    double growthA =
                        prevA > 0
                            ? ((double) (a.getCount() - prevA) / prevA) * 100
                            : Double.MAX_VALUE;
                    double growthB =
                        prevB > 0
                            ? ((double) (b.getCount() - prevB) / prevB) * 100
                            : Double.MAX_VALUE;
                    return Double.compare(growthB, growthA);
                  })
              .limit(limit)
              .map(
                  keyword ->
                      PopularKeywordResponse.KeywordStats.builder()
                          .keyword(keyword.getKeyword())
                          .count(keyword.getCount())
                          .percentage(keyword.getPercentage())
                          .rank(keyword.getRank())
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

  private String buildStatsQuery(LocalDateTime from, LocalDateTime to) {
    StringBuilder query = new StringBuilder();
    query.append("{\n");
    query.append("  \"query\": {\n");
    query.append("    \"bool\": {\n");
    query.append("      \"must\": [\n");
    query.append("        {\n");
    query.append("          \"range\": {\n");
    query.append("            \"timestamp\": {\n");
    query.append("              \"gte\": \"").append(from).append("\",\n");
    query.append("              \"lte\": \"").append(to).append("\"\n");
    query.append("            }\n");
    query.append("          }\n");
    query.append("        }");

    query.append("\n");
    query.append("      ]\n");
    query.append("    }\n");
    query.append("  },\n");
    query.append("  \"aggs\": {\n");
    query.append("    \"total_searches\": {\n");
    query.append("      \"value_count\": {\n");
    query.append("        \"field\": \"timestamp\"\n");
    query.append("      }\n");
    query.append("    },\n");
    query.append("    \"total_documents\": {\n");
    query.append("      \"sum\": {\n");
    query.append("        \"field\": \"resultCount\"\n");
    query.append("      }\n");
    query.append("    },\n");
    query.append("    \"search_failures\": {\n");
    query.append("      \"filter\": {\n");
    query.append("        \"term\": {\n");
    query.append("          \"resultCount\": 0\n");
    query.append("        }\n");
    query.append("      }\n");
    query.append("    },\n");
    query.append("    \"errors\": {\n");
    query.append("      \"filter\": {\n");
    query.append("        \"term\": {\n");
    query.append("          \"isError\": true\n");
    query.append("        }\n");
    query.append("      }\n");
    query.append("    },\n");
    query.append("    \"successful_searches\": {\n");
    query.append("      \"filter\": {\n");
    query.append("        \"term\": {\n");
    query.append("          \"isError\": false\n");
    query.append("        }\n");
    query.append("      }\n");
    query.append("    },\n");
    query.append("    \"avg_response_time\": {\n");
    query.append("      \"avg\": {\n");
    query.append("        \"field\": \"responseTimeMs\"\n");
    query.append("      }\n");
    query.append("    }\n");
    query.append("  }\n");
    query.append("}\n");

    return query.toString();
  }

  private String buildPopularKeywordsQuery(LocalDateTime from, LocalDateTime to, int limit) {
    StringBuilder query = new StringBuilder();
    query.append("{\n");
    query.append("  \"query\": {\n");
    query.append("    \"bool\": {\n");
    query.append("      \"must\": [\n");
    query.append("        {\n");
    query.append("          \"range\": {\n");
    query.append("            \"timestamp\": {\n");
    query.append("              \"gte\": \"").append(from).append("\",\n");
    query.append("              \"lte\": \"").append(to).append("\"\n");
    query.append("            }\n");
    query.append("          }\n");
    query.append("        }");

    query.append(",\n");
    query.append("        {\n");
    query.append("          \"term\": {\n");
    query.append("            \"isError\": false\n");
    query.append("          }\n");
    query.append("        }\n");
    query.append("      ]\n");
    query.append("    }\n");
    query.append("  },\n");
    query.append("  \"aggs\": {\n");
    query.append("    \"keywords\": {\n");
    query.append("      \"terms\": {\n");
    query.append("        \"field\": \"searchKeyword.keyword\",\n");
    query.append("        \"size\": ").append(limit).append("\n");
    query.append("      }\n");
    query.append("    }\n");
    query.append("  }\n");
    query.append("}\n");

    return query.toString();
  }

  private String buildTrendsQuery(LocalDateTime from, LocalDateTime to, String interval) {
    String dateHistogramInterval = "hour".equals(interval) ? "1h" : "1d";

    StringBuilder query = new StringBuilder();
    query.append("{\n");
    query.append("  \"query\": {\n");
    query.append("    \"bool\": {\n");
    query.append("      \"must\": [\n");
    query.append("        {\n");
    query.append("          \"range\": {\n");
    query.append("            \"timestamp\": {\n");
    query.append("              \"gte\": \"").append(from).append("\",\n");
    query.append("              \"lte\": \"").append(to).append("\"\n");
    query.append("            }\n");
    query.append("          }\n");
    query.append("        }");

    query.append("\n");
    query.append("      ]\n");
    query.append("    }\n");
    query.append("  },\n");
    query.append("  \"aggs\": {\n");
    query.append("    \"time_buckets\": {\n");
    query.append("      \"date_histogram\": {\n");
    query.append("        \"field\": \"timestamp\",\n");
    query.append("        \"fixed_interval\": \"").append(dateHistogramInterval).append("\",\n");
    query.append("        \"time_zone\": \"Asia/Seoul\"\n");
    query.append("      },\n");
    query.append("      \"aggs\": {\n");
    query.append("        \"search_count\": {\n");
    query.append("          \"value_count\": {\n");
    query.append("            \"field\": \"timestamp\"\n");
    query.append("          }\n");
    query.append("        },\n");
    query.append("        \"avg_response_time\": {\n");
    query.append("          \"avg\": {\n");
    query.append("            \"field\": \"responseTimeMs\"\n");
    query.append("          }\n");
    query.append("        }\n");
    query.append("      }\n");
    query.append("    }\n");
    query.append("  }\n");
    query.append("}\n");

    return query.toString();
  }

  private String buildIndexDistributionQuery(LocalDateTime from, LocalDateTime to) {
    StringBuilder query = new StringBuilder();
    query.append("{\n");
    query.append("  \"query\": {\n");
    query.append("    \"range\": {\n");
    query.append("      \"timestamp\": {\n");
    query.append("        \"gte\": \"").append(from).append("\",\n");
    query.append("        \"lte\": \"").append(to).append("\"\n");
    query.append("      }\n");
    query.append("    }\n");
    query.append("  },\n");
    query.append("  \"aggs\": {\n");
    query.append("    \"total_searches\": {\n");
    query.append("      \"value_count\": {\n");
    query.append("        \"field\": \"timestamp\"\n");
    query.append("      }\n");
    query.append("    },\n");
    query.append("    \"indices\": {\n");
    query.append("      \"terms\": {\n");
    query.append("        \"field\": \"indexName.keyword\",\n");
    query.append("        \"size\": 100\n");
    query.append("      },\n");
    query.append("      \"aggs\": {\n");
    query.append("        \"avg_response_time\": {\n");
    query.append("          \"avg\": {\n");
    query.append("            \"field\": \"responseTimeMs\"\n");
    query.append("          }\n");
    query.append("        },\n");
    query.append("        \"errors\": {\n");
    query.append("          \"filter\": {\n");
    query.append("            \"term\": {\n");
    query.append("              \"isError\": true\n");
    query.append("            }\n");
    query.append("          }\n");
    query.append("        },\n");
    query.append("        \"successful_searches\": {\n");
    query.append("          \"filter\": {\n");
    query.append("            \"term\": {\n");
    query.append("              \"isError\": false\n");
    query.append("            }\n");
    query.append("          }\n");
    query.append("        }\n");
    query.append("      }\n");
    query.append("    }\n");
    query.append("  }\n");
    query.append("}\n");

    return query.toString();
  }

  private DashboardStatsResponse parseStatsResponse(
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

    return DashboardStatsResponse.builder()
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

  private IndexDistributionResponse parseIndexDistributionResponse(
      SearchResponse<Void> response, LocalDateTime from, LocalDateTime to) {
    var aggs = response.aggregations();
    long totalSearches = extractLongValue(aggs, "total_searches", "value");
    var indicesAgg = aggs.get("indices");

    List<IndexStats> indices = new ArrayList<>();

    if (indicesAgg != null && indicesAgg._kind().jsonValue().equals("sterms")) {
      try {
        var termsAgg = indicesAgg.sterms();
        var buckets = termsAgg.buckets().array();

        for (var bucket : buckets) {
          long searchCount = bucket.docCount();
          double percentage = totalSearches > 0 ? (double) searchCount / totalSearches * 100 : 0.0;
          double avgResponseTime =
              extractDoubleValue(bucket.aggregations(), "avg_response_time", "value");
          long errorCount = extractLongValue(bucket.aggregations(), "errors", "doc_count");
          long successfulSearches =
              extractLongValue(bucket.aggregations(), "successful_searches", "doc_count");
          double successRate =
              searchCount > 0 ? (double) successfulSearches / searchCount * 100 : 0.0;

          indices.add(
              IndexDistributionResponse.IndexStats.builder()
                  .searchCount(searchCount)
                  .percentage(Math.round(percentage * 100.0) / 100.0)
                  .averageResponseTime(Math.round(avgResponseTime * 100.0) / 100.0)
                  .errorCount(errorCount)
                  .successRate(Math.round(successRate * 100.0) / 100.0)
                  .build());
        }
      } catch (Exception e) {
        log.warn("인덱스별 분포 집계 결과 파싱 실패: {}", e.getMessage());
      }
    }

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return IndexDistributionResponse.builder()
        .indices(indices)
        .period(period)
        .totalSearchCount(totalSearches)
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
