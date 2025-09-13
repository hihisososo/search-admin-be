package com.yjlee.search.async.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "비동기 작업 상세 정보")
public class AsyncTaskResponse {

  @Schema(description = "작업 ID", example = "1")
  private Long id;

  @Schema(description = "작업 타입")
  private AsyncTaskType taskType;

  @Schema(description = "작업 상태")
  private AsyncTaskStatus status;

  @Schema(description = "진행률 (0-100)", example = "50")
  private Integer progress;

  @Schema(description = "진행 상태 메시지", example = "데이터 처리 중...")
  private String message;

  @Schema(description = "에러 메시지", example = "null")
  private String errorMessage;

  @Schema(description = "작업 결과 (JSON 형식)", example = "null")
  private String result;

  @Schema(description = "생성 시간", example = "2024-01-01T00:00:00Z")
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime createdAt;

  @Schema(description = "시작 시간", example = "2024-01-01T00:00:05Z")
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime startedAt;

  @Schema(description = "완료 시간", example = "2024-01-01T00:00:30Z")
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime completedAt;
}
