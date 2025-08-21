package com.yjlee.search.index.service;

import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.util.BrandExtractor;
import com.yjlee.search.index.util.ModelExtractor;
import java.util.List;
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
    // 모델명 먼저 추출
    List<String> models = ModelExtractor.extractModels(product.getName());

    // 모델명 제거 후 정규화
    String nameWithoutModels = TextPreprocessor.removeModels(product.getName(), models);
    String normalizedName = TextPreprocessor.normalizeUnits(nameWithoutModels);
    String preprocessedName = TextPreprocessor.preprocess(normalizedName);

    String nameUnits = TextPreprocessor.extractUnits(product.getName());
    String specsUnits =
        TextPreprocessor.extractUnits(product.getSpecs() != null ? product.getSpecs() : "");

    return ProductDocument.builder()
        .id(String.valueOf(product.getId()))
        .name(preprocessedName)
        .nameRaw(product.getName())
        .nameUnit(nameUnits)
        .model(models.isEmpty() ? null : String.join(" ", models))
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
