package com.yjlee.search.analysis.controller;

import com.yjlee.search.analysis.dto.IndexAnalysisRequest;
import com.yjlee.search.analysis.dto.IndexAnalysisResponse;
import com.yjlee.search.analysis.dto.QueryAnalysisRequest;
import com.yjlee.search.analysis.dto.QueryAnalysisResponse;
import com.yjlee.search.analysis.dto.TempIndexRefreshResponse;
import com.yjlee.search.analysis.service.IndexAnalysisService;
import com.yjlee.search.analysis.service.QueryAnalysisService;
import com.yjlee.search.analysis.service.TempIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Query Analysis", description = "쿼리 분석 API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class QueryAnalysisController {

  private final QueryAnalysisService queryAnalysisService;
  private final TempIndexService tempIndexService;
  private final IndexAnalysisService indexAnalysisService;

  @Operation(summary = "쿼리 분석", description = "입력된 쿼리를 분석하여 형태소 분석, 동의어 확장, 단위 추출, 모델명 추출 결과를 반환합니다.")
  @PostMapping("/query-analysis")
  public ResponseEntity<QueryAnalysisResponse> analyzeQuery(
      @RequestBody @Valid QueryAnalysisRequest request) {
    return ResponseEntity.ok(queryAnalysisService.analyzeQuery(request));
  }

  @Operation(summary = "색인 분석", description = "주어진 쿼리를 색인 분석기로 분석합니다")
  @PostMapping("/index-analysis")
  public ResponseEntity<IndexAnalysisResponse> analyzeForIndexing(
      @RequestBody @Valid IndexAnalysisRequest request) {
    return ResponseEntity.ok(indexAnalysisService.analyzeForIndexing(request));
  }

  @Operation(summary = "임시 인덱스 갱신", description = "CURRENT 환경의 사전 데이터로 임시 분석 인덱스를 재생성합니다.")
  @PostMapping("/temp-index/refresh")
  public ResponseEntity<TempIndexRefreshResponse> refreshTempIndex() {
    return ResponseEntity.ok(tempIndexService.refreshTempIndexWithResponse());
  }
}
