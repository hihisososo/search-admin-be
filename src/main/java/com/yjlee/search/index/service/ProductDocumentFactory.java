package com.yjlee.search.index.service;

import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.util.BrandExtractor;
import org.springframework.stereotype.Component;

@Component
public class ProductDocumentFactory {

  public ProductDocument create(Product product) {
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
        .reviewCount(product.getReviewCount())
        .categoryName(product.getCategoryName())
        .category(
            TextPreprocessor.preprocess(
                product.getCategoryName() != null ? product.getCategoryName() : ""))
        .specs(TextPreprocessor.preprocess(product.getSpecs() != null ? product.getSpecs() : ""))
        .specsRaw(product.getSpecs())
        .build();
  }

  private String formatRegisteredMonth(String regMonth) {
    if (regMonth == null || regMonth.isEmpty()) {
      return null;
    }
    String formatted = regMonth.replace(".", "-");
    return formatted.substring(0, Math.min(7, formatted.length()));
  }
}
