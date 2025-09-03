package com.yjlee.search.search.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
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
@Schema(description = "쿼리 분석 응답")
public class QueryAnalysisResponse {

  @Schema(description = "분석 환경", example = "CURRENT")
  String environment;

  @Schema(description = "원본 쿼리", example = "삼성전자 노트북 1kg")
  String originalQuery;

  @Schema(description = "Nori 형태소 분석 결과")
  NoriAnalysis noriAnalysis;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @Schema(description = "Nori 형태소 분석 결과")
  public static class NoriAnalysis {

    @Schema(description = "형태소 분석 토큰")
    List<TokenInfo> tokens;

    @Schema(description = "토큰화된 형태와 동의어", example = "데스크 탑{desk top|pc|desktop}")
    String formattedTokens;

    @Schema(description = "원본 토큰별 동의어 확장 정보", example = "{\"제로\": [\"pc\", \"데스크톱\", \"데스크\"]}")
    Map<String, List<String>> synonymExpansions;
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @Schema(description = "토큰 정보")
  public static class TokenInfo {

    @Schema(description = "토큰", example = "삼성")
    String token;

    @Schema(description = "토큰 타입", example = "SYNONYM")
    String type;

    @Schema(description = "위치", example = "0")
    int position;

    @Schema(description = "시작 오프셋", example = "0")
    int startOffset;

    @Schema(description = "종료 오프셋", example = "4")
    int endOffset;
  }

}
