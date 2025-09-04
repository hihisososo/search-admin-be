package com.yjlee.search.search.service.builder.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExtractedTerms {

  public static ExtractedTerms empty() {
    return ExtractedTerms.builder().build();
  }

  public boolean isEmpty() {
    return true;
  }
}
