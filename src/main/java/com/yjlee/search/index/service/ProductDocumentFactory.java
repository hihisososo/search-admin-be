package com.yjlee.search.index.service;

import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.util.BrandExtractor;
import com.yjlee.search.index.util.ModelExtractor;
import com.yjlee.search.index.util.UnitExtractor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    // 모델명 추출 (name과 specs 모두에서 추출)
    List<String> nameModels = ModelExtractor.extractModels(product.getName());
    List<String> specsModels =
        ModelExtractor.extractModels(product.getSpecs() != null ? product.getSpecs() : "");

    // 중복 제거하여 병합
    List<String> models = new ArrayList<>(nameModels);
    for (String specModel : specsModels) {
      if (!models.contains(specModel)) {
        models.add(specModel);
      }
    }

    // name 필드는 모델명 포함한 전체 상품명 전처리 (normalizeUnits 포함)
    String preprocessedName = TextPreprocessor.preprocess(product.getName());

    // name과 specs에서 단위 추출 및 증강 후 중복 제거
    List<String> nameUnits = UnitExtractor.extractUnitsForIndexing(product.getName());
    List<String> specsUnits =
        UnitExtractor.extractUnitsForIndexing(product.getSpecs() != null ? product.getSpecs() : "");

    // 중복 제거하여 하나의 units 필드로 통합
    Set<String> unitsSet = new HashSet<>();
    unitsSet.addAll(nameUnits);
    unitsSet.addAll(specsUnits);
    String combinedUnits = unitsSet.isEmpty() ? null : String.join(" ", unitsSet);

    // 후보군 검색용 필드: 전처리 적용 (normalizeUnits 포함)
    String nameCandidate = TextPreprocessor.preprocess(product.getName());
    String specsCandidate =
        TextPreprocessor.preprocess(product.getSpecs() != null ? product.getSpecs() : "");

    String categoryCandidate =
        TextPreprocessor.preprocess(
            product.getCategoryName() != null ? product.getCategoryName() : "");

    return ProductDocument.builder()
        .id(String.valueOf(product.getId()))
        .name(preprocessedName)
        .nameRaw(product.getName())
        .units(combinedUnits)
        .nameCandidate(nameCandidate)
        .model(models.isEmpty() ? null : String.join(" ", models))
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
        .specsCandidate(specsCandidate)
        .categoryCandidate(categoryCandidate)
        .build();
  }

  public ProductDocument createProductDocument(Product product) {
    return create(product);
  }
}
