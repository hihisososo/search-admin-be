package com.yjlee.search.searchlog.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 검색 로그를 저장하는 Elasticsearch 문서 모델 인덱스 패턴: search-logs-yyyy.MM.dd */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchLogDocument {

  /** 검색 실행 시간 */
  private LocalDateTime timestamp;

  /** 검색 키워드 */
  @JsonProperty("search_keyword")
  @JsonAlias("searchKeyword")
  private String searchKeyword;

  /** 검색 대상 인덱스명 */
  @JsonProperty("index_name")
  @JsonAlias("indexName")
  private String indexName;

  /** 응답 시간 (밀리초) */
  @JsonProperty("response_time_ms")
  @JsonAlias("responseTimeMs")
  private Long responseTimeMs;

  /** 검색 결과 수 */
  @JsonProperty("result_count")
  @JsonAlias("resultCount")
  private Long resultCount;

  /** 실행된 Query DSL (저장용, 검색 불가) */
  @JsonProperty("query_dsl")
  @JsonAlias("queryDsl")
  private String queryDsl;

  /** 클라이언트 IP */
  @JsonProperty("client_ip")
  @JsonAlias("clientIp")
  private String clientIp;

  /** User-Agent */
  @JsonProperty("user_agent")
  @JsonAlias("userAgent")
  private String userAgent;

  /** 에러 발생 여부 */
  @JsonProperty("is_error")
  @JsonAlias("isError")
  private Boolean isError;

  /** 에러 메시지 (저장용, 검색 불가) */
  @JsonProperty("error_message")
  @JsonAlias("errorMessage")
  private String errorMessage;

  /** 검색 세션 ID */
  @JsonProperty("session_id")
  @JsonAlias("sessionId")
  private String sessionId;
}
