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
        .specs(document.getSpecs())
        .specsRaw(document.getSpecsRaw())
        // 벡터 임시 비활성화 시 빈값도 넣지 않도록 null 허용
        .nameSpecsVector((vector == null || vector.isEmpty()) ? null : vector)
        .build();
  }

  public String createSearchableText(ProductDocument document) {
    StringBuilder text = new StringBuilder();

    if (document.getNameRaw() != null) {
      text.append(document.getNameRaw().trim());
    }

    if (document.getSpecsRaw() != null && !document.getSpecsRaw().trim().isEmpty()) {
      if (text.length() > 0) {
        text.append(" ");
      }
      text.append(document.getSpecsRaw().trim());
    }

    return text.toString();
  }
}
