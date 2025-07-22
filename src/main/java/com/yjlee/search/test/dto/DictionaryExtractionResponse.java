package com.yjlee.search.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Schema(description = "사전 엔트리 추출 결과")
public class DictionaryExtractionResponse {

  @Schema(description = "분석 성공 여부")
  private boolean success;

  @Schema(description = "분석한 상품 개수")
  private int analyzedProductCount;

  @Schema(description = "사용자 사전 엔트리 목록")
  private List<UserDictionaryEntry> userDictionaryEntries;

  @Schema(description = "동의어 사전 엔트리 목록")
  private List<SynonymDictionaryEntry> synonymDictionaryEntries;

  @Schema(description = "불용어 사전 엔트리 목록")
  private List<StopwordDictionaryEntry> stopwordDictionaryEntries;

  @Schema(description = "오타교정 사전 엔트리 목록")
  private List<TypoCorrectionDictionaryEntry> typoCorrectionDictionaryEntries;

  @Schema(description = "처리 시간")
  private LocalDateTime processedAt;

  @Schema(description = "LLM 응답 원문")
  private String rawLlmResponse;

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Schema(description = "사용자 사전 엔트리")
  public static class UserDictionaryEntry {
    @Schema(description = "키워드", example = "갤럭시S24")
    private String keyword;

    @Schema(description = "설명", example = "삼성 스마트폰 모델명")
    private String description;
  }

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Schema(description = "동의어 사전 엔트리")
  public static class SynonymDictionaryEntry {
    @Schema(description = "기준 키워드", example = "스마트폰")
    private String keyword;

    @Schema(description = "동의어 목록", example = "핸드폰,휴대폰,모바일")
    private String synonyms;

    @Schema(description = "설명", example = "스마트폰과 동일한 의미의 단어들")
    private String description;
  }

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Schema(description = "불용어 사전 엔트리")
  public static class StopwordDictionaryEntry {
    @Schema(description = "불용어", example = "그리고")
    private String keyword;

    @Schema(description = "설명", example = "검색에서 제외할 접속사")
    private String description;
  }

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Schema(description = "오타교정 사전 엔트리")
  public static class TypoCorrectionDictionaryEntry {
    @Schema(description = "오타", example = "삼송")
    private String keyword;

    @Schema(description = "교정어", example = "삼성")
    private String correctedWord;

    @Schema(description = "설명", example = "삼성 브랜드명 오타")
    private String description;
  }
}
