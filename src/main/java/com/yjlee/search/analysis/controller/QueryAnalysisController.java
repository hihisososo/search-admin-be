package com.yjlee.search.analysis.controller;

import com.yjlee.search.analysis.dto.AnalysisRequest;
import com.yjlee.search.analysis.dto.IndexAnalysisResponse;
import com.yjlee.search.analysis.dto.QueryAnalysisResponse;
import com.yjlee.search.analysis.dto.TempIndexRefreshResponse;
import com.yjlee.search.analysis.service.AnalysisService;
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

@Tag(name = "형태소 분석 API")
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class QueryAnalysisController {

  private final AnalysisService analysisService;
  private final TempIndexService tempIndexManager;

  @Operation(summary = "검색용 형태소 분석", description = "형태소 분석, 동의어 확장, 단위 추출, 모델명 추출 결과를 반환")
  @PostMapping("/search")
  public ResponseEntity<QueryAnalysisResponse> analyzeQuery(
      @RequestBody @Valid AnalysisRequest request) {
    return ResponseEntity.ok(analysisService.analyzeQuery(request));
  }

  @Operation(summary = "색인용 형태소 분석", description = "형태소 분석, 동의어 확장, 단위 추출, 모델명 추출 결과를 반환")
  @PostMapping("/index")
  public ResponseEntity<IndexAnalysisResponse> analyzeForIndexing(
      @RequestBody @Valid AnalysisRequest request) {
    return ResponseEntity.ok(analysisService.analyzeForIndexing(request));
  }

  @Operation(summary = "임시 인덱스 갱신", description = "CURRENT 환경의 사전 데이터로 임시 분석 인덱스를 재생성")
  @PostMapping("/temp-index-refresh")
  public ResponseEntity<TempIndexRefreshResponse> refreshTempIndex() {
    return ResponseEntity.ok(tempIndexManager.refreshTempIndexWithResponse());
  }
}
