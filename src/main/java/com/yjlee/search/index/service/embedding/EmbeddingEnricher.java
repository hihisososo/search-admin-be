package com.yjlee.search.index.service.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.model.ProductEmbedding;
import com.yjlee.search.index.repository.ProductEmbeddingRepository;
import com.yjlee.search.index.service.ProductDocumentConverter;
import com.yjlee.search.index.service.ProductDocumentFactory;
import com.yjlee.search.index.service.ProductEmbeddingGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingEnricher {

  private final ProductEmbeddingRepository embeddingRepository;
  private final ProductEmbeddingGenerator embeddingGenerator;
  private final ProductDocumentFactory documentFactory;
  private final ProductDocumentConverter documentConverter;

  @Value("${embedding.cache.max-size:1000}")
  private int maxCacheSize;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Map<Long, Map<String, List<Float>>> embeddingCache;

  @jakarta.annotation.PostConstruct
  public void init() {
    embeddingCache =
        Collections.synchronizedMap(
            new LinkedHashMap<Long, Map<String, List<Float>>>(maxCacheSize + 1, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(
                  Map.Entry<Long, Map<String, List<Float>>> eldest) {
                boolean shouldRemove = size() > maxCacheSize;
                if (shouldRemove) {
                  log.trace("LRU 캐시 제거: productId={}", eldest.getKey());
                }
                return shouldRemove;
              }
            });
    log.info("임베딩 캐시 초기화 - 최대 크기: {}", maxCacheSize);
  }

  public Map<Long, Map<String, List<Float>>> preloadEmbeddings(List<Long> productIds) {
    log.debug("{}개 상품의 임베딩 사전 로딩", productIds.size());

    // Check cache first
    Map<Long, Map<String, List<Float>>> cached = new HashMap<>();
    List<Long> uncachedIds = new ArrayList<>();

    for (Long id : productIds) {
      if (embeddingCache.containsKey(id)) {
        cached.put(id, embeddingCache.get(id));
      } else {
        uncachedIds.add(id);
      }
    }

    if (!uncachedIds.isEmpty()) {
      // Load from database
      List<ProductEmbedding> embeddings = embeddingRepository.findByProductIdIn(uncachedIds);
      Map<Long, Map<String, List<Float>>> loaded =
          embeddings.stream()
              .collect(Collectors.toMap(ProductEmbedding::getProductId, this::convertEmbedding));

      // Update cache
      embeddingCache.putAll(loaded);
      cached.putAll(loaded);
    }

    log.debug(
        "{}개 임베딩 로딩 완료 (캐시에서 {}개, 현재 캐시 크기: {})",
        cached.size(),
        productIds.size() - uncachedIds.size(),
        embeddingCache.size());

    return cached;
  }

  public List<ProductDocument> enrichProducts(
      List<Product> products, Map<Long, Map<String, List<Float>>> existingEmbeddings) {

    List<ProductDocument> documents = products.stream().map(documentFactory::create).toList();

    // Find products without embeddings
    Set<Long> existingIds = existingEmbeddings.keySet();
    List<Product> productsWithoutEmbedding =
        products.stream().filter(p -> !existingIds.contains(p.getId())).toList();

    Map<Long, Map<String, List<Float>>> newEmbeddings = new HashMap<>();

    if (!productsWithoutEmbedding.isEmpty()) {
      log.info("{}개 상품의 임베딩 생성 중", productsWithoutEmbedding.size());
      newEmbeddings = generateMissingEmbeddings(productsWithoutEmbedding, documents);
    }

    // Combine embeddings
    Map<Long, Map<String, List<Float>>> allEmbeddings = new HashMap<>(existingEmbeddings);
    allEmbeddings.putAll(newEmbeddings);

    // Apply embeddings to documents
    return applyEmbeddings(documents, products, allEmbeddings);
  }

  private Map<Long, Map<String, List<Float>>> generateMissingEmbeddings(
      List<Product> products, List<ProductDocument> documents) {

    Map<Long, ProductDocument> docMap =
        documents.stream().collect(Collectors.toMap(d -> Long.parseLong(d.getId()), d -> d));

    List<String> nameTexts = new ArrayList<>();
    List<String> specsTexts = new ArrayList<>();

    for (Product product : products) {
      ProductDocument doc = docMap.get(product.getId());
      if (doc != null) {
        nameTexts.add(documentConverter.createNameText(doc));
        specsTexts.add(documentConverter.createSpecsText(doc));
      }
    }

    List<List<Float>> nameEmbeddings = embeddingGenerator.generateBulkEmbeddings(nameTexts);
    List<List<Float>> specsEmbeddings = embeddingGenerator.generateBulkEmbeddings(specsTexts);

    Map<Long, Map<String, List<Float>>> newEmbeddings = new HashMap<>();

    for (int i = 0; i < products.size(); i++) {
      Long productId = products.get(i).getId();
      List<Float> nameEmbedding = i < nameEmbeddings.size() ? nameEmbeddings.get(i) : List.of();
      List<Float> specsEmbedding = i < specsEmbeddings.size() ? specsEmbeddings.get(i) : List.of();

      if (!nameEmbedding.isEmpty() && !specsEmbedding.isEmpty()) {
        newEmbeddings.put(
            productId,
            Map.of(
                "name", nameEmbedding,
                "specs", specsEmbedding));

        // Save to database
        saveEmbedding(
            productId, nameTexts.get(i), specsTexts.get(i), nameEmbedding, specsEmbedding);
      }
    }

    // Update cache
    embeddingCache.putAll(newEmbeddings);

    return newEmbeddings;
  }

  private List<ProductDocument> applyEmbeddings(
      List<ProductDocument> documents,
      List<Product> products,
      Map<Long, Map<String, List<Float>>> embeddings) {

    return documents.stream()
        .map(
            doc -> {
              Long productId = Long.parseLong(doc.getId());
              Map<String, List<Float>> embedding = embeddings.get(productId);

              if (embedding != null) {
                List<Float> nameVector = embedding.getOrDefault("name", List.of());
                List<Float> specsVector = embedding.getOrDefault("specs", List.of());
                return documentConverter.convert(doc, nameVector, specsVector);
              }

              return doc;
            })
        .toList();
  }

  private void saveEmbedding(
      Long productId,
      String nameText,
      String specsText,
      List<Float> nameVector,
      List<Float> specsVector) {

    try {
      if (!embeddingRepository.existsByProductId(productId)) {
        ProductEmbedding embedding = new ProductEmbedding();
        embedding.setProductId(productId);
        embedding.setNameText(nameText);
        embedding.setNameVector(serializeVector(nameVector));
        embedding.setSpecsText(specsText);
        embedding.setSpecsVector(serializeVector(specsVector));

        embeddingRepository.save(embedding);
        log.debug("상품 {} 임베딩 저장 완료", productId);
      }
    } catch (Exception e) {
      log.error("상품 {} 임베딩 저장 실패", productId, e);
    }
  }

  private Map<String, List<Float>> convertEmbedding(ProductEmbedding embedding) {
    return Map.of(
        "name", deserializeVector(embedding.getNameVector()),
        "specs", deserializeVector(embedding.getSpecsVector()));
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
    int previousSize = embeddingCache.size();
    embeddingCache.clear();
    log.info("임베딩 캐시 초기화 완료 (제거된 항목: {}개)", previousSize);
  }

  public int getCacheSize() {
    return embeddingCache.size();
  }
}
