package com.yjlee.search.index.controller;

import com.yjlee.search.index.dto.KeywordExtractionResponse;
import com.yjlee.search.index.service.KeywordExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/test/keywords")
@RequiredArgsConstructor
@Tag(name = "키워드 추출 테스트", description = "상품명에서 키워드 추출 테스트 API")
public class KeywordExtractionController {

  private final KeywordExtractionService keywordExtractionService;

  @GetMapping("/extract-from-products")
  @Operation(summary = "상품 테이블에서 키워드 추출", description = "상품 테이블의 name 필드에서 검색 가능한 키워드들을 추출합니다.")
  public ResponseEntity<List<KeywordExtractionResponse>> extractKeywordsFromProducts(
      @Parameter(description = "추출할 상품 수", example = "10") @RequestParam(defaultValue = "10")
          int limit) {

    log.info("상품 테이블에서 키워드 추출 요청 - limit: {}", limit);

    if (limit > 100) {
      limit = 100; // 최대 100개로 제한
    }

    List<KeywordExtractionResponse> results =
        keywordExtractionService.extractKeywordsFromProducts(limit);

    log.info("키워드 추출 완료 - 결과: {} 건", results.size());
    return ResponseEntity.ok(results);
  }

  @GetMapping("/extract-from-name")
  @Operation(summary = "특정 상품명에서 키워드 추출", description = "입력된 상품명에서 검색 가능한 키워드들을 추출합니다.")
  public ResponseEntity<KeywordExtractionResponse> extractKeywordsFromName(
      @Parameter(description = "상품명", example = "아이폰 15 Pro 128GB 블루", required = true)
          @RequestParam
          String productName) {

    log.info("상품명에서 키워드 추출 요청: {}", productName);

    KeywordExtractionResponse result =
        keywordExtractionService.extractKeywordsFromProductName(productName);

    return ResponseEntity.ok(result);
  }

  @GetMapping("/sample-product-names")
  @Operation(summary = "샘플 상품명 조회", description = "테스트용 상품명들을 조회합니다.")
  public ResponseEntity<List<String>> getSampleProductNames(
      @Parameter(description = "조회할 상품명 수", example = "20") @RequestParam(defaultValue = "20")
          int count) {

    log.info("샘플 상품명 조회 요청 - count: {}", count);

    if (count > 50) {
      count = 50; // 최대 50개로 제한
    }

    List<String> productNames = keywordExtractionService.getRandomProductNames(count);

    log.info("샘플 상품명 조회 완료 - 결과: {} 건", productNames.size());
    return ResponseEntity.ok(productNames);
  }

  @PostMapping("/batch-extract")
  @Operation(summary = "여러 상품명에서 일괄 키워드 추출", description = "여러 상품명을 입력받아 각각에서 키워드를 추출합니다.")
  public ResponseEntity<List<KeywordExtractionResponse>> batchExtractKeywords(
      @Parameter(description = "상품명 목록", required = true) @RequestBody List<String> productNames) {

    log.info("일괄 키워드 추출 요청 - 상품명 수: {}", productNames.size());

    if (productNames.size() > 50) {
      productNames = productNames.subList(0, 50); // 최대 50개로 제한
    }

    List<KeywordExtractionResponse> results =
        productNames.stream()
            .map(keywordExtractionService::extractKeywordsFromProductName)
            .toList();

    log.info("일괄 키워드 추출 완료 - 결과: {} 건", results.size());
    return ResponseEntity.ok(results);
  }

  @GetMapping("/demo")
  @Operation(summary = "키워드 추출 데모", description = "다양한 상품명 예시로 키워드 추출 결과를 보여줍니다.")
  public ResponseEntity<List<KeywordExtractionResponse>> demo() {

    log.info("키워드 추출 데모 요청");

    List<String> demoProductNames =
        List.of(
            "아이폰 15 Pro 128GB 블루",
            "삼성 갤럭시 S24 Ultra 256GB 티타늄 그레이",
            "MacBook Pro 14인치 M3 Pro 512GB 스페이스 그레이",
            "LG 그램 17인치 노트북 1TB SSD",
            "소니 WH-1000XM5 블루투스 헤드폰 블랙",
            "Apple Watch Series 9 45mm GPS 실버",
            "Nintendo Switch OLED 화이트",
            "iPad Air 5세대 10.9인치 Wi-Fi 64GB 핑크");

    List<KeywordExtractionResponse> results =
        demoProductNames.stream()
            .map(keywordExtractionService::extractKeywordsFromProductName)
            .toList();

    log.info("키워드 추출 데모 완료 - 결과: {} 건", results.size());
    return ResponseEntity.ok(results);
  }
}
