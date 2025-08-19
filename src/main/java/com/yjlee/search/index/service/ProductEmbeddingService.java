package com.yjlee.search.index.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.model.ProductEmbedding;
import com.yjlee.search.index.repository.ProductEmbeddingRepository;
import com.yjlee.search.index.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEmbeddingService {
  
  private final ProductRepository productRepository;
  private final ProductEmbeddingRepository productEmbeddingRepository;
  private final ProductDocumentFactory documentFactory;
  private final ProductDocumentConverter documentConverter;
  private final ProductEmbeddingGenerator embeddingGenerator;
  private final ObjectMapper objectMapper = new ObjectMapper();
  
  private static final int BATCH_SIZE = 100;
  private AtomicInteger processedCount = new AtomicInteger(0);
  private long totalCount = 0;
  
  @Transactional
  public Map<String, Object> generateAllEmbeddings() {
    log.info("전체 상품 임베딩 생성 시작");
    
    processedCount.set(0);
    totalCount = productRepository.count();
    
    int successCount = 0;
    int failCount = 0;
    int pageNumber = 0;
    
    while (true) {
      Page<Product> productPage = productRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));
      if (productPage.isEmpty()) break;
      
      try {
        int batchResult = processBatch(productPage.getContent());
        successCount += batchResult;
        processedCount.addAndGet(productPage.getNumberOfElements());
        
        log.info("배치 {} 완료: {}/{} 처리 (전체 진행률: {}/{})", 
            pageNumber + 1, 
            batchResult, 
            productPage.getNumberOfElements(),
            processedCount.get(), 
            totalCount);
        
      } catch (Exception e) {
        log.error("배치 {} 처리 중 오류", pageNumber + 1, e);
        failCount += productPage.getNumberOfElements();
      }
      
      pageNumber++;
    }
    
    log.info("전체 상품 임베딩 생성 완료 - 성공: {}, 실패: {}", successCount, failCount);
    
    return Map.of(
        "total", totalCount,
        "success", successCount,
        "failed", failCount
    );
  }
  
  private int processBatch(List<Product> products) {
    List<String> texts = new ArrayList<>();
    List<Product> validProducts = new ArrayList<>();
    
    for (Product product : products) {
      if (productEmbeddingRepository.existsByProductId(product.getId())) {
        log.debug("상품 {} 임베딩 이미 존재, 스킵", product.getId());
        continue;
      }
      
      var document = documentFactory.create(product);
      String searchableText = documentConverter.createSearchableText(document);
      texts.add(searchableText);
      validProducts.add(product);
    }
    
    if (texts.isEmpty()) {
      return 0;
    }
    
    List<List<Float>> embeddings = embeddingGenerator.generateBulkEmbeddings(texts);
    
    List<ProductEmbedding> embeddingsToSave = new ArrayList<>();
    for (int i = 0; i < validProducts.size(); i++) {
      Product product = validProducts.get(i);
      List<Float> embedding = i < embeddings.size() ? embeddings.get(i) : new ArrayList<>();
      
      if (!embedding.isEmpty()) {
        ProductEmbedding productEmbedding = new ProductEmbedding();
        productEmbedding.setProductId(product.getId());
        productEmbedding.setEmbeddingText(texts.get(i));
        productEmbedding.setEmbeddingVector(serializeVector(embedding));
        embeddingsToSave.add(productEmbedding);
      }
    }
    
    productEmbeddingRepository.saveAll(embeddingsToSave);
    return embeddingsToSave.size();
  }
  
  @Transactional(readOnly = true)
  public Map<Long, List<Float>> getEmbeddingsByProductIds(List<Long> productIds) {
    List<ProductEmbedding> embeddings = productEmbeddingRepository.findByProductIdIn(productIds);
    
    return embeddings.stream()
        .collect(Collectors.toMap(
            ProductEmbedding::getProductId,
            embedding -> deserializeVector(embedding.getEmbeddingVector())
        ));
  }
  
  @Transactional(readOnly = true)
  public Map<String, Object> getStatus() {
    long embeddingCount = productEmbeddingRepository.countEmbeddings();
    long productCount = productRepository.count();
    
    return Map.of(
        "totalProducts", productCount,
        "embeddingsGenerated", embeddingCount,
        "remaining", productCount - embeddingCount,
        "progress", processedCount.get() > 0 ? 
            Map.of(
                "current", processedCount.get(),
                "total", totalCount,
                "percentage", totalCount > 0 ? (processedCount.get() * 100.0 / totalCount) : 0
            ) : Map.of("message", "No generation in progress")
    );
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
      return objectMapper.readValue(vectorString, 
          objectMapper.getTypeFactory().constructCollectionType(List.class, Float.class));
    } catch (JsonProcessingException e) {
      log.error("벡터 역직렬화 실패", e);
      return new ArrayList<>();
    }
  }
}