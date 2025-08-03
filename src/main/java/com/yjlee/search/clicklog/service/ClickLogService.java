package com.yjlee.search.clicklog.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import com.yjlee.search.clicklog.model.ClickLogDocument;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickLogService {

  private final ElasticsearchClient elasticsearchClient;
  private static final String CLICK_LOG_INDEX_PREFIX = "click-logs-";
  private static final DateTimeFormatter INDEX_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

  public ClickLogResponse logClick(ClickLogRequest request, HttpServletRequest httpRequest) {
    try {
      LocalDateTime now = LocalDateTime.now();
      String indexName = CLICK_LOG_INDEX_PREFIX + now.format(INDEX_DATE_FORMATTER);
      
      ClickLogDocument document = buildClickLogDocument(request, httpRequest, now);
      
      IndexRequest<ClickLogDocument> indexRequest = IndexRequest.of(i -> i
          .index(indexName)
          .document(document));
      
      IndexResponse response = elasticsearchClient.index(indexRequest);
      
      log.info("클릭 로그 저장 성공 - 세션: {}, 키워드: {}, 상품: {}, 위치: {}", 
          request.getSearchSessionId(), 
          request.getSearchKeyword(),
          request.getClickedProductId(),
          request.getClickPosition());
      
      return ClickLogResponse.builder()
          .success(true)
          .message("클릭 로그가 성공적으로 저장되었습니다.")
          .timestamp(now.toString())
          .build();
      
    } catch (Exception e) {
      log.error("클릭 로그 저장 실패: {}", e.getMessage(), e);
      return ClickLogResponse.builder()
          .success(false)
          .message("클릭 로그 저장 중 오류가 발생했습니다: " + e.getMessage())
          .timestamp(LocalDateTime.now().toString())
          .build();
    }
  }

  private ClickLogDocument buildClickLogDocument(
      ClickLogRequest request, 
      HttpServletRequest httpRequest, 
      LocalDateTime timestamp) {
    
    return ClickLogDocument.builder()
        .timestamp(timestamp)
        .searchSessionId(request.getSearchSessionId())
        .searchKeyword(request.getSearchKeyword())
        .clickedProductId(request.getClickedProductId())
        .clickedProductName(request.getClickedProductName())
        .clickPosition(request.getClickPosition())
        .indexName(request.getIndexName())
        .clientIp(getClientIp(httpRequest))
        .userAgent(httpRequest.getHeader("User-Agent"))
        .clickType(request.getClickType())
        .dwellTimeMs(request.getDwellTimeMs())
        .build();
  }

  private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp;
    }
    
    return request.getRemoteAddr();
  }
}