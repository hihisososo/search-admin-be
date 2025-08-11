package com.yjlee.search.dictionary.typo.recommendation.controller;

import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationListResponse;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationRequest;
import com.yjlee.search.dictionary.typo.recommendation.service.TypoCorrectionRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dictionaries/typos/recommendations")
@RequiredArgsConstructor
@Tag(name = "Typo Correction Recommendation", description = "오타 교정어 추천 관리 API")
public class TypoCorrectionRecommendationController {

  private final TypoCorrectionRecommendationService recommendationService;

  @PostMapping("/generate")
  @Operation(summary = "오타 교정어 추천 생성", description = "상품명을 분석하여 공백 기준 오타 교정어 페어를 추천합니다")
  public ResponseEntity<Void> generateTypoRecommendations(
      @RequestBody(required = false) TypoCorrectionRecommendationRequest request) {
    if (request == null) request = TypoCorrectionRecommendationRequest.builder().build();
    log.info("오타 교정어 추천 요청 - 샘플 크기: {}", request.getSampleSize());
    recommendationService.generateRecommendations(request);
    return ResponseEntity.ok().build();
  }

  @GetMapping
  @Operation(summary = "오타 교정어 추천 목록", description = "저장된 오타 교정어 추천 목록을 조회합니다")
  public ResponseEntity<TypoCorrectionRecommendationListResponse> list() {
    return ResponseEntity.ok(recommendationService.getRecommendations());
  }
}
