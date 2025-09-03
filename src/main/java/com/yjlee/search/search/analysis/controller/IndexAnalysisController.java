package com.yjlee.search.search.analysis.controller;

import com.yjlee.search.search.analysis.dto.IndexAnalysisRequest;
import com.yjlee.search.search.analysis.dto.IndexAnalysisResponse;
import com.yjlee.search.search.analysis.service.IndexAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/index/query-analysis")
@Tag(name = "색인 분석", description = "색인 분석 API")
public class IndexAnalysisController {

  private final IndexAnalysisService indexAnalysisService;

  @PostMapping
  @Operation(summary = "색인 분석", description = "주어진 쿼리를 색인 분석기로 분석합니다")
  public ResponseEntity<IndexAnalysisResponse> analyzeForIndexing(
      @Valid @RequestBody IndexAnalysisRequest request) {

    log.info("색인 분석 요청 - 쿼리: {}, 환경: {}", request.getQuery(), request.getEnvironment());

    IndexAnalysisResponse response = indexAnalysisService.analyzeForIndexing(request);
    return ResponseEntity.ok(response);
  }
}
