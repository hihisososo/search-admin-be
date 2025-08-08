package com.yjlee.search.dictionary.stopword.recommendation.controller;

import com.yjlee.search.dictionary.stopword.recommendation.dto.StopwordRecommendationListResponse;
import com.yjlee.search.dictionary.stopword.recommendation.dto.StopwordRecommendationRequest;
import com.yjlee.search.dictionary.stopword.recommendation.service.StopwordRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dictionaries/stopwords/recommendations")
@RequiredArgsConstructor
@Tag(name = "Stopword Recommendation", description = "불용어 추천 관리 API")
public class StopwordRecommendationController {

  private final StopwordRecommendationService recommendationService;

  @PostMapping("/generate")
  @Operation(summary = "불용어 추천 생성", description = "상품명을 분석하여 불용어를 추천합니다")
  public ResponseEntity<Void> generateStopwordRecommendations(
      @RequestBody(required = false) StopwordRecommendationRequest request) {
    if (request == null) request = StopwordRecommendationRequest.builder().build();
    log.info("불용어 추천 요청 - 샘플 크기: {}", request.getSampleSize());
    recommendationService.generateRecommendations(request);
    return ResponseEntity.ok().build();
  }

  @GetMapping
  @Operation(summary = "불용어 추천 목록", description = "저장된 불용어 추천 목록을 조회합니다")
  public ResponseEntity<StopwordRecommendationListResponse> list() {
    return ResponseEntity.ok(recommendationService.getRecommendations());
  }
}


