package com.yjlee.search.index.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductDocumentFactoryTest {

  private ProductDocumentFactory factory;

  @BeforeEach
  void setUp() {
    factory = new ProductDocumentFactory();
  }

  @Test
  @DisplayName("ID 변환 - Long을 String으로")
  void should_convert_id_from_long_to_string() {
    Product product = createProduct("상품명", "스펙");
    product.setId(12345L);

    ProductDocument document = factory.create(product);

    assertThat(document.getId()).isEqualTo("12345");
  }

  @Test
  @DisplayName("브랜드명 추출 - 첫 번째 단어")
  void should_extract_brand_from_product_name() {
    Product product = createProduct("삼성 갤럭시 노트북", "스펙");

    ProductDocument document = factory.create(product);

    assertThat(document.getBrandName()).isEqualTo("삼성");
  }

  @Test
  @DisplayName("브랜드명 추출 - 특수문자 포함된 경우")
  void should_extract_brand_with_special_chars() {
    Product product = createProduct("[정품] 애플 아이폰", "스펙");

    ProductDocument document = factory.create(product);

    assertThat(document.getBrandName()).isEqualTo("[정품]");
  }

  @Test
  @DisplayName("등록월 포맷팅 - 점을 대시로 변환")
  void should_format_registered_month() {
    Product product = createProduct("상품명", "스펙");
    product.setRegMonth("2024.01");

    ProductDocument document = factory.create(product);

    assertThat(document.getRegisteredMonth()).isEqualTo("2024-01");
  }

  @Test
  @DisplayName("등록월이 없는 경우 빈 문자열")
  void should_handle_null_registered_month() {
    Product product = createProduct("상품명", "스펙");
    product.setRegMonth(null);

    ProductDocument document = factory.create(product);

    assertThat(document.getRegisteredMonth()).isEmpty();
  }

  @Test
  @DisplayName("가격 변환 - Long to Integer")
  void should_convert_price_to_integer() {
    Product product = createProduct("상품명", "스펙");
    product.setPrice(19900L);

    ProductDocument document = factory.create(product);

    assertThat(document.getPrice()).isEqualTo(19900);
  }

  @Test
  @DisplayName("가격이 없는 경우 null 처리")
  void should_handle_null_price() {
    Product product = createProduct("상품명", "스펙");
    product.setPrice(null);

    ProductDocument document = factory.create(product);

    assertThat(document.getPrice()).isNull();
  }

  @Test
  @DisplayName("리뷰 수가 없는 경우 0으로 설정")
  void should_set_review_count_to_zero_when_null() {
    Product product = createProduct("상품명", "스펙");
    product.setReviewCount(null);

    ProductDocument document = factory.create(product);

    assertThat(document.getReviewCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("null 스펙 처리 - 빈 문자열로 변환")
  void should_handle_null_specs() {
    Product product = createProduct("상품명", null);

    ProductDocument document = factory.create(product);

    assertThat(document.getSpecs()).isEmpty();
    assertThat(document.getSpecsRaw()).isNull();
  }

  @Test
  @DisplayName("null 카테고리 처리 - 빈 문자열로 변환")
  void should_handle_null_category() {
    Product product = createProduct("상품명", "스펙");
    product.setCategoryName(null);

    ProductDocument document = factory.create(product);

    assertThat(document.getCategory()).isEmpty();
    assertThat(document.getCategoryName()).isNull();
  }

  @Test
  @DisplayName("원본 데이터 보존 - nameRaw와 specsRaw")
  void should_preserve_raw_data() {
    Product product = createProduct("LG TV 100 ml", "크기: 55 inch");

    ProductDocument document = factory.create(product);

    assertThat(document.getNameRaw()).isEqualTo("LG TV 100 ml");
    assertThat(document.getSpecsRaw()).isEqualTo("크기: 55 inch");
  }

  @Test
  @DisplayName("전처리된 데이터 확인 - name과 specs")
  void should_apply_preprocessing_to_name_and_specs() {
    Product product = createProduct("코카콜라 500 ml", "무게: 100 g");

    ProductDocument document = factory.create(product);

    // 전처리가 적용되었는지만 확인 (구체적인 전처리는 TextPreprocessorTest에서 검증)
    assertThat(document.getName()).isNotEqualTo(product.getName());
    assertThat(document.getSpecs()).isNotEqualTo(product.getSpecs());
  }

  @Test
  @DisplayName("평점 그대로 유지 - BigDecimal")
  void should_preserve_rating() {
    Product product = createProduct("상품명", "스펙");
    BigDecimal rating = new BigDecimal("4.5");
    product.setRating(rating);

    ProductDocument document = factory.create(product);

    assertThat(document.getRating()).isEqualTo(rating);
  }

  @Test
  @DisplayName("썸네일 URL 그대로 유지")
  void should_preserve_thumbnail_url() {
    Product product = createProduct("상품명", "스펙");
    String url = "http://example.com/image.jpg";
    product.setThumbnailUrl(url);

    ProductDocument document = factory.create(product);

    assertThat(document.getThumbnailUrl()).isEqualTo(url);
  }

  @Test
  @DisplayName("카테고리 정보 처리")
  void should_process_category_info() {
    Product product = createProduct("상품명", "스펙");
    product.setCategoryName("전자제품/노트북&태블릿");

    ProductDocument document = factory.create(product);

    assertThat(document.getCategoryName()).isEqualTo("전자제품/노트북&태블릿");
    // 전처리된 카테고리
    assertThat(document.getCategory()).isNotNull();
  }

  @Test
  @DisplayName("모든 필드 정상 변환 - 통합 테스트")
  void should_convert_all_fields_correctly() {
    Product product = new Product();
    product.setId(999L);
    product.setName("삼성 노트북 Pro");
    product.setSpecs("CPU: i7, RAM: 16GB");
    product.setThumbnailUrl("http://example.com/notebook.jpg");
    product.setPrice(1500000L);
    product.setRating(new BigDecimal("4.8"));
    product.setReviewCount(250);
    product.setCategoryId(10L);
    product.setCategoryName("노트북");
    product.setRegMonth("2024.03");

    ProductDocument document = factory.create(product);

    assertThat(document.getId()).isEqualTo("999");
    assertThat(document.getNameRaw()).isEqualTo("삼성 노트북 Pro");
    assertThat(document.getBrandName()).isEqualTo("삼성");
    assertThat(document.getThumbnailUrl()).isEqualTo("http://example.com/notebook.jpg");
    assertThat(document.getPrice()).isEqualTo(1500000);
    assertThat(document.getRegisteredMonth()).isEqualTo("2024-03");
    assertThat(document.getRating()).isEqualTo(new BigDecimal("4.8"));
    assertThat(document.getReviewCount()).isEqualTo(250);
    assertThat(document.getCategoryName()).isEqualTo("노트북");
    assertThat(document.getSpecsRaw()).isEqualTo("CPU: i7, RAM: 16GB");
  }

  private Product createProduct(String name, String specs) {
    Product product = new Product();
    product.setId(1L);
    product.setName(name);
    product.setSpecs(specs);
    product.setThumbnailUrl("http://example.com/image.jpg");
    product.setPrice(10000L);
    product.setRating(new BigDecimal("4.5"));
    product.setReviewCount(100);
    product.setCategoryId(1L);
    product.setCategoryName("카테고리");
    product.setRegMonth("2024.01");
    return product;
  }
}
