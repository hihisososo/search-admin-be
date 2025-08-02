package com.yjlee.search.searchlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "인기 검색어")
public class PopularKeywordDto {

  @Schema(description = "검색어", example = "아이폰")
  private String keyword;

  @Schema(description = "검색 횟수", example = "1250")
  private Long searchCount;

  @Schema(description = "순위", example = "1")
  private Integer rank;

  @Schema(description = "이전 순위", example = "3")
  private Integer previousRank;

  @Schema(description = "순위 변동 (양수: 순위 상승, 음수: 순위 하락, 0: 변동 없음)", example = "2")
  private Integer rankChange;

  @Schema(
      description = "변동 상태",
      example = "UP",
      allowableValues = {"UP", "DOWN", "SAME", "NEW"})
  private RankChangeStatus changeStatus;

  public enum RankChangeStatus {
    UP, // 순위 상승
    DOWN, // 순위 하락
    SAME, // 변동 없음
    NEW // 신규 진입
  }
}
