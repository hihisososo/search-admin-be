package com.yjlee.search.dictionary.synonym.recommendation.controller;

import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymRecommendationListResponse;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymRecommendationRequest;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymRecommendationResponse;
import com.yjlee.search.dictionary.synonym.recommendation.service.SynonymRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dictionaries/synonyms/recommendations")
@RequiredArgsConstructor
@Tag(name = "Synonym Recommendation", description = "유의어 사전 추천 관리 API")
public class SynonymRecommendationController {

  private final SynonymRecommendationService recommendationService;

  @PostMapping("/generate")
  @Operation(summary = "유의어 추천 그룹 생성", description = "상품명을 분석하여 유의어 추천 그룹을 생성합니다")
  public ResponseEntity<SynonymRecommendationResponse> generateSynonymRecommendations(
      @RequestBody(required = false) SynonymRecommendationRequest request) {

    if (request == null) {
      request = SynonymRecommendationRequest.builder().build();
    }

    log.info("유의어 추천 요청 - 샘플 크기: {}", request.getSampleSize());
    SynonymRecommendationResponse response = recommendationService.generateRecommendations(request);
    log.info(
        "유의어 추천 완료 - 생성: {}, 저장: {}, 중복: {}",
        response.getTotalGenerated(),
        response.getTotalSaved(),
        response.getDuplicatesSkipped());

    return ResponseEntity.ok(response);
  }

  @GetMapping
  @Operation(summary = "추천 유의어 그룹 목록 조회", description = "저장된 추천 유의어 그룹 목록을 조회합니다")
  public ResponseEntity<SynonymRecommendationListResponse> getSynonymRecommendations() {
    SynonymRecommendationListResponse response = recommendationService.getRecommendations();
    return ResponseEntity.ok(response);
  }
}


