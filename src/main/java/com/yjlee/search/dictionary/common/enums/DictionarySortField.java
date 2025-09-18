package com.yjlee.search.dictionary.common.enums;

import java.util.Set;

public final class DictionarySortField {

  public static final String DEFAULT_FIELD = "updatedAt";
  private static final Set<String> VALID_FIELDS = Set.of("keyword", "createdAt", "updatedAt");

  private DictionarySortField() {
    // 유틸리티 클래스 인스턴스 생성 방지
  }

  public static String getValidFieldOrDefault(String sortBy) {
    return VALID_FIELDS.contains(sortBy) ? sortBy : DEFAULT_FIELD;
  }
}
