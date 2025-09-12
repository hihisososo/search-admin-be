package com.yjlee.search.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "쿼리 분석 응답")
public class QueryAnalysisResponse {

  @Schema(description = "분석 환경", example = "CURRENT")
  String environment;

  @Schema(description = "원본 쿼리", example = "삼성전자 노트북 1kg")
  String originalQuery;

  @Schema(description = "전처리된 쿼리", example = "삼성전자 노트북 1kg")
  String preprocessedQuery;

  @Schema(description = "분석된 토큰 리스트", example = "[\"삼성전자\", \"노트북\", \"1kg\"]")
  List<String> tokens;

  @Schema(description = "Mermaid 그래프 다이어그램")
  String mermaidGraph;
}
