package com.yjlee.search.recommendation.dto;

import com.yjlee.search.recommendation.model.RelatedKeywordDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "연관검색어 응답")
public class RelatedKeywordResponse {

  @Schema(description = "원본 검색어", example = "아이폰")
  private String keyword;

  @Schema(description = "연관검색어 목록")
  private List<RelatedKeywordDocument.RelatedKeyword> relatedKeywords;

  @Schema(description = "연관검색어 개수", example = "5")
  private int count;
}
