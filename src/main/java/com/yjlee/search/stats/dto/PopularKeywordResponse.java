package com.yjlee.search.stats.dto;

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
    Integer previousRank;
    Integer rankChange;
    RankChangeStatus changeStatus;
  }

  public enum RankChangeStatus {
    UP, // 순위 상승
    DOWN, // 순위 하락
    SAME, // 변동 없음
    NEW // 신규 진입
  }
}
