package com.yjlee.search.dictionary.user.dto;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AnalyzeTextResponse {

  String environment;
  String originalText;
  List<TokenInfo> tokens;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class TokenInfo {
    String token;
    String type;
    int position;
    int startOffset;
    int endOffset;
    List<String> positionLengthTags;
  }
}
