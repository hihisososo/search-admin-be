package com.yjlee.search.embedding.controller;

import com.yjlee.search.embedding.service.EmbeddingService;
import com.yjlee.search.embedding.service.EmbeddingService.EmbeddingType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/embeddings")
@RequiredArgsConstructor
@Tag(name = "Embedding", description = "임베딩 관리 API")
public class EmbeddingController {

  private final EmbeddingService embeddingService;

  @PostMapping("/generate")
  @Operation(summary = "임베딩 생성", description = "텍스트로부터 임베딩 벡터를 생성합니다")
  public ResponseEntity<Map<String, Object>> generateEmbedding(
      @RequestParam String text, @RequestParam(defaultValue = "DOCUMENT") EmbeddingType type) {

    log.info("🚀 임베딩 생성 요청: type={}, text length={}", type, text.length());

    float[] embedding = embeddingService.getEmbedding(text, type);

    Map<String, Object> response = new HashMap<>();
    response.put("model", embeddingService.getModelName());
    response.put("dimension", embedding.length);
    response.put("type", type.toString());
    response.put("text_preview", text.length() > 100 ? text.substring(0, 100) + "..." : text);
    response.put("embedding_preview", getPreview(embedding, 10));

    return ResponseEntity.ok(response);
  }

  @PostMapping("/bulk")
  @Operation(summary = "벌크 임베딩 생성", description = "여러 텍스트로부터 임베딩 벡터를 일괄 생성합니다")
  public ResponseEntity<Map<String, Object>> generateBulkEmbeddings(
      @RequestBody List<String> texts,
      @RequestParam(defaultValue = "DOCUMENT") EmbeddingType type) {

    log.info("🚀 벌크 임베딩 생성 요청: type={}, texts count={}", type, texts.size());

    List<float[]> embeddings = embeddingService.getBulkEmbeddings(texts, type);

    Map<String, Object> response = new HashMap<>();
    response.put("model", embeddingService.getModelName());
    response.put("type", type.toString());
    response.put("count", embeddings.size());
    response.put("dimension", embeddings.isEmpty() ? 0 : embeddings.get(0).length);

    return ResponseEntity.ok(response);
  }

  @GetMapping("/model/info")
  @Operation(summary = "모델 정보 조회", description = "현재 사용 중인 임베딩 모델 정보를 조회합니다")
  public ResponseEntity<Map<String, Object>> getModelInfo() {
    Map<String, Object> response = new HashMap<>();
    response.put("model_name", embeddingService.getModelName());
    response.put("dimension", embeddingService.getEmbeddingDimension());

    return ResponseEntity.ok(response);
  }

  private float[] getPreview(float[] embedding, int size) {
    if (embedding.length <= size) {
      return embedding;
    }
    float[] preview = new float[size];
    System.arraycopy(embedding, 0, preview, 0, size);
    return preview;
  }
}
