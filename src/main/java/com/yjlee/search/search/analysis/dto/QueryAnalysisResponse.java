package com.yjlee.search.search.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
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

  @Schema(description = "추출된 단위 정보")
  List<UnitInfo> units;

  @Schema(description = "추출된 모델명", example = "[\"ABC-123\", \"XYZ-789\"]")
  List<String> models;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @Schema(description = "Nori 형태소 분석 결과")
  public static class NoriAnalysis {

    @Schema(description = "형태소 분석 토큰")
    List<TokenInfo> tokens;

    @Schema(description = "동의어 확장 경로 목록", example = "[\"pc\", \"데스크톱\", \"데스크 탑\"]")
    List<String> synonymPaths;
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

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @Schema(description = "단위 정보")
  public static class UnitInfo {

    @Schema(description = "원본 단위", example = "1kg")
    String original;

    @Schema(description = "확장된 동의어", example = "[\"1kg\", \"1킬로그램\", \"1킬로\"]")
    Set<String> expanded;
  }
}
