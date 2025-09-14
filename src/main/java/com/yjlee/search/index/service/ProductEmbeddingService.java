package com.yjlee.search.index.service;

import com.yjlee.search.embedding.service.EmbeddingService;
import com.yjlee.search.embedding.service.EmbeddingService.EmbeddingType;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.model.TextEmbedding;
import com.yjlee.search.index.repository.TextEmbeddingRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEmbeddingService {

  private final ProductDocumentFactory documentFactory;
  private final TextEmbeddingRepository embeddingRepository;
  private final EmbeddingService embeddingService;

  public List<ProductDocument> enrichWithEmbeddings(List<Product> products) {
    // 1. Document 생성
    List<ProductDocument> documents = products.stream().map(documentFactory::create).toList();

    // 2. 각 문서에서 텍스트 추출 및 해시 생성
    Map<String, String> textToHashMap = new HashMap<>();
    for (ProductDocument doc : documents) {
      String nameText = doc.getNameRaw();
      String specsText = doc.getSpecsRaw();

      // null이나 빈 문자열은 제외
      if (nameText != null && !nameText.isEmpty()) {
        textToHashMap.put(nameText, generateHash(nameText));
      }
      if (specsText != null && !specsText.isEmpty()) {
        textToHashMap.put(specsText, generateHash(specsText));
      }
    }

    // 3. DB에서 기존 임베딩 조회
    List<String> uniqueHashes = textToHashMap.values().stream().distinct().toList();

    Map<String, List<Float>> hashToVectorMap = loadExistingEmbeddings(uniqueHashes);

    // 4. 없는 임베딩만 생성하고 저장
    generateAndSaveMissingEmbeddings(textToHashMap, hashToVectorMap);

    // 5. Document에 임베딩 직접 적용 (재생성하지 않음)
    applyEmbeddingsToDocuments(documents, textToHashMap, hashToVectorMap);
    return documents;
  }

  private Map<String, List<Float>> loadExistingEmbeddings(List<String> hashes) {
    List<TextEmbedding> existingEmbeddings = embeddingRepository.findByHashIn(hashes);

    return existingEmbeddings.stream()
        .collect(
            Collectors.toMap(
                TextEmbedding::getHash, embedding -> deserializeVector(embedding.getVector())));
  }

  private void generateAndSaveMissingEmbeddings(
      Map<String, String> textToHashMap, Map<String, List<Float>> hashToVectorMap) {

    // 생성이 필요한 텍스트 찾기 (중복 Map 제거)
    List<String> textsToGenerate =
        textToHashMap.entrySet().stream()
            .filter(entry -> !hashToVectorMap.containsKey(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();

    if (textsToGenerate.isEmpty()) {
      return;
    }

    log.info("{}개 텍스트의 임베딩 생성 중", textsToGenerate.size());

    // 벌크로 임베딩 생성
    List<float[]> embeddings =
        embeddingService.getBulkEmbeddings(textsToGenerate, EmbeddingType.DOCUMENT);

    // DB에 저장
    List<TextEmbedding> toSave = new ArrayList<>();
    for (int i = 0; i < textsToGenerate.size(); i++) {
      String text = textsToGenerate.get(i);
      String hash = textToHashMap.get(text);

      if (i < embeddings.size()) {
        List<Float> vector = convertToFloatList(embeddings.get(i));

        if (!vector.isEmpty()) {
          TextEmbedding embedding = new TextEmbedding();
          embedding.setText(text);
          embedding.setHash(hash);
          embedding.setVector(serializeVector(vector));
          toSave.add(embedding);

          hashToVectorMap.put(hash, vector);
        }
      }
    }

    // 배치로 저장
    if (!toSave.isEmpty()) {
      saveEmbeddingsBatch(toSave);
      log.info("{}개 텍스트 임베딩 저장 완료", toSave.size());
    }
  }

  public void saveEmbeddingsBatch(List<TextEmbedding> embeddings) {
    for (TextEmbedding embedding : embeddings) {
      saveEmbeddingIndividual(embedding);
    }
  }

  @Transactional
  public void saveEmbeddingIndividual(TextEmbedding embedding) {
    try {
      embeddingRepository.save(embedding);
    } catch (DataIntegrityViolationException e) {
      log.debug("임베딩 이미 존재: {}", embedding.getHash());
    }
  }

  private void applyEmbeddingsToDocuments(
      List<ProductDocument> documents,
      Map<String, String> textToHashMap,
      Map<String, List<Float>> hashToVectorMap) {

    // ProductDocument 직접 수정 (재생성하지 않음)
    documents.forEach(
        doc -> {
          String nameText = doc.getNameRaw();
          String specsText = doc.getSpecsRaw();
          String nameHash = textToHashMap.get(nameText);
          String specsHash = textToHashMap.get(specsText);

          // null 체크 후 벡터 설정
          if (nameHash != null && !nameHash.isEmpty()) {
            doc.setNameVector(hashToVectorMap.get(nameHash));
          }
          if (specsHash != null && !specsHash.isEmpty()) {
            doc.setSpecsVector(hashToVectorMap.get(specsHash));
          }
        });
  }

  private String generateHash(String text) {
    if (text == null || text.isEmpty()) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();

      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }

      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 알고리즘을 찾을 수 없습니다", e);
      return "";
    }
  }

  private List<Float> convertToFloatList(float[] array) {
    // Stream API 활용 (IntStream 우회)
    List<Float> list = new ArrayList<>(array.length);
    for (float f : array) {
      list.add(f);
    }
    return list;
  }

  private byte[] serializeVector(List<Float> vector) {
    try {
      // float[] → byte[]
      ByteBuffer buffer = ByteBuffer.allocate(vector.size() * 4);
      for (Float f : vector) {
        buffer.putFloat(f != null ? f : 0.0f);
      }

      // GZIP 압축
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
        gzip.write(buffer.array());
      }
      return baos.toByteArray();
    } catch (IOException e) {
      log.error("벡터 압축 실패", e);
      throw new RuntimeException("벡터 압축 실패", e);
    }
  }

  private List<Float> deserializeVector(byte[] compressedBytes) {
    if (compressedBytes == null || compressedBytes.length == 0) {
      return new ArrayList<>();
    }

    try {
      // GZIP 압축 해제
      ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
      byte[] bytes;
      try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
        bytes = gzip.readAllBytes();
      }

      // byte[] → float[]
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      List<Float> vector = new ArrayList<>();
      while (buffer.hasRemaining()) {
        vector.add(buffer.getFloat());
      }
      return vector;
    } catch (IOException e) {
      log.error("벡터 압축 해제 실패", e);
      return new ArrayList<>();
    }
  }
}
