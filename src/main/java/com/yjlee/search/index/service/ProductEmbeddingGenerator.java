package com.yjlee.search.index.service;

import com.yjlee.search.embedding.service.EmbeddingService;
import com.yjlee.search.embedding.service.EmbeddingService.EmbeddingType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEmbeddingGenerator {

  private final EmbeddingService embeddingService;

  public List<List<Float>> generateBulkEmbeddings(List<String> texts) {
    try {
      log.info("🔄 벌크 임베딩 생성 시작: {}개", texts.size());

      List<float[]> embeddings = embeddingService.getBulkEmbeddings(texts, EmbeddingType.DOCUMENT);
      List<List<Float>> result = new ArrayList<>();

      for (float[] embedding : embeddings) {
        result.add(convertToFloatList(embedding));
      }

      log.info("✅ 벌크 임베딩 생성 완료: {}개", result.size());
      return result;

    } catch (Exception e) {
      log.error("❌ 벌크 임베딩 생성 실패, 빈 임베딩 반환", e);

      List<List<Float>> emptyEmbeddings = new ArrayList<>();
      for (int i = 0; i < texts.size(); i++) {
        emptyEmbeddings.add(new ArrayList<>());
      }
      return emptyEmbeddings;
    }
  }

  private List<Float> convertToFloatList(float[] embedding) {
    List<Float> result = new ArrayList<>(embedding.length);
    for (float f : embedding) {
      result.add(f);
    }
    return result;
  }
}
