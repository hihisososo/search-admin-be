package com.yjlee.search.searchlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "검색 로그 응답")
public class SearchLogResponse {

  @Schema(description = "로그 ID", example = "es_doc_id_123")
  private String id;

  @Schema(description = "검색 실행 시간", example = "2025-01-23T14:30:25")
  private LocalDateTime timestamp;

  @Schema(description = "검색 키워드", example = "삼성 갤럭시")
  private String searchKeyword;

  @Schema(description = "검색 대상 인덱스명", example = "products")
  private String indexName;

  @Schema(description = "응답 시간 (밀리초)", example = "45")
  private Long responseTimeMs;

  @Schema(description = "검색 결과 수", example = "156")
  private Long resultCount;

  @Schema(description = "클라이언트 IP", example = "192.168.1.100")
  private String clientIp;

  @Schema(description = "User-Agent", example = "Mozilla/5.0...")
  private String userAgent;

  @Schema(description = "에러 발생 여부", example = "false")
  private Boolean isError;

  @Schema(description = "에러 메시지", example = "null")
  private String errorMessage;
}
