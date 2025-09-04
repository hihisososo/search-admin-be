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
@Schema(description = "사용하지 않는 인덱스 삭제 응답")
public class DeleteUnusedIndicesResponse {

  @Schema(
      description = "삭제된 인덱스 목록",
      example = "[\"products-20240101120000\", \"autocomplete-20240101120000\"]")
  private List<String> deletedIndices;

  @Schema(description = "삭제 실패한 인덱스 목록", example = "[]")
  private List<String> failedIndices;

  @Schema(description = "삭제 요청된 인덱스 개수", example = "5")
  private int requestedCount;

  @Schema(description = "성공적으로 삭제된 인덱스 개수", example = "5")
  private int deletedCount;

  @Schema(description = "삭제 실패한 인덱스 개수", example = "0")
  private int failedCount;

  @Schema(description = "작업 성공 여부", example = "true")
  private boolean success;

  @Schema(description = "작업 메시지", example = "5개의 미사용 인덱스가 삭제되었습니다")
  private String message;

  public static DeleteUnusedIndicesResponse of(
      List<String> deletedIndices, List<String> failedIndices, int requestedCount) {

    int deletedCount = deletedIndices.size();
    int failedCount = failedIndices.size();
    boolean success = failedCount == 0;
    String message = String.format("%d개의 미사용 인덱스 중 %d개가 삭제되었습니다", requestedCount, deletedCount);

    if (failedCount > 0) {
      message += String.format(" (%d개 실패)", failedCount);
    }

    return DeleteUnusedIndicesResponse.builder()
        .deletedIndices(deletedIndices)
        .failedIndices(failedIndices)
        .requestedCount(requestedCount)
        .deletedCount(deletedCount)
        .failedCount(failedCount)
        .success(success)
        .message(message)
        .build();
  }
}
