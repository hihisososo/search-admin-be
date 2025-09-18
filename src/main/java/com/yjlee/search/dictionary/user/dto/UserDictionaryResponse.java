package com.yjlee.search.dictionary.user.dto;

import com.yjlee.search.dictionary.user.model.UserDictionary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@Schema(description = "사용자 사전 응답")
public class UserDictionaryResponse {

  @Schema(description = "사전 ID", example = "1")
  Long id;

  @Schema(description = "사용자 정의 단어", example = "카카오페이")
  String keyword;

  @Schema(description = "생성 시간", example = "2024-01-01T10:00:00")
  LocalDateTime createdAt;

  @Schema(description = "수정 시간", example = "2024-01-01T10:00:00")
  LocalDateTime updatedAt;

  public static UserDictionaryResponse from(UserDictionary entity) {
    return UserDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
