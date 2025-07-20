package com.yjlee.search.index.controller;

import com.yjlee.search.index.dto.ProductIndexingResponse;
import com.yjlee.search.index.service.ProductIndexingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Product Indexing", description = "상품 Elasticsearch 색인 관리 API")
@RestController
@RequestMapping("/api/v1/products/indexing")
@RequiredArgsConstructor
public class ProductIndexingController {

  private final ProductIndexingService productIndexingService;

  @Operation(
      summary = "전체 상품 색인",
      description = "데이터베이스의 모든 상품을 Elasticsearch에 색인합니다. 상품 인덱스와 자동완성 인덱스 모두 색인됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @PostMapping("/all")
  public ResponseEntity<ProductIndexingResponse> indexAllProducts() {
    try {
      log.info("전체 상품 및 자동완성 색인 요청");
      int totalIndexed = productIndexingService.indexAllProducts();
      return ResponseEntity.ok(ProductIndexingResponse.success("전체 상품 및 자동완성 색인 완료", totalIndexed));
    } catch (Exception e) {
      log.error("전체 상품 및 자동완성 색인 실패", e);
      return ResponseEntity.internalServerError()
          .body(ProductIndexingResponse.error("전체 상품 및 자동완성 색인 실패: " + e.getMessage()));
    }
  }
}
