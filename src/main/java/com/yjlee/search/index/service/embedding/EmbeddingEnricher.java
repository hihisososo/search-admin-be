package com.yjlee.search.index.service.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.model.TextEmbedding;
import com.yjlee.search.index.repository.TextEmbeddingRepository;
import com.yjlee.search.index.service.ProductDocumentConverter;
import com.yjlee.search.index.service.ProductDocumentFactory;
import com.yjlee.search.index.service.ProductEmbeddingGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingEnricher {

  private final TextEmbeddingRepository embeddingRepository;
  private final ProductEmbeddingGenerator embeddingGenerator;
  private final ProductDocumentFactory documentFactory;
  private final ProductDocumentConverter documentConverter;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public Map<Long, Map<String, List<Float>>> preloadEmbeddings(List<Long> productIds) {
    return new HashMap<>();
  }

  public List<ProductDocument> enrichProducts(
      List<Product> products, Map<Long, Map<String, List<Float>>> legacyEmbeddings) {

    List<ProductDocument> documents = products.stream().map(documentFactory::create).toList();

    Map<String, String> textToHashMap = new HashMap<>();
    List<String> allTexts = new ArrayList<>();
    List<String> allHashes = new ArrayList<>();

    for (ProductDocument doc : documents) {
      String nameText = documentConverter.createNameText(doc);
      String specsText = documentConverter.createSpecsText(doc);

      String nameHash = generateHash(nameText);
      String specsHash = generateHash(specsText);

      textToHashMap.put(nameText, nameHash);
      textToHashMap.put(specsText, specsHash);

      allTexts.add(nameText);
      allTexts.add(specsText);
      allHashes.add(nameHash);
      allHashes.add(specsHash);
    }

    List<String> uniqueHashes = allHashes.stream().distinct().toList();
    List<TextEmbedding> existingEmbeddings = embeddingRepository.findByHashIn(uniqueHashes);
    Map<String, List<Float>> hashToVectorMap =
        existingEmbeddings.stream()
            .collect(
                Collectors.toMap(
                    TextEmbedding::getHash, embedding -> deserializeVector(embedding.getVector())));

    Map<String, String> textToGenerateMap = new HashMap<>();
    List<String> textsToGenerate = new ArrayList<>();

    for (Map.Entry<String, String> entry : textToHashMap.entrySet()) {
      String text = entry.getKey();
      String hash = entry.getValue();
      if (!hashToVectorMap.containsKey(hash)) {
        textToGenerateMap.put(text, hash);
        textsToGenerate.add(text);
      }
    }

    if (!textsToGenerate.isEmpty()) {
      log.info("{}개 텍스트의 임베딩 생성 중", textsToGenerate.size());
      List<List<Float>> newEmbeddings = embeddingGenerator.generateBulkEmbeddings(textsToGenerate);

      List<TextEmbedding> toSave = new ArrayList<>();
      for (int i = 0; i < textsToGenerate.size(); i++) {
        String text = textsToGenerate.get(i);
        String hash = textToGenerateMap.get(text);
        List<Float> vector = i < newEmbeddings.size() ? newEmbeddings.get(i) : List.of();

        if (!vector.isEmpty()) {
          TextEmbedding embedding = new TextEmbedding();
          embedding.setText(text);
          embedding.setHash(hash);
          embedding.setVector(serializeVector(vector));
          toSave.add(embedding);

          hashToVectorMap.put(hash, vector);
        }
      }

      if (!toSave.isEmpty()) {
        int savedCount = 0;
        for (TextEmbedding embedding : toSave) {
          try {
            // 먼저 존재하는지 확인
            if (!embeddingRepository.existsByHash(embedding.getHash())) {
              embeddingRepository.save(embedding);
              savedCount++;
            }
          } catch (Exception e) {
            // 중복 키 에러 등은 무시 (이미 다른 스레드가 저장했을 수 있음)
            log.debug("임베딩 저장 스킵 (이미 존재): {}", embedding.getHash());
          }
        }
        if (savedCount > 0) {
          log.info("{}개 텍스트 임베딩 저장 완료", savedCount);
        }
      }
    }

    return applyEmbeddings(documents, textToHashMap, hashToVectorMap);
  }

  private List<ProductDocument> applyEmbeddings(
      List<ProductDocument> documents,
      Map<String, String> textToHashMap,
      Map<String, List<Float>> hashToVectorMap) {

    return documents.stream()
        .map(
            doc -> {
              String nameText = documentConverter.createNameText(doc);
              String specsText = documentConverter.createSpecsText(doc);

              String nameHash = textToHashMap.get(nameText);
              String specsHash = textToHashMap.get(specsText);

              List<Float> nameVector = hashToVectorMap.getOrDefault(nameHash, List.of());
              List<Float> specsVector = hashToVectorMap.getOrDefault(specsHash, List.of());

              return documentConverter.convert(doc, nameVector, specsVector);
            })
        .toList();
  }

  private String generateHash(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 알고리즘을 찾을 수 없습니다", e);
      return "";
    }
  }

  private String serializeVector(List<Float> vector) {
    try {
      return objectMapper.writeValueAsString(vector);
    } catch (JsonProcessingException e) {
      log.error("벡터 직렬화 실패", e);
      return "[]";
    }
  }

  private List<Float> deserializeVector(String vectorString) {
    try {
      return objectMapper.readValue(
          vectorString,
          objectMapper.getTypeFactory().constructCollectionType(List.class, Float.class));
    } catch (JsonProcessingException e) {
      log.error("벡터 역직렬화 실패", e);
      return new ArrayList<>();
    }
  }

  public void clearCache() {
    log.info("캐시 기능이 제거되었습니다");
  }

  public int getCacheSize() {
    return 0;
  }
}
