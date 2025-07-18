package com.yjlee.search.search.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SearchExecuteResponse {
  String indexName;
  Object searchResult;
  long took;
}
