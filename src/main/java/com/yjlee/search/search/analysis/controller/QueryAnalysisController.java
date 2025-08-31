package com.yjlee.search.search.analysis.controller;

import com.yjlee.search.search.analysis.dto.QueryAnalysisRequest;
import com.yjlee.search.search.analysis.dto.QueryAnalysisResponse;
import com.yjlee.search.search.analysis.service.QueryAnalysisService;
import com.yjlee.search.search.analysis.service.TempIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Query Analysis", description = "쿼리 분석 API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class QueryAnalysisController {

  private final QueryAnalysisService queryAnalysisService;
  private final TempIndexService tempIndexService;

  @Operation(
      summary = "쿼리 분석",
      description = "입력된 쿼리를 분석하여 형태소 분석, 동의어 확장, 단위 추출, 모델명 추출 결과를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @PostMapping("/query-analysis")
  public ResponseEntity<QueryAnalysisResponse> analyzeQuery(
      @RequestBody @Valid QueryAnalysisRequest request) {

    log.info("쿼리 분석 요청 - 쿼리: {}, 환경: {}", request.getQuery(), request.getEnvironment());

    try {
      QueryAnalysisResponse response = queryAnalysisService.analyzeQuery(request);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("쿼리 분석 실패", e);
      throw new RuntimeException("쿼리 분석 중 오류가 발생했습니다: " + e.getMessage());
    }
  }

  @Operation(summary = "임시 인덱스 갱신", description = "CURRENT 환경의 사전 데이터로 임시 분석 인덱스를 재생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @PostMapping("/temp-index/refresh")
  public ResponseEntity<Map<String, String>> refreshTempIndex() {

    log.info("임시 인덱스 갱신 요청");

    try {
      tempIndexService.refreshTempIndex();

      Map<String, String> response =
          Map.of(
              "status", "success",
              "message", "임시 인덱스가 성공적으로 갱신되었습니다",
              "indexName", tempIndexService.getTempIndexName());

      log.info("임시 인덱스 갱신 완료");
      return ResponseEntity.ok(response);

    } catch (IOException e) {
      log.error("임시 인덱스 갱신 실패", e);
      throw new RuntimeException("임시 인덱스 갱신 중 오류가 발생했습니다: " + e.getMessage());
    }
  }
}
