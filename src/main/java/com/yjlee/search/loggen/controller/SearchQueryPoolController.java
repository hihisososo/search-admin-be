package com.yjlee.search.loggen.controller;

import com.yjlee.search.loggen.service.SearchQueryPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SearchQueryPool", description = "검색어 풀 관리 API")
@RestController
@RequestMapping("/api/v1/admin/query-pool")
@RequiredArgsConstructor
public class SearchQueryPoolController {

  private final SearchQueryPoolService poolService;

  @Operation(summary = "검색어 풀 생성", description = "각 카테고리별로 지정된 개수만큼 검색어를 생성하고 검증 후 풀에 저장")
  @PostMapping("/generate")
  public ResponseEntity<?> generateQueries(
      @RequestParam(defaultValue = "20") int queriesPerCategory) {
    int saved = poolService.generateAndSaveQueries(queriesPerCategory);
    return ResponseEntity.ok(
        Map.of(
            "queriesPerCategory", queriesPerCategory,
            "totalSaved", saved,
            "message", String.format("카테고리당 %d개씩 생성 요청, 총 %d개 저장됨", queriesPerCategory, saved)));
  }

  @Operation(summary = "검색어 풀 개수 조회")
  @GetMapping("/count")
  public ResponseEntity<?> getPoolCount() {
    long count = poolService.getPoolCount();
    return ResponseEntity.ok(
        Map.of("count", count, "message", String.format("검색어 풀에 %d개의 검색어가 있습니다", count)));
  }

  @Operation(summary = "전체 검색어 목록 조회")
  @GetMapping("/list")
  public ResponseEntity<?> getAllQueries() {
    return ResponseEntity.ok(poolService.getAllQueries());
  }
}
