package com.yjlee.search.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yjlee.search.evaluation.model.AsyncTaskStatus;
import com.yjlee.search.evaluation.model.AsyncTaskType;
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
public class AsyncTaskResponse {

  private Long id;
  private AsyncTaskType taskType;
  private AsyncTaskStatus status;
  private Integer progress;
  private String message;
  private String errorMessage;
  private String result;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime createdAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime startedAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime completedAt;
}
