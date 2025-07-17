package com.yjlee.search.searchlog.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.searchlog.model.SearchLogDocument;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 검색 분석을 위한 로그 수집 및 집계 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogService {

  private final ElasticsearchClient elasticsearchClient;

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
   * 로그 인덱스명 생성 패턴: search-logs-yyyy.MM.dd
   *
   * @param timestamp 로그 생성 시간
   * @return 생성된 인덱스명
   */
  private String generateIndexName(LocalDateTime timestamp) {
    return INDEX_PREFIX + timestamp.format(INDEX_DATE_FORMAT);
  }
}
