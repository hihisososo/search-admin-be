package com.yjlee.search.deployment.dto;

import com.yjlee.search.deployment.model.IndexEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "환경 정보 응답")
public class EnvironmentInfoResponse {

  @Schema(description = "환경 타입")
  private String environmentType;

  @Schema(description = "환경 설명")
  private String environmentDescription;

  @Schema(description = "인덱스 이름")
  private String indexName;

  @Schema(description = "자동완성 인덱스 이름")
  private String autocompleteIndexName;

  @Schema(description = "문서 개수")
  private Long documentCount;

  @Schema(description = "인덱스 상태")
  private String indexStatus;

  @Schema(description = "인덱스 상태 설명")
  private String indexStatusDescription;

  @Schema(description = "색인 날짜")
  private LocalDateTime indexDate;

  @Schema(description = "버전")
  private String version;

  public static EnvironmentInfoResponse from(IndexEnvironment environment) {
    return EnvironmentInfoResponse.builder()
        .environmentType(environment.getEnvironmentType().name())
        .environmentDescription(environment.getEnvironmentType().getDescription())
        .indexName(environment.getIndexName())
        .autocompleteIndexName(environment.getAutocompleteIndexName())
        .documentCount(environment.getDocumentCount())
        .indexStatus(environment.getIndexStatus().name())
        .indexStatusDescription(environment.getIndexStatus().getDescription())
        .indexDate(environment.getIndexDate())
        .version(environment.getVersion())
        .build();
  }
}
