package com.yjlee.search.common;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

@Getter
@ToString
@Builder
@Jacksonized
public class ErrorResponse {
  private final int code;
  private final String message;
  @Default private final LocalDateTime timestamp = LocalDateTime.now();
  private final String errorId;
  private final String path;
}
