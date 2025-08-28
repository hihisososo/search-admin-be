package com.yjlee.search.index.service;

import com.yjlee.search.index.dto.ProductDocument;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductDocumentConverter {

  public ProductDocument convert(ProductDocument document, List<Float> vector) {
    return ProductDocument.builder()
        .id(document.getId())
        .name(document.getName())
        .nameRaw(document.getNameRaw())
        .model(document.getModel())
        .brandName(document.getBrandName())
        .thumbnailUrl(document.getThumbnailUrl())
        .price(document.getPrice())
        .registeredMonth(document.getRegisteredMonth())
        .rating(document.getRating())
        .reviewCount(document.getReviewCount())
        .categoryName(document.getCategoryName())
        .category(document.getCategory())
        .specs(document.getSpecs())
        .specsRaw(document.getSpecsRaw())
        .units(document.getUnits())
        .nameCandidate(document.getNameCandidate())
        .specsCandidate(document.getSpecsCandidate())
        .categoryCandidate(document.getCategoryCandidate())
        .nameSpecsVector((vector == null || vector.isEmpty()) ? null : vector)
        .build();
  }

  public String createSearchableText(ProductDocument document) {
    StringBuilder text = new StringBuilder();

    // [TITLE] 섹션
    if (document.getNameRaw() != null && !document.getNameRaw().trim().isEmpty()) {
      text.append("[TITLE] ").append(document.getNameRaw().trim());
    }

    // [CATEGORY] 섹션
    if (document.getCategoryName() != null && !document.getCategoryName().trim().isEmpty()) {
      if (text.length() > 0) {
        text.append("\n");
      }
      text.append("[CATEGORY] ").append(document.getCategoryName().trim());
    }

    // [SPECS] 섹션
    if (document.getSpecsRaw() != null && !document.getSpecsRaw().trim().isEmpty()) {
      if (text.length() > 0) {
        text.append("\n");
      }
      text.append("[SPECS] ").append(document.getSpecsRaw().trim());
    }

    return text.toString();
  }
}
