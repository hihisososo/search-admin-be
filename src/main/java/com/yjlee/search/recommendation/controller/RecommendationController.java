package com.yjlee.search.recommendation.controller;

import com.yjlee.search.recommendation.dto.RelatedKeywordCalculationRequest;
import com.yjlee.search.recommendation.dto.RelatedKeywordResponse;
import com.yjlee.search.recommendation.model.RelatedKeywordDocument;
import com.yjlee.search.recommendation.service.RelatedKeywordCalculationService;
import com.yjlee.search.recommendation.service.RelatedKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Recommendation", description = "추천 API")
@RestController
@RequestMapping("/api/v1/recommendation")
@RequiredArgsConstructor
public class RecommendationController {

  private final RelatedKeywordService relatedKeywordService;
  private final RelatedKeywordCalculationService calculationService;

  @Operation(summary = "연관검색어 조회")
  @GetMapping("/related-keywords")
  public ResponseEntity<RelatedKeywordResponse> getRelatedKeywords(@RequestParam String keyword) {

    List<RelatedKeywordDocument.RelatedKeyword> relatedKeywords =
        relatedKeywordService.getRelatedKeywords(keyword);

    return ResponseEntity.ok(
        RelatedKeywordResponse.builder()
            .keyword(keyword)
            .relatedKeywords(relatedKeywords)
            .count(relatedKeywords.size())
            .build());
  }

  @Operation(summary = "연관검색어 계산 (관리자용)")
  @PostMapping("/admin/calculate")
  public ResponseEntity<String> calculateRelatedKeywords(
      @RequestBody RelatedKeywordCalculationRequest request) {

    log.info("연관검색어 계산 요청: {} ~ {}", request.getFrom(), request.getTo());

    LocalDateTime from =
        request.getFrom() != null ? request.getFrom() : LocalDateTime.now().minusDays(7);
    LocalDateTime to = request.getTo() != null ? request.getTo() : LocalDateTime.now();

    // 비동기로 처리하거나 별도 스레드로 처리 권장
    new Thread(() -> calculationService.calculateRelatedKeywords(from, to)).start();

    return ResponseEntity.ok("연관검색어 계산이 시작되었습니다.");
  }

  @Operation(summary = "연관검색어 즉시 계산 (관리자용)")
  @PostMapping("/admin/calculate-now")
  public ResponseEntity<String> calculateRelatedKeywordsNow(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to) {

    LocalDateTime fromDate = from != null ? from : LocalDateTime.now().minusDays(7);
    LocalDateTime toDate = to != null ? to : LocalDateTime.now();

    log.info("연관검색어 즉시 계산 시작: {} ~ {}", fromDate, toDate);

    calculationService.calculateRelatedKeywords(fromDate, toDate);

    return ResponseEntity.ok("연관검색어 계산이 완료되었습니다.");
  }
}
