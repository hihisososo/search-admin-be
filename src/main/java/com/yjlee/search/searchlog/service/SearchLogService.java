package com.yjlee.search.searchlog.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.search.dto.PopularKeywordDto;
import com.yjlee.search.search.dto.PopularKeywordsResponse;
import com.yjlee.search.search.dto.TrendingKeywordDto;
import com.yjlee.search.search.dto.TrendingKeywordsResponse;
import com.yjlee.search.searchlog.model.SearchLogDocument;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 검색 분석을 위한 로그 수집 및 집계 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogService {

  private final ElasticsearchClient elasticsearchClient;
  private final ProductRepository productRepository;

  private static final String INDEX_PREFIX = "search-logs-";
  private static final DateTimeFormatter INDEX_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy.MM.dd");

  /**
   * 검색 로그를 Elasticsearch에 저장
   *
   * @param searchLogDocument 저장할 검색 로그
   */
  public void saveSearchLog(SearchLogDocument searchLogDocument) {
    try {
      String indexName = generateIndexName(searchLogDocument.getTimestamp());

      log.debug(
          "Saving search log to index: {}, keyword: {}",
          indexName,
          searchLogDocument.getSearchKeyword());

      elasticsearchClient.index(
          indexRequest -> indexRequest.index(indexName).document(searchLogDocument));

      log.debug("Search log saved successfully");

    } catch (Exception e) {
      log.error("Failed to save search log: {}", e.getMessage(), e);
    }
  }

  /**
   * 인기 검색어 조회
   *
   * @param fromDate 조회 시작 날짜
   * @param toDate 조회 종료 날짜
   * @param limit 결과 개수 제한
   * @return 인기 검색어 응답
   */
  public PopularKeywordsResponse getPopularKeywords(
      LocalDateTime fromDate, LocalDateTime toDate, int limit) {
    log.info("인기 검색어 조회 요청 - 기간: {} ~ {}, 제한: {}", fromDate, toDate, limit);

    try {
      // 현재 기간의 인기 검색어 조회
      List<PopularKeywordDto> currentKeywords = getPopularKeywordsList(fromDate, toDate, limit);

      // 이전 기간 계산 (동일한 기간만큼 이전)
      long periodDays = java.time.Duration.between(fromDate, toDate).toDays();
      LocalDateTime previousFromDate = fromDate.minusDays(periodDays);
      LocalDateTime previousToDate = fromDate;

      // 이전 기간의 인기 검색어 조회
      List<PopularKeywordDto> previousKeywords =
          getPopularKeywordsList(previousFromDate, previousToDate, limit * 2);

      // 변동폭 계산
      List<PopularKeywordDto> keywordsWithRankChange =
          calculateRankChanges(currentKeywords, previousKeywords);

      log.info("인기 검색어 조회 완료 - 결과 수: {}", keywordsWithRankChange.size());

      return PopularKeywordsResponse.builder()
          .keywords(keywordsWithRankChange)
          .fromDate(fromDate)
          .toDate(toDate)
          .totalCount(keywordsWithRankChange.size())
          .lastUpdated(LocalDateTime.now())
          .build();

    } catch (Exception e) {
      log.error("인기 검색어 조회 실패", e);
      throw new RuntimeException("인기 검색어 조회 실패: " + e.getMessage(), e);
    }
  }

  /** 특정 기간의 인기 검색어 목록 조회 (내부 메서드) */
  private List<PopularKeywordDto> getPopularKeywordsList(
      LocalDateTime fromDate, LocalDateTime toDate, int limit) {
    try {
      String indexPattern = generateIndexPattern(fromDate, toDate);

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(indexPattern)
                      .size(0)
                      .query(buildDateRangeQuery(fromDate, toDate))
                      .aggregations(
                          "popular_keywords",
                          Aggregation.of(
                              a ->
                                  a.terms(
                                      t ->
                                          t.field("searchKeyword.keyword")
                                              .size(limit)
                                              .minDocCount(1)))));

      SearchResponse<JsonNode> response = elasticsearchClient.search(searchRequest, JsonNode.class);

      List<PopularKeywordDto> popularKeywords = new ArrayList<>();
      if (response.aggregations() != null
          && response.aggregations().get("popular_keywords") != null) {
        StringTermsAggregate termsAgg = response.aggregations().get("popular_keywords").sterms();
        int rank = 1;
        for (StringTermsBucket bucket : termsAgg.buckets().array()) {
          popularKeywords.add(
              PopularKeywordDto.builder()
                  .keyword(bucket.key().stringValue())
                  .searchCount(bucket.docCount())
                  .rank(rank++)
                  .build());
        }
      }

      return popularKeywords;
    } catch (Exception e) {
      log.warn("인기 검색어 조회 실패 (기간: {} ~ {}): {}", fromDate, toDate, e.getMessage());
      return new ArrayList<>();
    }
  }

  /** 순위 변동 계산 */
  private List<PopularKeywordDto> calculateRankChanges(
      List<PopularKeywordDto> currentKeywords, List<PopularKeywordDto> previousKeywords) {

    // 이전 기간 키워드 순위 맵 생성
    Map<String, Integer> previousRankMap =
        previousKeywords.stream()
            .collect(
                Collectors.toMap(
                    PopularKeywordDto::getKeyword,
                    PopularKeywordDto::getRank,
                    (existing, replacement) -> existing));

    return currentKeywords.stream()
        .map(
            current -> {
              Integer previousRank = previousRankMap.get(current.getKeyword());
              Integer rankChange = null;
              PopularKeywordDto.RankChangeStatus changeStatus = null;

              if (previousRank == null) {
                // 신규 진입
                changeStatus = PopularKeywordDto.RankChangeStatus.NEW;
              } else {
                // 순위 변동 계산 (이전 순위 - 현재 순위, 양수면 상승)
                rankChange = previousRank - current.getRank();

                if (rankChange > 0) {
                  changeStatus = PopularKeywordDto.RankChangeStatus.UP;
                } else if (rankChange < 0) {
                  changeStatus = PopularKeywordDto.RankChangeStatus.DOWN;
                } else {
                  changeStatus = PopularKeywordDto.RankChangeStatus.SAME;
                }
              }

              return PopularKeywordDto.builder()
                  .keyword(current.getKeyword())
                  .searchCount(current.getSearchCount())
                  .rank(current.getRank())
                  .previousRank(previousRank)
                  .rankChange(rankChange)
                  .changeStatus(changeStatus)
                  .build();
            })
        .collect(Collectors.toList());
  }

  /**
   * 급등 검색어 조회
   *
   * @param currentFromDate 현재 기간 시작
   * @param currentToDate 현재 기간 종료
   * @param previousFromDate 비교 기간 시작
   * @param previousToDate 비교 기간 종료
   * @param limit 결과 개수 제한
   * @return 급등 검색어 응답
   */
  public TrendingKeywordsResponse getTrendingKeywords(
      LocalDateTime currentFromDate,
      LocalDateTime currentToDate,
      LocalDateTime previousFromDate,
      LocalDateTime previousToDate,
      int limit) {

    log.info(
        "급등 검색어 조회 요청 - 현재: {} ~ {}, 이전: {} ~ {}, 제한: {}",
        currentFromDate,
        currentToDate,
        previousFromDate,
        previousToDate,
        limit);

    try {
      Map<String, Long> currentCounts = getKeywordCounts(currentFromDate, currentToDate, limit * 3);
      Map<String, Long> previousCounts =
          getKeywordCounts(previousFromDate, previousToDate, limit * 3);

      List<TrendingKeywordDto> trendingKeywords = new ArrayList<>();

      for (Map.Entry<String, Long> entry : currentCounts.entrySet()) {
        String keyword = entry.getKey();
        Long currentCount = entry.getValue();
        Long previousCount = previousCounts.getOrDefault(keyword, 0L);

        if (currentCount >= 5) {
          double growthRate =
              previousCount == 0
                  ? currentCount * 100.0
                  : ((double) (currentCount - previousCount) / previousCount) * 100.0;

          if (growthRate > 50.0) {
            trendingKeywords.add(
                TrendingKeywordDto.builder()
                    .keyword(keyword)
                    .currentCount(currentCount)
                    .previousCount(previousCount)
                    .growthRate(Math.round(growthRate * 10.0) / 10.0)
                    .build());
          }
        }
      }

      trendingKeywords.sort((a, b) -> b.getGrowthRate().compareTo(a.getGrowthRate()));

      int rank = 1;
      for (int i = 0; i < Math.min(limit, trendingKeywords.size()); i++) {
        TrendingKeywordDto keyword = trendingKeywords.get(i);
        trendingKeywords.set(
            i,
            TrendingKeywordDto.builder()
                .keyword(keyword.getKeyword())
                .currentCount(keyword.getCurrentCount())
                .previousCount(keyword.getPreviousCount())
                .growthRate(keyword.getGrowthRate())
                .rank(rank++)
                .build());
      }

      List<TrendingKeywordDto> finalResult =
          trendingKeywords.subList(0, Math.min(limit, trendingKeywords.size()));

      log.info("급등 검색어 조회 완료 - 결과 수: {}", finalResult.size());

      return TrendingKeywordsResponse.builder()
          .keywords(finalResult)
          .currentFromDate(currentFromDate)
          .currentToDate(currentToDate)
          .previousFromDate(previousFromDate)
          .previousToDate(previousToDate)
          .totalCount(finalResult.size())
          .lastUpdated(LocalDateTime.now())
          .build();

    } catch (Exception e) {
      log.error("급등 검색어 조회 실패", e);
      throw new RuntimeException("급등 검색어 조회 실패: " + e.getMessage(), e);
    }
  }

  private Map<String, Long> getKeywordCounts(
      LocalDateTime fromDate, LocalDateTime toDate, int limit) {
    try {
      String indexPattern = generateIndexPattern(fromDate, toDate);

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(indexPattern)
                      .size(0)
                      .query(buildDateRangeQuery(fromDate, toDate))
                      .aggregations(
                          "keyword_counts",
                          Aggregation.of(
                              a ->
                                  a.terms(
                                      t ->
                                          t.field("searchKeyword.keyword")
                                              .size(limit)
                                              .minDocCount(1)))));

      SearchResponse<JsonNode> response = elasticsearchClient.search(searchRequest, JsonNode.class);

      Map<String, Long> keywordCounts = new HashMap<>();
      if (response.aggregations() != null
          && response.aggregations().get("keyword_counts") != null) {
        StringTermsAggregate termsAgg = response.aggregations().get("keyword_counts").sterms();
        for (StringTermsBucket bucket : termsAgg.buckets().array()) {
          keywordCounts.put(bucket.key().stringValue(), bucket.docCount());
        }
      }

      return keywordCounts;
    } catch (Exception e) {
      log.error("키워드 카운트 조회 실패", e);
      return new HashMap<>();
    }
  }

  private Query buildDateRangeQuery(LocalDateTime fromDate, LocalDateTime toDate) {
    return Query.of(
        q ->
            q.bool(
                BoolQuery.of(
                    b ->
                        b.filter(Query.of(f -> f.term(t -> t.field("isError").value(false))))
                            .filter(Query.of(f -> f.exists(e -> e.field("searchKeyword")))))));
  }

  private String generateIndexPattern(LocalDateTime fromDate, LocalDateTime toDate) {
    if (fromDate.toLocalDate().equals(toDate.toLocalDate())) {
      return generateIndexName(fromDate);
    }
    return INDEX_PREFIX + "*";
  }

  /**
   * 로그 인덱스명 생성 패턴: search-logs-yyyy.MM.dd
   *
   * @param timestamp 로그 생성 시간
   * @return 생성된 인덱스명
   */
  private String generateIndexName(LocalDateTime timestamp) {
    return INDEX_PREFIX + timestamp.format(INDEX_DATE_FORMAT);
  }

  /**
   * 샘플 검색 로그 데이터 생성
   *
   * @param count 생성할 로그 개수
   * @return 생성된 로그 개수
   */
  public int generateSampleLogs(int count) {
    log.info("샘플 검색 로그 생성 시작 - 요청 개수: {}", count);

    try {
      List<Product> products = productRepository.findAll();
      if (products.isEmpty()) {
        log.warn("상품 데이터가 없어 샘플 로그를 생성할 수 없습니다.");
        return 0;
      }

      List<String> keywords = generateSearchKeywords(products);
      if (keywords.isEmpty()) {
        log.warn("생성할 키워드가 없습니다.");
        return 0;
      }

      LocalDateTime now = LocalDateTime.now();
      String[] userAgents = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X) AppleWebKit/605.1.15",
        "Mozilla/5.0 (Android 11; Mobile; rv:68.0) Gecko/68.0 Firefox/88.0"
      };

      String[] sampleIps = {
        "192.168.1.100", "10.0.0.15", "172.16.0.50", "203.241.132.54", "121.78.45.123"
      };

      int generated = 0;
      for (int i = 0; i < count; i++) {
        String keyword = keywords.get(ThreadLocalRandom.current().nextInt(keywords.size()));

        SearchLogDocument logDocument =
            SearchLogDocument.builder()
                .timestamp(
                    now.minusMinutes(ThreadLocalRandom.current().nextInt(1440))) // 최근 24시간 내 랜덤
                .searchKeyword(keyword)
                .indexName("products")
                .responseTimeMs((long) ThreadLocalRandom.current().nextInt(10, 500))
                .resultCount((long) ThreadLocalRandom.current().nextInt(0, 1000))
                .queryDsl(
                    "{\"query\":{\"multi_match\":{\"query\":\""
                        + keyword
                        + "\",\"fields\":[\"name\",\"description\"]}}}")
                .clientIp(sampleIps[ThreadLocalRandom.current().nextInt(sampleIps.length)])
                .userAgent(userAgents[ThreadLocalRandom.current().nextInt(userAgents.length)])
                .isError(ThreadLocalRandom.current().nextDouble() < 0.05) // 5% 에러율
                .errorMessage(null)
                .build();

        saveSearchLog(logDocument);
        generated++;

        if (generated % 50 == 0) {
          log.info("샘플 로그 생성 진행: {}/{}", generated, count);
        }
      }

      log.info("샘플 검색 로그 생성 완료: {} 건", generated);
      return generated;

    } catch (Exception e) {
      log.error("샘플 검색 로그 생성 실패", e);
      throw new RuntimeException("샘플 검색 로그 생성 실패: " + e.getMessage(), e);
    }
  }

  /** 상품 데이터에서 검색 키워드 생성 */
  private List<String> generateSearchKeywords(List<Product> products) {
    Set<String> keywordSet = new HashSet<>();

    for (Product product : products) {
      if (product.getName() != null && !product.getName().isBlank()) {
        String name = product.getName().trim();

        // 전체 상품명
        keywordSet.add(name);

        // 첫 번째 단어 (브랜드)
        String[] words = name.split("\\s+");
        if (words.length > 0 && words[0].length() >= 2) {
          keywordSet.add(words[0]);
        }

        // 부분 문자열 (2글자 이상)
        for (String word : words) {
          if (word.length() >= 2) {
            keywordSet.add(word);

            // 단어의 부분 문자열
            if (word.length() >= 4) {
              for (int i = 0; i <= word.length() - 3; i++) {
                String substring = word.substring(i, i + 3);
                if (substring.matches(".*[가-힣a-zA-Z].*")) {
                  keywordSet.add(substring);
                }
              }
            }
          }
        }

        // 카테고리명
        if (product.getCategoryName() != null && !product.getCategoryName().isBlank()) {
          keywordSet.add(product.getCategoryName());
        }
      }
    }

    // 너무 짧거나 숫자만 있는 키워드 제거
    return keywordSet.stream()
        .filter(keyword -> keyword.length() >= 2)
        .filter(keyword -> keyword.matches(".*[가-힣a-zA-Z].*"))
        .sorted()
        .toList();
  }
}
