package com.yjlee.search.index.service;

import com.yjlee.search.index.dto.ProductDocument;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductDocumentConverter {

  public ProductDocument convert(
      ProductDocument document, List<Float> nameVector, List<Float> specsVector) {
    return ProductDocument.builder()
        .id(document.getId())
        .name(document.getName())
        .nameRaw(document.getNameRaw())
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
        .nameVector((nameVector == null || nameVector.isEmpty()) ? null : nameVector)
        .specsVector((specsVector == null || specsVector.isEmpty()) ? null : specsVector)
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

  public String createNameText(ProductDocument document) {
    StringBuilder text = new StringBuilder();

    // 상품명
    if (document.getNameRaw() != null && !document.getNameRaw().trim().isEmpty()) {
      text.append(document.getNameRaw().trim());
    }

    // 브랜드명 추가 (상품명과 함께 임베딩)
    if (document.getBrandName() != null && !document.getBrandName().trim().isEmpty()) {
      if (text.length() > 0) {
        text.append(" ");
      }
      text.append(document.getBrandName().trim());
    }

    return text.toString();
  }

  public String createSpecsText(ProductDocument document) {
    StringBuilder text = new StringBuilder();

    // 스펙 정보
    if (document.getSpecsRaw() != null && !document.getSpecsRaw().trim().isEmpty()) {
      text.append(document.getSpecsRaw().trim());
    }

    // 카테고리 정보도 스펙과 함께 임베딩
    if (document.getCategoryName() != null && !document.getCategoryName().trim().isEmpty()) {
      if (text.length() > 0) {
        text.append(" ");
      }
      text.append(document.getCategoryName().trim());
    }

    return text.toString();
  }
}
