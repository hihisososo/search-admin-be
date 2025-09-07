package com.yjlee.search.index.controller;

import com.yjlee.search.index.service.ProductIndexingService;
import com.yjlee.search.index.service.monitor.IndexProgressMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/indexing")
@RequiredArgsConstructor
@Tag(name = "Indexing", description = "상품 색인 API")
public class IndexingController {

  private final ProductIndexingService indexingService;
  private final IndexProgressMonitor progressMonitor;

  @PostMapping("/products")
  @Operation(summary = "전체 상품 색인", description = "모든 상품을 병렬로 색인합니다")
  public ResponseEntity<Map<String, Object>> indexAllProducts(
      @Parameter(description = "색인할 최대 문서 수 (기본값: 전체)") @RequestParam(required = false)
          Integer maxDocuments) {
    try {
      if (maxDocuments != null && maxDocuments > 0) {
        log.info("API를 통한 상품 색인 시작 - 최대 {}개 문서", maxDocuments);
      } else {
        log.info("API를 통한 상품 색인 시작 - 전체 문서");
      }

      int totalIndexed = indexingService.indexAllProducts(maxDocuments);

      var stats = progressMonitor.getStatistics();

      return ResponseEntity.ok(
          Map.of(
              "status",
              "success",
              "totalIndexed",
              totalIndexed,
              "maxDocuments",
              maxDocuments != null ? maxDocuments : "unlimited",
              "successRate",
              stats.getSuccessRate(),
              "averageRate",
              stats.getAverageRate(),
              "durationSeconds",
              stats.getTotalDuration().toSeconds()));
    } catch (IOException e) {
      log.error("상품 색인 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "error", "message", e.getMessage()));
    }
  }
}
