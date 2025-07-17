package com.yjlee.search.dashboard.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class PopularKeywordResponse {

  List<KeywordStats> keywords;
  String period;

  @Value
  @Builder
  @Jacksonized
  public static class KeywordStats {
    String keyword;
    long count;
    double percentage;
    int rank;
  }
}
