package com.yjlee.search.index.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.embedding.service.EmbeddingService;
import com.yjlee.search.embedding.service.EmbeddingService.EmbeddingType;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.model.TextEmbedding;
import com.yjlee.search.index.repository.TextEmbeddingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEmbeddingService {

  private final ProductDocumentFactory documentFactory;
  private final TextEmbeddingRepository embeddingRepository;
  private final EmbeddingService embeddingService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional
  public List<ProductDocument> enrichWithEmbeddings(List<Product> products) {
    // 1. Document 생성
    List<ProductDocument> documents = products.stream()
        .map(documentFactory::create)
        .toList();

    // 2. 각 문서에서 텍스트 추출 및 해시 생성
    Map<String, String> textToHashMap = new HashMap<>();
    for (ProductDocument doc : documents) {
      String nameText = createNameText(doc);
      String specsText = createSpecsText(doc);
      
      textToHashMap.put(nameText, generateHash(nameText));
      textToHashMap.put(specsText, generateHash(specsText));
    }

    // 3. DB에서 기존 임베딩 조회
    List<String> uniqueHashes = textToHashMap.values().stream()
        .distinct()
        .toList();
    
    Map<String, List<Float>> hashToVectorMap = loadExistingEmbeddings(uniqueHashes);

    // 4. 없는 임베딩만 생성하고 저장
    generateAndSaveMissingEmbeddings(textToHashMap, hashToVectorMap);

    // 5. Document에 임베딩 적용
    return applyEmbeddingsToDocuments(documents, textToHashMap, hashToVectorMap);
  }

  private Map<String, List<Float>> loadExistingEmbeddings(List<String> hashes) {
    List<TextEmbedding> existingEmbeddings = embeddingRepository.findByHashIn(hashes);
    
    return existingEmbeddings.stream()
        .collect(Collectors.toMap(
            TextEmbedding::getHash,
            embedding -> deserializeVector(embedding.getVector())
        ));
  }

  private void generateAndSaveMissingEmbeddings(
      Map<String, String> textToHashMap,
      Map<String, List<Float>> hashToVectorMap) {
    
    // 생성이 필요한 텍스트 찾기
    List<String> textsToGenerate = new ArrayList<>();
    Map<String, String> textToGenerateMap = new HashMap<>();
    
    for (Map.Entry<String, String> entry : textToHashMap.entrySet()) {
      String text = entry.getKey();
      String hash = entry.getValue();
      
      if (!hashToVectorMap.containsKey(hash)) {
        textsToGenerate.add(text);
        textToGenerateMap.put(text, hash);
      }
    }

    if (textsToGenerate.isEmpty()) {
      return;
    }

    log.info("{}개 텍스트의 임베딩 생성 중", textsToGenerate.size());
    
    // 벌크로 임베딩 생성
    List<float[]> embeddings = embeddingService.getBulkEmbeddings(
        textsToGenerate, 
        EmbeddingType.DOCUMENT
    );

    // DB에 저장
    List<TextEmbedding> toSave = new ArrayList<>();
    for (int i = 0; i < textsToGenerate.size(); i++) {
      String text = textsToGenerate.get(i);
      String hash = textToGenerateMap.get(text);
      
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

  private void saveEmbeddingsBatch(List<TextEmbedding> embeddings) {
    for (TextEmbedding embedding : embeddings) {
      try {
        if (!embeddingRepository.existsByHash(embedding.getHash())) {
          embeddingRepository.save(embedding);
        }
      } catch (Exception e) {
        log.debug("임베딩 저장 스킵 (이미 존재): {}", embedding.getHash());
      }
    }
  }

  private List<ProductDocument> applyEmbeddingsToDocuments(
      List<ProductDocument> documents,
      Map<String, String> textToHashMap,
      Map<String, List<Float>> hashToVectorMap) {
    
    return documents.stream()
        .map(doc -> {
          String nameText = createNameText(doc);
          String specsText = createSpecsText(doc);
          
          String nameHash = textToHashMap.get(nameText);
          String specsHash = textToHashMap.get(specsText);
          
          List<Float> nameVector = hashToVectorMap.getOrDefault(nameHash, null);
          List<Float> specsVector = hashToVectorMap.getOrDefault(specsHash, null);
          
          return ProductDocument.builder()
              .id(doc.getId())
              .name(doc.getName())
              .nameRaw(doc.getNameRaw())
              .brandName(doc.getBrandName())
              .thumbnailUrl(doc.getThumbnailUrl())
              .price(doc.getPrice())
              .registeredMonth(doc.getRegisteredMonth())
              .rating(doc.getRating())
              .reviewCount(doc.getReviewCount())
              .categoryName(doc.getCategoryName())
              .category(doc.getCategory())
              .specs(doc.getSpecs())
              .specsRaw(doc.getSpecsRaw())
              .nameVector(nameVector)
              .specsVector(specsVector)
              .build();
        })
        .toList();
  }

  private String createNameText(ProductDocument document) {
    StringBuilder text = new StringBuilder();
    
    if (document.getNameRaw() != null && !document.getNameRaw().trim().isEmpty()) {
      text.append(document.getNameRaw().trim());
    }
    
    if (document.getBrandName() != null && !document.getBrandName().trim().isEmpty()) {
      if (text.length() > 0) text.append(" ");
      text.append(document.getBrandName().trim());
    }
    
    return text.toString();
  }

  private String createSpecsText(ProductDocument document) {
    StringBuilder text = new StringBuilder();
    
    if (document.getSpecsRaw() != null && !document.getSpecsRaw().trim().isEmpty()) {
      text.append(document.getSpecsRaw().trim());
    }
    
    if (document.getCategoryName() != null && !document.getCategoryName().trim().isEmpty()) {
      if (text.length() > 0) text.append(" ");
      text.append(document.getCategoryName().trim());
    }
    
    return text.toString();
  }

  private String generateHash(String text) {
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
    List<Float> list = new ArrayList<>(array.length);
    for (float f : array) {
      list.add(f);
    }
    return list;
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
          objectMapper.getTypeFactory().constructCollectionType(List.class, Float.class)
      );
    } catch (JsonProcessingException e) {
      log.error("벡터 역직렬화 실패", e);
      return new ArrayList<>();
    }
  }
}