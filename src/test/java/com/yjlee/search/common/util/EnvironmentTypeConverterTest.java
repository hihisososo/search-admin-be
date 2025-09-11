package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnvironmentTypeConverter 테스트")
class EnvironmentTypeConverterTest {

  @Test
  @DisplayName("IndexEnvironmentType을 DictionaryEnvironmentType으로 변환")
  void testToDictionaryEnvironmentType() {
    // DEV 변환
    DictionaryEnvironmentType result =
        EnvironmentTypeConverter.toDictionaryEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
    assertThat(result).isEqualTo(DictionaryEnvironmentType.DEV);

    // PROD 변환
    result =
        EnvironmentTypeConverter.toDictionaryEnvironmentType(IndexEnvironment.EnvironmentType.PROD);
    assertThat(result).isEqualTo(DictionaryEnvironmentType.PROD);

    // null 처리
    result = EnvironmentTypeConverter.toDictionaryEnvironmentType(null);
    assertThat(result).isEqualTo(DictionaryEnvironmentType.CURRENT);
  }

  @Test
  @DisplayName("DictionaryEnvironmentType을 IndexEnvironmentType으로 변환")
  void testToIndexEnvironmentType() {
    // DEV 변환
    IndexEnvironment.EnvironmentType result =
        EnvironmentTypeConverter.toIndexEnvironmentType(DictionaryEnvironmentType.DEV);
    assertThat(result).isEqualTo(IndexEnvironment.EnvironmentType.DEV);

    // PROD 변환
    result = EnvironmentTypeConverter.toIndexEnvironmentType(DictionaryEnvironmentType.PROD);
    assertThat(result).isEqualTo(IndexEnvironment.EnvironmentType.PROD);

    // CURRENT는 PROD로 매핑
    result = EnvironmentTypeConverter.toIndexEnvironmentType(DictionaryEnvironmentType.CURRENT);
    assertThat(result).isEqualTo(IndexEnvironment.EnvironmentType.PROD);

    // null 처리
    result = EnvironmentTypeConverter.toIndexEnvironmentType(null);
    assertThat(result).isEqualTo(IndexEnvironment.EnvironmentType.PROD);
  }

  @Test
  @DisplayName("양방향 변환 일관성 테스트")
  void testBidirectionalConversion() {
    // DEV -> Dictionary -> Index
    IndexEnvironment.EnvironmentType originalIndex = IndexEnvironment.EnvironmentType.DEV;
    DictionaryEnvironmentType dict =
        EnvironmentTypeConverter.toDictionaryEnvironmentType(originalIndex);
    IndexEnvironment.EnvironmentType convertedBack =
        EnvironmentTypeConverter.toIndexEnvironmentType(dict);
    assertThat(convertedBack).isEqualTo(originalIndex);

    // PROD -> Dictionary -> Index
    originalIndex = IndexEnvironment.EnvironmentType.PROD;
    dict = EnvironmentTypeConverter.toDictionaryEnvironmentType(originalIndex);
    convertedBack = EnvironmentTypeConverter.toIndexEnvironmentType(dict);
    assertThat(convertedBack).isEqualTo(originalIndex);
  }
}
