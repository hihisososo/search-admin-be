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

        log.info(
            "배치 {} 완료: {}/{} 처리 (전체 진행률: {}/{})",
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
        "failed", failCount);
  }

  private int processBatch(List<Product> products) {
    List<String> nameTexts = new ArrayList<>();
    List<String> specsTexts = new ArrayList<>();
    List<Product> validProducts = new ArrayList<>();

    for (Product product : products) {
      if (productEmbeddingRepository.existsByProductId(product.getId())) {
        log.debug("상품 {} 임베딩 이미 존재, 스킵", product.getId());
        continue;
      }

      var document = documentFactory.create(product);
      String nameText = documentConverter.createNameText(document);
      String specsText = documentConverter.createSpecsText(document);

      nameTexts.add(nameText);
      specsTexts.add(specsText);
      validProducts.add(product);
    }

    if (nameTexts.isEmpty()) {
      return 0;
    }

    // 각 필드별로 임베딩 생성
    List<List<Float>> nameEmbeddings = embeddingGenerator.generateBulkEmbeddings(nameTexts);
    List<List<Float>> specsEmbeddings = embeddingGenerator.generateBulkEmbeddings(specsTexts);

    List<ProductEmbedding> embeddingsToSave = new ArrayList<>();
    for (int i = 0; i < validProducts.size(); i++) {
      Product product = validProducts.get(i);
      List<Float> nameEmbedding =
          i < nameEmbeddings.size() ? nameEmbeddings.get(i) : new ArrayList<>();
      List<Float> specsEmbedding =
          i < specsEmbeddings.size() ? specsEmbeddings.get(i) : new ArrayList<>();

      if (!nameEmbedding.isEmpty() && !specsEmbedding.isEmpty()) {
        ProductEmbedding productEmbedding = new ProductEmbedding();
        productEmbedding.setProductId(product.getId());
        productEmbedding.setNameText(nameTexts.get(i));
        productEmbedding.setNameVector(serializeVector(nameEmbedding));
        productEmbedding.setSpecsText(specsTexts.get(i));
        productEmbedding.setSpecsVector(serializeVector(specsEmbedding));
        embeddingsToSave.add(productEmbedding);
      }
    }

    productEmbeddingRepository.saveAll(embeddingsToSave);
    return embeddingsToSave.size();
  }

  @Transactional(readOnly = true)
  public Map<Long, Map<String, List<Float>>> getEmbeddingsByProductIds(List<Long> productIds) {
    List<ProductEmbedding> embeddings = productEmbeddingRepository.findByProductIdIn(productIds);

    return embeddings.stream()
        .collect(
            Collectors.toMap(
                ProductEmbedding::getProductId,
                embedding ->
                    Map.of(
                        "name", deserializeVector(embedding.getNameVector()),
                        "specs", deserializeVector(embedding.getSpecsVector()))));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getStatus() {
    long embeddingCount = productEmbeddingRepository.countEmbeddings();
    long productCount = productRepository.count();

    return Map.of(
        "totalProducts",
        productCount,
        "embeddingsGenerated",
        embeddingCount,
        "remaining",
        productCount - embeddingCount,
        "progress",
        processedCount.get() > 0
            ? Map.of(
                "current",
                processedCount.get(),
                "total",
                totalCount,
                "percentage",
                totalCount > 0 ? (processedCount.get() * 100.0 / totalCount) : 0)
            : Map.of("message", "No generation in progress"));
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

  @Transactional
  public void saveEmbeddings(
      List<Product> products,
      List<String> nameTexts,
      List<String> specsTexts,
      List<List<Float>> nameEmbeddings,
      List<List<Float>> specsEmbeddings) {
    if (products.size() != nameTexts.size()
        || products.size() != specsTexts.size()
        || products.size() != nameEmbeddings.size()
        || products.size() != specsEmbeddings.size()) {
      log.error(
          "입력 데이터 크기 불일치 - products: {}, nameTexts: {}, specsTexts: {}, nameEmbeddings: {}, specsEmbeddings: {}",
          products.size(),
          nameTexts.size(),
          specsTexts.size(),
          nameEmbeddings.size(),
          specsEmbeddings.size());
      return;
    }

    List<ProductEmbedding> embeddingsToSave = new ArrayList<>();

    for (int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      String nameText = nameTexts.get(i);
      String specsText = specsTexts.get(i);
      List<Float> nameEmbedding = nameEmbeddings.get(i);
      List<Float> specsEmbedding = specsEmbeddings.get(i);

      if (nameEmbedding != null
          && !nameEmbedding.isEmpty()
          && specsEmbedding != null
          && !specsEmbedding.isEmpty()) {
        // 이미 존재하는지 확인
        if (!productEmbeddingRepository.existsByProductId(product.getId())) {
          ProductEmbedding productEmbedding = new ProductEmbedding();
          productEmbedding.setProductId(product.getId());
          productEmbedding.setNameText(nameText);
          productEmbedding.setNameVector(serializeVector(nameEmbedding));
          productEmbedding.setSpecsText(specsText);
          productEmbedding.setSpecsVector(serializeVector(specsEmbedding));
          embeddingsToSave.add(productEmbedding);
        }
      }
    }

    if (!embeddingsToSave.isEmpty()) {
      productEmbeddingRepository.saveAll(embeddingsToSave);
      log.info("{}개의 임베딩을 DB에 저장했습니다", embeddingsToSave.size());
    }
  }
}
