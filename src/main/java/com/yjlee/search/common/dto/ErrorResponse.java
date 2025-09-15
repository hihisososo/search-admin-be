package com.yjlee.search.common.dto;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
  @Default private final LocalDateTime timestamp = LocalDateTime.now(ZoneOffset.UTC);
  private final String path;
}
