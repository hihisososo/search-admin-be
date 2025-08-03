package com.yjlee.search.dictionary.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class DictionarySortUtilsTest {

  @Test
  @DisplayName("현재 사전 정렬 - 기본값 (updatedAt desc)")
  void createSort_CurrentDictionary_DefaultValues() {
    Sort sort = DictionarySortUtils.createSort(null, null, false);
    
    assertThat(sort.getOrderFor("updatedAt")).isNotNull();
    assertThat(sort.getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("스냅샷 사전 정렬 - 기본값 (createdAt desc)")
  void createSort_SnapshotDictionary_DefaultValues() {
    Sort sort = DictionarySortUtils.createSort(null, null, true);
    
    assertThat(sort.getOrderFor("createdAt")).isNotNull();
    assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("정렬 필드와 방향 지정")
  void createSort_WithSortByAndDirection() {
    Sort sort = DictionarySortUtils.createSort("keyword", "asc", false);
    
    assertThat(sort.getOrderFor("keyword")).isNotNull();
    assertThat(sort.getOrderFor("keyword").getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  @DisplayName("허용되지 않은 정렬 필드 - 기본값으로 변경")
  void createSort_InvalidField_DefaultsToUpdatedAt() {
    Sort sort = DictionarySortUtils.createSort("invalidField", "desc", false);
    
    assertThat(sort.getOrderFor("updatedAt")).isNotNull();
    assertThat(sort.getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("빈 문자열 정렬 필드 - 기본값 사용")
  void createSort_EmptyField_DefaultsToUpdatedAt() {
    Sort sort = DictionarySortUtils.createSort("", "", false);
    
    assertThat(sort.getOrderFor("updatedAt")).isNotNull();
    assertThat(sort.getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }
}