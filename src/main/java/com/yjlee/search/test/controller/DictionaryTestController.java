package com.yjlee.search.test.controller;

import com.yjlee.search.test.dto.DictionaryExtractionResponse;
import com.yjlee.search.test.service.DictionaryExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/test/dictionary")
@RequiredArgsConstructor
@Tag(name = "Dictionary Test", description = "사전 데이터 추출 테스트 API")
public class DictionaryTestController {

  private final DictionaryExtractionService dictionaryExtractionService;

  @Operation(
      summary = "상품명 기반 사전 엔트리 추출",
      description = "상품 테이블의 name 필드를 분석하여 각 사전에 적합한 엔트리를 LLM으로 추출")
  @PostMapping("/extract")
  public ResponseEntity<DictionaryExtractionResponse> extractDictionaryEntries(
      @Parameter(description = "분석할 상품 개수 제한", example = "100") @RequestParam(defaultValue = "100")
          int limit) {

    log.info("사전 엔트리 추출 요청 - 제한: {}", limit);

    try {
      DictionaryExtractionResponse response =
          dictionaryExtractionService.extractDictionaryEntries(limit);
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("사전 엔트리 추출 실패", e);
      throw new RuntimeException("사전 엔트리 추출 중 오류가 발생했습니다: " + e.getMessage());
    }
  }

  @Operation(summary = "상품명 목록 조회", description = "테스트용으로 상품명 목록만 조회")
  @GetMapping("/products/names")
  public ResponseEntity<?> getProductNames(
      @Parameter(description = "조회할 상품 개수 제한", example = "10") @RequestParam(defaultValue = "10")
          int limit) {

    try {
      var productNames = dictionaryExtractionService.getProductNames(limit);
      return ResponseEntity.ok(
          java.util.Map.of(
              "success", true, "count", productNames.size(), "productNames", productNames));

    } catch (Exception e) {
      log.error("상품명 조회 실패", e);
      return ResponseEntity.internalServerError()
          .body(java.util.Map.of("success", false, "message", e.getMessage()));
    }
  }
}
