package com.yjlee.search.clicklog.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import com.yjlee.search.clicklog.model.ClickLogDocument;
import com.yjlee.search.common.constants.IndexNameConstants;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickLogService {

  private final ElasticsearchClient elasticsearchClient;
  private static final DateTimeFormatter INDEX_DATE_FORMATTER =
      DateTimeFormatter.ofPattern(IndexNameConstants.INDEX_DATE_FORMAT);

  public ClickLogResponse logClick(ClickLogRequest request) {
    try {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      String indexName = IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);

      ClickLogDocument document =
          ClickLogDocument.builder()
              .timestamp(now)
              .searchKeyword(request.getSearchKeyword())
              .clickedProductId(request.getClickedProductId())
              .indexName(request.getIndexName())
              .sessionId(request.getSessionId())
              .build();

      IndexRequest<ClickLogDocument> indexRequest =
          IndexRequest.of(i -> i.index(indexName).document(document));

      elasticsearchClient.index(indexRequest);

      log.info(
          "클릭 로그 저장 성공 - 키워드: {}, 상품: {}",
          request.getSearchKeyword(),
          request.getClickedProductId());

      return ClickLogResponse.builder()
          .success(true)
          .message("클릭 로그가 성공적으로 저장되었습니다.")
          .timestamp(now)
          .build();

    } catch (Exception e) {
      throw new RuntimeException("클릭 로그 저장 실패", e);
    }
  }
}
