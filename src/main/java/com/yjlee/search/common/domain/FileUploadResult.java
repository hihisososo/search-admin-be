package com.yjlee.search.common.domain;

@lombok.Builder
@lombok.Getter
public class FileUploadResult {
  private final boolean success;
  private final String commandId;
  private final String message;
  private final String version;
}
