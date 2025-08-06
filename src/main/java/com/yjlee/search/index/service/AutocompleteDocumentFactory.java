package com.yjlee.search.index.service;

import com.yjlee.search.common.util.KoreanTextUtils;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import org.springframework.stereotype.Component;

@Component
public class AutocompleteDocumentFactory {

  public AutocompleteDocument create(Product product) {
    String name = product.getName();
    String nameLower = name.toLowerCase();
    String nameNoSpace = name.replaceAll("\\s+", "");
    String nameNoSpaceLower = nameNoSpace.toLowerCase();
    return AutocompleteDocument.builder()
        .name(nameLower)
        .nameJamo(KoreanTextUtils.decomposeHangul(nameLower))
        .nameChosung(KoreanTextUtils.extractChosung(nameLower))
        .nameNori(nameLower)
        .nameJamoNoSpace(KoreanTextUtils.decomposeHangul(nameNoSpaceLower))
        .build();
  }

  public AutocompleteDocument createFromProductDocument(ProductDocument productDocument) {
    String name = productDocument.getNameRaw();
    String nameLower = name.toLowerCase();
    String nameNoSpace = name.replaceAll("\\s+", "");
    String nameNoSpaceLower = nameNoSpace.toLowerCase();
    return AutocompleteDocument.builder()
        .name(nameLower)
        .nameJamo(KoreanTextUtils.decomposeHangul(nameLower))
        .nameChosung(KoreanTextUtils.extractChosung(nameLower))
        .nameNori(nameLower)
        .nameJamoNoSpace(KoreanTextUtils.decomposeHangul(nameNoSpaceLower))
        .build();
  }
}
