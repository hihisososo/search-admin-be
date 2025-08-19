package com.yjlee.search.index.controller;

import com.yjlee.search.index.service.ProductEmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/embeddings")
@RequiredArgsConstructor
@Tag(name = "Product Embeddings", description = "상품 임베딩 벡터 관리 API")
public class ProductEmbeddingController {

  private final ProductEmbeddingService productEmbeddingService;

  @PostMapping("/generate")
  @Operation(summary = "전체 상품 임베딩 생성", description = "모든 상품에 대한 임베딩 벡터를 생성하여 DB에 저장합니다")
  public ResponseEntity<Map<String, Object>> generateAllEmbeddings() {
    log.info("전체 상품 임베딩 생성 요청");

    Map<String, Object> result = productEmbeddingService.generateAllEmbeddings();

    return ResponseEntity.ok(result);
  }

  @GetMapping("/status")
  @Operation(summary = "임베딩 생성 상태 확인", description = "임베딩 생성 진행 상황 및 통계를 조회합니다")
  public ResponseEntity<Map<String, Object>> getEmbeddingStatus() {
    Map<String, Object> status = productEmbeddingService.getStatus();
    return ResponseEntity.ok(status);
  }
}
