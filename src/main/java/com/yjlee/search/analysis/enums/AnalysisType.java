package com.yjlee.search.analysis.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisType {
  SEARCH("nori_search_analyzer", "search_synonym_filter"),
  INDEX("nori_index_analyzer", "stopword_filter");

  private final String analyzer;
  private final String targetFilter;
}
