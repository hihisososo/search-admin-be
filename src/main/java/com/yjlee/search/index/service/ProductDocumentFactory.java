package com.yjlee.search.index.service;

import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.util.BrandExtractor;
import com.yjlee.search.index.util.ModelExtractor;
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
    String normalizedName = TextPreprocessor.normalizeUnits(product.getName());
    String preprocessedName = TextPreprocessor.preprocess(normalizedName);

    String nameUnits = TextPreprocessor.extractUnits(product.getName());
    String specsUnits =
        TextPreprocessor.extractUnits(product.getSpecs() != null ? product.getSpecs() : "");

    return ProductDocument.builder()
        .id(String.valueOf(product.getId()))
        .name(preprocessedName)
        .nameRaw(product.getName())
        .nameUnit(nameUnits)
        .model(ModelExtractor.extractModels(product.getName()))
        .brandName(BrandExtractor.extractBrand(product.getName()))
        .thumbnailUrl(product.getThumbnailUrl())
        .price(product.getPrice() != null ? product.getPrice().intValue() : null)
        .registeredMonth(formatRegisteredMonth(product.getRegMonth()))
        .rating(product.getRating())
        .reviewCount(product.getReviewCount() != null ? product.getReviewCount() : 0)
        .categoryName(product.getCategoryName())
        .specs(TextPreprocessor.preprocess(product.getSpecs() + " " + product.getCategoryName()))
        .specsRaw(product.getSpecs())
        .specsUnit(specsUnits)
        .build();
  }

  public ProductDocument createProductDocument(Product product) {
    return create(product);
  }
}
