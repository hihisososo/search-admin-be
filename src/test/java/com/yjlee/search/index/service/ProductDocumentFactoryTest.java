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
  @DisplayName("상품명 전처리 - 단위 정규화 및 특수문자 제거")
  void should_preprocess_product_name() {
    Product product = createProduct("코카콜라 500 ml @특가!", "스펙");

    ProductDocument document = factory.create(product);

    assertThat(document.getName()).isEqualTo("코카콜라 500ml 특가");
    assertThat(document.getNameRaw()).isEqualTo("코카콜라 500 ml @특가!");
  }

  @Test
  @DisplayName("브랜드명 추출 - 첫 번째 단어")
  void should_extract_brand_from_product_name() {
    Product product = createProduct("삼성 갤럭시 노트북", "스펙");

    ProductDocument document = factory.create(product);

    assertThat(document.getBrandName()).isEqualTo("삼성");
  }

  @Test
  @DisplayName("카테고리 전처리")
  void should_preprocess_category() {
    Product product = createProduct("상품명", "스펙");
    product.setCategoryName("전자제품/노트북&태블릿");

    ProductDocument document = factory.create(product);

    assertThat(document.getCategory()).isEqualTo("전자제품/노트북&태블릿");
    assertThat(document.getCategoryName()).isEqualTo("전자제품/노트북&태블릿");
  }

  @Test
  @DisplayName("스펙 전처리 - 단위 정규화")
  void should_preprocess_specs() {
    Product product = createProduct("상품명", "무게 1.5 kg, 크기 30 cm");

    ProductDocument document = factory.create(product);

    assertThat(document.getSpecs()).isEqualTo("무게 1.5kg 크기 30cm");
    assertThat(document.getSpecsRaw()).isEqualTo("무게 1.5 kg, 크기 30 cm");
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
  @DisplayName("리뷰 수가 없는 경우 0으로 설정")
  void should_set_review_count_to_zero_when_null() {
    Product product = createProduct("상품명", "스펙");
    product.setReviewCount(null);

    ProductDocument document = factory.create(product);

    assertThat(document.getReviewCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("복합 전처리 - 천단위 구분자 + 단위 정규화")
  void should_handle_complex_preprocessing() {
    Product product = createProduct("LG 올레드 TV 19900원", "화면 크기: 55 inch, 무게: 15.5 kg");

    ProductDocument document = factory.create(product);

    assertThat(document.getName()).isEqualTo("lg 올레드 tv 19900원");
    assertThat(document.getBrandName()).isEqualTo("LG");
    assertThat(document.getSpecs()).isEqualTo("화면 크기 55inch 무게 15.5kg");
  }

  @Test
  @DisplayName("크기 표시 분해 - x를 공백으로")
  void should_expand_size_notation() {
    Product product = createProduct("상자 10x20x30cm", "크기: 100x200mm");

    ProductDocument document = factory.create(product);

    assertThat(document.getName()).isEqualTo("상자 10 x 20 x 30cm");
    assertThat(document.getSpecs()).isEqualTo("크기 100 x 200mm");
  }

  @Test
  @DisplayName("연속 단위 분해")
  void should_separate_consecutive_units() {
    Product product = createProduct("제품 10cm20kg30개", "10kg20cm30ml");

    ProductDocument document = factory.create(product);

    assertThat(document.getName()).isEqualTo("제품 10cm 20kg 30개");
    assertThat(document.getSpecs()).isEqualTo("10kg 20cm 30ml");
  }

  @Test
  @DisplayName("null 스펙 처리")
  void should_handle_null_specs() {
    Product product = createProduct("상품명", null);

    ProductDocument document = factory.create(product);

    assertThat(document.getSpecs()).isEmpty();
    assertThat(document.getSpecsRaw()).isNull();
  }

  @Test
  @DisplayName("null 카테고리 처리")
  void should_handle_null_category() {
    Product product = createProduct("상품명", "스펙");
    product.setCategoryName(null);

    ProductDocument document = factory.create(product);

    assertThat(document.getCategory()).isEmpty();
    assertThat(document.getCategoryName()).isNull();
  }

  @Test
  @DisplayName("한글 단위 정규화")
  void should_normalize_korean_units() {
    Product product = createProduct("삼양라면 5 개입", "중량: 120 그램, 조리시간: 4 분");

    ProductDocument document = factory.create(product);

    assertThat(document.getName()).isEqualTo("삼양라면 5개입");
    assertThat(document.getSpecs()).isEqualTo("중량 120그램 조리시간 4분");
  }

  @Test
  @DisplayName("특수문자가 포함된 브랜드명 추출")
  void should_extract_brand_with_special_chars() {
    Product product = createProduct("[정품] 애플 아이폰", "스펙");

    ProductDocument document = factory.create(product);

    assertThat(document.getBrandName()).isEqualTo("[정품]");
    assertThat(document.getName()).isEqualTo("정품 애플 아이폰");
  }

  @Test
  @DisplayName("천단위 구분자 제거")
  void should_remove_thousand_separators() {
    Product product = createProduct("상품 1,234원", "가격: 1,234,567원");

    ProductDocument document = factory.create(product);

    assertThat(document.getName()).isEqualTo("상품 1234원");
    assertThat(document.getSpecs()).isEqualTo("가격 1234567원");
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
