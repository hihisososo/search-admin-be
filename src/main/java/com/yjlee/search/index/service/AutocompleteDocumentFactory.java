package com.yjlee.search.index.service;

import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.AutocompleteDocument;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.index.model.Product;
import org.springframework.stereotype.Component;

@Component
public class AutocompleteDocumentFactory {

  public AutocompleteDocument create(Product product) {
    return AutocompleteDocument.builder()
        .name(product.getName())
        .nameIcu(TextPreprocessor.preprocess(product.getName()))
        .build();
  }
  
  public AutocompleteDocument createFromProductDocument(ProductDocument productDocument) {
    return AutocompleteDocument.builder()
        .name(productDocument.getNameRaw())
        .nameIcu(productDocument.getName())
        .build();
  }
}