package com.yjlee.search.dictionary.recommendation.controller;

import com.yjlee.search.dictionary.recommendation.dto.RecommendationListResponse;
import com.yjlee.search.dictionary.recommendation.dto.RecommendationRequest;
import com.yjlee.search.dictionary.recommendation.dto.RecommendationResponse;
import com.yjlee.search.dictionary.recommendation.service.DictionaryRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dictionaries/recommendations")
@RequiredArgsConstructor
@Tag(name = "Dictionary Recommendation", description = "사용자 사전 추천 관리 API")
public class DictionaryRecommendationController {

  private final DictionaryRecommendationService recommendationService;

  @PostMapping("/generate")
  @Operation(summary = "사용자 사전 추천 단어 생성", description = "상품명을 분석하여 사용자 사전 추천 단어를 생성합니다")
  public ResponseEntity<RecommendationResponse> generateRecommendations(
      @RequestBody(required = false) RecommendationRequest request) {

    if (request == null) {
      request = RecommendationRequest.builder().build();
    }

    log.info("사용자 사전 추천 요청 - 샘플 크기: {}", request.getSampleSize());
    RecommendationResponse response = recommendationService.generateRecommendations(request);
    log.info(
        "사용자 사전 추천 완료 - 생성: {}, 저장: {}, 중복: {}",
        response.getTotalGenerated(),
        response.getTotalSaved(),
        response.getDuplicatesSkipped());

    return ResponseEntity.ok(response);
  }

  @GetMapping
  @Operation(summary = "추천 단어 목록 조회", description = "저장된 추천 단어 목록을 조회합니다")
  public ResponseEntity<RecommendationListResponse> getRecommendations() {
    RecommendationListResponse response = recommendationService.getRecommendations();
    return ResponseEntity.ok(response);
  }
}
