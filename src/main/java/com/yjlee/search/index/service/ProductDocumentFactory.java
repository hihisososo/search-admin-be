package com.yjlee.search.index.service;

import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.util.BrandExtractor;
import org.springframework.stereotype.Component;

@Component
public class ProductDocumentFactory {

  private String formatRegisteredMonth(String regMonth) {
    if (regMonth == null || regMonth.isEmpty()) {
      return "";
    }
    String formatted = regMonth.replace(".", "-");
    return formatted.substring(0, Math.min(7, formatted.length()));
  }

  public ProductDocument create(Product product) {
    // name 필드는 전체 상품명 전처리 (normalizeUnits 포함)
    String preprocessedName = TextPreprocessor.preprocess(product.getName());

    return ProductDocument.builder()
        .id(String.valueOf(product.getId()))
        .name(preprocessedName)
        .nameRaw(product.getName())
        .brandName(BrandExtractor.extractBrand(product.getName()))
        .thumbnailUrl(product.getThumbnailUrl())
        .price(product.getPrice() != null ? product.getPrice().intValue() : null)
        .registeredMonth(formatRegisteredMonth(product.getRegMonth()))
        .rating(product.getRating())
        .reviewCount(product.getReviewCount() != null ? product.getReviewCount() : 0)
        .categoryName(product.getCategoryName())
        .category(
            TextPreprocessor.preprocess(
                product.getCategoryName() != null ? product.getCategoryName() : ""))
        .specs(TextPreprocessor.preprocess(product.getSpecs() != null ? product.getSpecs() : ""))
        .specsRaw(product.getSpecs())
        .build();
  }

  public ProductDocument createProductDocument(Product product) {
    return create(product);
  }
}
