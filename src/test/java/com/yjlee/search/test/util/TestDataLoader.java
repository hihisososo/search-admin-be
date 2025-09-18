package com.yjlee.search.test.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.model.Product;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

public class TestDataLoader {

  private static final String TEST_DATA_PATH = "test-data/products.json";
  private final ObjectMapper objectMapper;

  public TestDataLoader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 테스트 상품 데이터 로드 */
  public List<Product> loadProducts() {
    try (InputStream inputStream = new ClassPathResource(TEST_DATA_PATH).getInputStream()) {
      List<Map<String, Object>> productMaps =
          objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});

      return productMaps.stream().map(this::mapToProduct).collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load test products", e);
    }
  }

  /** 특정 개수만큼 상품 로드 */
  public List<Product> loadProducts(int limit) {
    return loadProducts().stream().limit(limit).collect(Collectors.toList());
  }

  /** Map을 Product 엔티티로 변환 */
  private Product mapToProduct(Map<String, Object> map) {
    Product product = new Product();

    // ID는 자동 생성되므로 설정하지 않음
    product.setName((String) map.get("name"));
    product.setThumbnailUrl((String) map.get("thumbnailUrl"));
    product.setPrice(((Number) map.get("price")).longValue());
    product.setSpecs((String) map.get("specs"));
    product.setRegMonth((String) map.get("regMonth"));

    if (map.get("rating") != null) {
      product.setRating(new java.math.BigDecimal(map.get("rating").toString()));
    }

    if (map.get("reviewCount") != null) {
      product.setReviewCount(((Number) map.get("reviewCount")).intValue());
    }

    product.setCategoryId(((Number) map.get("categoryId")).longValue());
    product.setCategoryName((String) map.get("categoryName"));

    return product;
  }
}
