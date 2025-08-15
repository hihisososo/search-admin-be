package com.yjlee.search.dictionary.synonym.recommendation.controller;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.GenerateRequest;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.ListResponse;
import com.yjlee.search.dictionary.synonym.recommendation.service.SynonymTermRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dictionaries/synonyms/term-recommendations")
@RequiredArgsConstructor
@Tag(name = "Synonym Term Recommendation", description = "형태소 term 기반 유의어 추천 API")
public class SynonymTermRecommendationController {

  private final SynonymTermRecommendationService service;

  @PostMapping("/generate")
  @Operation(summary = "유의어(term) 추천 생성", description = "상품명 코퍼스에서 term을 추출하여 완전동의어를 추천합니다")
  public ResponseEntity<Void> generate(@RequestBody(required = false) GenerateRequest request) {
    if (request == null) request = GenerateRequest.builder().build();
    log.info(
        "유의어(term) 추천 요청 - sampleSize: {}, temperature: {}, desiredRecommendationCount: {}",
        request.getSampleSize(),
        request.getTemperature(),
        request.getDesiredRecommendationCount());
    service.generate(request);
    return ResponseEntity.ok().build();
  }

  @GetMapping
  @Operation(summary = "유의어(term) 추천 목록", description = "저장된 term별 유의어 추천 결과를 조회합니다")
  public ResponseEntity<ListResponse> list(
      @RequestParam(name = "sortBy", required = false, defaultValue = "count") String sortBy,
      @RequestParam(name = "sortDir", required = false, defaultValue = "desc") String sortDir) {
    return ResponseEntity.ok(service.list(sortBy, sortDir));
  }

  @PostMapping("/merge-to-dictionary")
  @Operation(
      summary = "추천 결과를 유의어 사전에 병합",
      description = "추천된 항목을 'base => syn1,syn2' 포맷으로 현재 사전에 병합합니다")
  public ResponseEntity<Void> mergeToDictionary(
      @RequestParam(name = "env", required = false) String env) {
    DictionaryEnvironmentType environment =
        "prod".equalsIgnoreCase(env)
            ? DictionaryEnvironmentType.PROD
            : "dev".equalsIgnoreCase(env)
                ? DictionaryEnvironmentType.DEV
                : DictionaryEnvironmentType.CURRENT;
    service.mergeToDictionary(environment);
    return ResponseEntity.ok().build();
  }
}
