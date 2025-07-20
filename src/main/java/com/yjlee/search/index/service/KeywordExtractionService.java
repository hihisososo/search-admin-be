package com.yjlee.search.index.service;

import com.yjlee.search.index.dto.KeywordExtractionResponse;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.index.util.BrandExtractor;
import com.yjlee.search.index.util.KeywordExtractor;
import com.yjlee.search.index.util.ModelExtractor;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExtractionService {

  private final ProductRepository productRepository;

  public List<KeywordExtractionResponse> extractKeywordsFromProducts(int limit) {
    log.info("상품명에서 키워드 추출 시작 - 제한: {} 건", limit);

    List<Product> products = productRepository.findAll(PageRequest.of(0, limit)).getContent();

    List<KeywordExtractionResponse> results =
        products.stream().map(this::extractKeywordsFromProduct).collect(Collectors.toList());

    log.info("키워드 추출 완료 - 처리된 상품: {} 건", results.size());
    return results;
  }

  public KeywordExtractionResponse extractKeywordsFromProductName(String productName) {
    log.info("상품명에서 키워드 추출: {}", productName);

    return KeywordExtractionResponse.builder()
        .productName(productName)
        .brand(BrandExtractor.extractBrand(productName))
        .models(ModelExtractor.extractModels(productName))
        .colors(KeywordExtractor.extractColors(productName))
        .specifications(KeywordExtractor.extractSpecifications(productName))
        .features(KeywordExtractor.extractFeatures(productName))
        .ngrams(KeywordExtractor.extractNgrams(productName, 1, 3))
        .allKeywords(KeywordExtractor.extractAllKeywords(productName))
        .build();
  }

  private KeywordExtractionResponse extractKeywordsFromProduct(Product product) {
    String productName = product.getName();

    return KeywordExtractionResponse.builder()
        .productName(productName)
        .brand(BrandExtractor.extractBrand(productName))
        .models(ModelExtractor.extractModels(productName))
        .colors(KeywordExtractor.extractColors(productName))
        .specifications(KeywordExtractor.extractSpecifications(productName))
        .features(KeywordExtractor.extractFeatures(productName))
        .ngrams(KeywordExtractor.extractNgrams(productName, 1, 3))
        .allKeywords(KeywordExtractor.extractAllKeywords(productName))
        .build();
  }

  public List<String> getRandomProductNames(int count) {
    log.info("랜덤 상품명 {} 건 조회", count);

    List<Product> products = productRepository.findAll(PageRequest.of(0, count * 2)).getContent();

    return products.stream()
        .map(Product::getName)
        .distinct()
        .limit(count)
        .collect(Collectors.toList());
  }
}
