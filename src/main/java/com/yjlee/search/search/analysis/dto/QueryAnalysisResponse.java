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

  @Schema(description = "분석된 토큰 리스트", example = "[\"삼성전자\", \"노트북\", \"1kg\"]")
  List<String> tokens;

  @Schema(
      description = "동의어 확장 결과",
      example = "{\"pc\": [\"데스크탑\", \"데스크톱\"], \"사과상자\": [\"apple상자\", \"애플box\"]}")
  Map<String, List<String>> synonymExpansions;

  @Schema(description = "Mermaid 그래프 다이어그램")
  String mermaidGraph;
}
