package com.yjlee.search.dictionary.typo.recommendation.controller;

import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationListResponse;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationRequest;
import com.yjlee.search.dictionary.typo.recommendation.service.TypoCorrectionRecommendationService;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
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
  private final TypoCorrectionDictionaryService dictionaryService;

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
  public ResponseEntity<TypoCorrectionRecommendationListResponse> list(
      @RequestParam(name = "sortBy", required = false, defaultValue = "count") String sortBy,
      @RequestParam(name = "sortDir", required = false, defaultValue = "desc") String sortDir) {
    return ResponseEntity.ok(recommendationService.getRecommendations(sortBy, sortDir));
  }

  @PostMapping("/promote-to-dictionary")
  @Operation(
      summary = "오타 추천을 사전으로 일괄 이동",
      description = "저장된 오타 교정어 추천을 현재 사전으로 모두 이동합니다 (추천은 이동 후 삭제)")
  public ResponseEntity<Map<String, Object>> promoteToDictionary() {
    var result = dictionaryService.promoteRecommendationsToCurrentDictionary(1, true);
    Map<String, Object> body = new HashMap<>();
    body.put("success", true);
    body.put("total", result.total());
    body.put("inserted", result.inserted());
    body.put("skippedExists", result.skippedExists());
    body.put("skippedMalformed", result.skippedMalformed());
    body.put("skippedBelowMinCount", result.skippedBelowMinCount());
    body.put("deleted", result.deleted());
    return ResponseEntity.ok(body);
  }
}
