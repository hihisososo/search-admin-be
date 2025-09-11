package com.yjlee.search.searchlog.service;

import static com.yjlee.search.searchlog.dto.PopularKeywordDto.RankChangeStatus.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.searchlog.dto.PopularKeywordDto;
import com.yjlee.search.searchlog.dto.PopularKeywordsResponse;
import com.yjlee.search.searchlog.dto.SearchLogListRequest;
import com.yjlee.search.searchlog.dto.SearchLogListResponse;
import com.yjlee.search.searchlog.dto.SearchLogResponse;
import com.yjlee.search.searchlog.dto.TrendingKeywordDto;
import com.yjlee.search.searchlog.dto.TrendingKeywordsResponse;
import com.yjlee.search.searchlog.model.SearchLogDocument;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
      long periodDays = Duration.between(fromDate, toDate).toDays();
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
          .lastUpdated(LocalDateTime.now(ZoneOffset.UTC))
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
                changeStatus = NEW;
              } else {
                // 순위 변동 계산 (이전 순위 - 현재 순위, 양수면 상승)
                rankChange = previousRank - current.getRank();

                if (rankChange > 0) {
                  changeStatus = UP;
                } else if (rankChange < 0) {
                  changeStatus = DOWN;
                } else {
                  changeStatus = SAME;
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
          .lastUpdated(LocalDateTime.now(ZoneOffset.UTC))
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
                            .filter(Query.of(f -> f.exists(e -> e.field("searchKeyword"))))
                            .filter(
                                Query.of(
                                    f ->
                                        f.range(
                                            r ->
                                                r.date(
                                                    d ->
                                                        d.field("timestamp")
                                                            .gte(fromDate.toString())
                                                            .lte(toDate.toString()))))))));
  }

  private String generateIndexPattern(LocalDateTime fromDate, LocalDateTime toDate) {
    // 날짜가 없으면 모든 인덱스 조회
    if (fromDate == null || toDate == null) {
      return INDEX_PREFIX + "*";
    }

    // 같은 날짜이면 해당 날짜의 인덱스만 조회
    if (fromDate.toLocalDate().equals(toDate.toLocalDate())) {
      return generateIndexName(fromDate);
    }

    // 다른 날짜이면 모든 인덱스 조회 (추후 날짜 범위별 인덱스 패턴 구현 가능)
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

  /** 일반 검색 로그 수집 */
  public void collectSearchLog(
      SearchExecuteRequest request,
      LocalDateTime timestamp,
      String clientIp,
      String userAgent,
      long responseTimeMs,
      SearchExecuteResponse response,
      boolean isError,
      String errorMessage,
      String sessionId) {

    String keyword =
        request.getQuery() != null && !request.getQuery().trim().isEmpty()
            ? request.getQuery().trim()
            : "unknown";

    Long resultCount =
        response != null && response.getHits() != null ? response.getHits().getTotal() : 0L;

    String queryDsl =
        response != null && response.getQueryDsl() != null ? response.getQueryDsl() : null;

    SearchLogDocument searchLog =
        SearchLogDocument.builder()
            .timestamp(timestamp)
            .searchKeyword(keyword)
            .indexName("products")
            .responseTimeMs(responseTimeMs)
            .resultCount(resultCount)
            .queryDsl(queryDsl)
            .clientIp(clientIp)
            .userAgent(userAgent)
            .isError(isError)
            .errorMessage(errorMessage)
            .sessionId(sessionId)
            .build();

    saveSearchLog(searchLog);

    log.debug("검색 로그 수집 완료 - 키워드: {}, 결과수: {}, 세션: {}", keyword, resultCount, sessionId);
  }

  public SearchLogListResponse getSearchLogs(SearchLogListRequest request) {
    int page = Math.max(0, request.getPage() != null ? request.getPage() : 0);
    int size = Math.max(1, Math.min(100, request.getSize() != null ? request.getSize() : 10));

    try {

      String indexPattern = generateIndexPattern(request.getStartDate(), request.getEndDate());

      Query query = buildSearchQuery(request);

      int from = page * size;

      // Elasticsearch from + size 제한 확인 (기본 10,000)
      if (from + size > 10000) {
        log.warn(
            "Elasticsearch from + size 제한 초과: from={}, size={}, 합계={} (최대 10,000)",
            from,
            size,
            from + size);
      }

      String sortParam = request.getSort();
      String resolvedSortField = getSortField(sortParam);
      SortOrder resolvedOrder =
          (request.getOrder() != null && request.getOrder().equalsIgnoreCase("asc"))
              ? SortOrder.Asc
              : SortOrder.Desc; // 기본 내림차순

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(indexPattern)
                      .query(query)
                      .from(from)
                      .size(size)
                      .sort(
                          sortBuilder ->
                              sortBuilder.field(
                                  fieldSort ->
                                      fieldSort.field(resolvedSortField).order(resolvedOrder)))
                      .allowNoIndices(true) // 인덱스가 없어도 에러 안남
                      .ignoreUnavailable(true) // 사용할 수 없는 인덱스 무시
                      .trackTotalHits(t -> t.enabled(true))); // 정확한 총 건수 계산

      SearchResponse<SearchLogDocument> response =
          elasticsearchClient.search(searchRequest, SearchLogDocument.class);

      List<SearchLogResponse> content =
          response.hits().hits().stream().map(this::convertToResponse).collect(Collectors.toList());

      long totalElements = response.hits().total() != null ? response.hits().total().value() : 0;
      int totalPages = (int) Math.ceil((double) totalElements / size);

      // 디버깅 로그
      log.info(
          "검색 로그 조회 결과 - from: {}, size: {}, 실제 조회된 건수: {}, 총 건수: {}, 계산된 총 페이지: {}, 요청 페이지: {}",
          from,
          size,
          content.size(),
          totalElements,
          totalPages,
          page);

      // 요청한 페이지가 총 페이지를 초과하고 데이터가 없는 경우 경고 로그
      if (page > totalPages && content.isEmpty() && totalElements > 0) {
        log.warn("요청한 페이지({})가 총 페이지({})를 초과함. 총 건수: {}", page, totalPages, totalElements);
      }

      return SearchLogListResponse.builder()
          .content(content)
          .totalElements(totalElements)
          .totalPages(totalPages)
          .currentPage(page)
          .size(size)
          .hasNext(page < totalPages - 1)
          .hasPrevious(page > 0)
          .build();

    } catch (Exception e) {
      log.error(
          "검색 로그 조회 실패 - 인덱스 패턴: {}, 페이지: {}, 크기: {}, 키워드: {}",
          generateIndexPattern(request.getStartDate(), request.getEndDate()),
          page,
          size,
          request.getKeyword(),
          e);

      // 빈 응답 반환 (에러 대신)
      return SearchLogListResponse.builder()
          .content(new ArrayList<>())
          .totalElements(0L)
          .totalPages(0)
          .currentPage(page)
          .size(size)
          .hasNext(false)
          .hasPrevious(false)
          .build();
    }
  }

  private Query buildSearchQuery(SearchLogListRequest request) {
    BoolQuery.Builder boolQuery = new BoolQuery.Builder();

    // 키워드 필터
    if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
      boolQuery.filter(
          Query.of(
              q ->
                  q.wildcard(
                      w ->
                          w.field("search_keyword.keyword")
                              .value("*" + request.getKeyword() + "*"))));
    }

    // 인덱스명 필터 제거 (요청 DTO에서 삭제됨)

    // 에러 여부 필터
    if (request.getIsError() != null) {
      boolQuery.filter(Query.of(q -> q.term(t -> t.field("isError").value(request.getIsError()))));
    }

    // IP 필터
    if (request.getClientIp() != null && !request.getClientIp().trim().isEmpty()) {
      boolQuery.filter(
          Query.of(q -> q.term(t -> t.field("clientIp.keyword").value(request.getClientIp()))));
    }

    // 날짜 범위 필터
    if (request.getStartDate() != null || request.getEndDate() != null) {
      boolQuery.filter(
          Query.of(
              q ->
                  q.range(
                      r ->
                          r.date(
                              d -> {
                                d.field("timestamp");
                                if (request.getStartDate() != null) {
                                  d.gte(request.getStartDate().toString());
                                }
                                if (request.getEndDate() != null) {
                                  d.lte(request.getEndDate().toString());
                                }
                                return d;
                              }))));
    }

    // 응답시간 범위 필터
    if (request.getMinResponseTime() != null || request.getMaxResponseTime() != null) {
      boolQuery.filter(
          Query.of(
              q ->
                  q.range(
                      r ->
                          r.number(
                              n -> {
                                n.field("response_time_ms");
                                if (request.getMinResponseTime() != null) {
                                  n.gte(request.getMinResponseTime().doubleValue());
                                }
                                if (request.getMaxResponseTime() != null) {
                                  n.lte(request.getMaxResponseTime().doubleValue());
                                }
                                return n;
                              }))));
    }

    // 결과수 범위 필터
    if (request.getMinResultCount() != null || request.getMaxResultCount() != null) {
      boolQuery.filter(
          Query.of(
              q ->
                  q.range(
                      r ->
                          r.number(
                              n -> {
                                n.field("result_count");
                                if (request.getMinResultCount() != null) {
                                  n.gte(request.getMinResultCount().doubleValue());
                                }
                                if (request.getMaxResultCount() != null) {
                                  n.lte(request.getMaxResultCount().doubleValue());
                                }
                                return n;
                              }))));
    }

    return Query.of(q -> q.bool(boolQuery.build()));
  }

  private SearchLogResponse convertToResponse(Hit<SearchLogDocument> hit) {
    SearchLogDocument doc = hit.source();
    return SearchLogResponse.builder()
        .id(hit.id())
        .timestamp(doc.getTimestamp())
        .searchKeyword(doc.getSearchKeyword())
        .indexName(doc.getIndexName())
        .responseTimeMs(doc.getResponseTimeMs())
        .resultCount(doc.getResultCount())
        .clientIp(doc.getClientIp())
        .userAgent(doc.getUserAgent())
        .isError(doc.getIsError())
        .errorMessage(doc.getErrorMessage())
        .queryDsl(doc.getQueryDsl())
        .build();
  }

  public SearchLogResponse getSearchLogDetail(String logId) {
    log.info("검색 로그 상세 조회 - ID: {}", logId);

    try {
      String indexPattern = INDEX_PREFIX + "*";

      SearchRequest searchRequest =
          SearchRequest.of(
              s -> s.index(indexPattern).query(Query.of(q -> q.ids(i -> i.values(logId)))).size(1));

      SearchResponse<SearchLogDocument> response =
          elasticsearchClient.search(searchRequest, SearchLogDocument.class);

      if (response.hits().hits().isEmpty()) {
        log.warn("검색 로그를 찾을 수 없음 - ID: {}", logId);
        return null;
      }

      return convertToResponse(response.hits().hits().get(0));

    } catch (Exception e) {
      log.error("검색 로그 상세 조회 실패 - ID: {}", logId, e);
      throw new RuntimeException("검색 로그 상세 조회 실패: " + e.getMessage(), e);
    }
  }

  private String getSortField(String sort) {
    if (sort == null || sort.isBlank()) {
      return "timestamp";
    }
    return switch (sort) {
      case "responseTime" -> "response_time_ms";
      case "resultCount" -> "result_count";
      case "searchKeyword" -> "search_keyword.keyword";
      default -> "timestamp";
    };
  }
}
