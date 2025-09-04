package com.yjlee.search.deployment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용하지 않는 인덱스 조회 응답")
public class UnusedIndicesResponse {

  @Schema(
      description = "사용하지 않는 인덱스 목록",
      example = "[\"products-20240101120000\", \"autocomplete-20240101120000\"]")
  private List<String> unusedIndices;

  @Schema(
      description = "현재 사용 중인 인덱스 목록",
      example = "[\"products-20240301120000\", \"autocomplete-20240301120000\"]")
  private List<String> usedIndices;

  @Schema(description = "Alias에 연결된 인덱스 목록", example = "[\"products-20240301120000\"]")
  private List<String> aliasedIndices;

  @Schema(description = "전체 인덱스 개수", example = "10")
  private int totalCount;

  @Schema(description = "삭제 가능한 인덱스 개수", example = "5")
  private int deletableCount;

  public static UnusedIndicesResponse of(
      List<String> unusedIndices, List<String> usedIndices, List<String> aliasedIndices) {
    return UnusedIndicesResponse.builder()
        .unusedIndices(unusedIndices)
        .usedIndices(usedIndices)
        .aliasedIndices(aliasedIndices)
        .totalCount(unusedIndices.size() + usedIndices.size())
        .deletableCount(unusedIndices.size())
        .build();
  }
}
