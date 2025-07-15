package com.yjlee.search.index.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class IndexDownloadResponse {
  String presignedUrl;
}
