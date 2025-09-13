package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.common.enums.EnvironmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnvironmentTypeConverter 테스트")
class EnvironmentTypeConverterTest {

  @Test
  @DisplayName("IndexEnvironmentType을 EnvironmentType으로 변환")
  void testToEnvironmentType() {
    // DEV 변환
    EnvironmentType result = EnvironmentTypeConverter.toEnvironmentType(EnvironmentType.DEV);
    assertThat(result).isEqualTo(EnvironmentType.DEV);

    // PROD 변환
    result = EnvironmentTypeConverter.toEnvironmentType(EnvironmentType.PROD);
    assertThat(result).isEqualTo(EnvironmentType.PROD);

    // null 처리
    result = EnvironmentTypeConverter.toEnvironmentType(null);
    assertThat(result).isEqualTo(EnvironmentType.CURRENT);
  }

  @Test
  @DisplayName("EnvironmentType을 IndexEnvironmentType으로 변환")
  void testToIndexEnvironmentType() {
    // DEV 변환
    EnvironmentType result = EnvironmentTypeConverter.toIndexEnvironmentType(EnvironmentType.DEV);
    assertThat(result).isEqualTo(EnvironmentType.DEV);

    // PROD 변환
    result = EnvironmentTypeConverter.toIndexEnvironmentType(EnvironmentType.PROD);
    assertThat(result).isEqualTo(EnvironmentType.PROD);

    // CURRENT는 PROD로 매핑
    result = EnvironmentTypeConverter.toIndexEnvironmentType(EnvironmentType.CURRENT);
    assertThat(result).isEqualTo(EnvironmentType.PROD);

    // null 처리
    result = EnvironmentTypeConverter.toIndexEnvironmentType(null);
    assertThat(result).isEqualTo(EnvironmentType.PROD);
  }

  @Test
  @DisplayName("양방향 변환 일관성 테스트")
  void testBidirectionalConversion() {
    // DEV -> Dictionary -> Index
    EnvironmentType originalIndex = EnvironmentType.DEV;
    EnvironmentType dict = EnvironmentTypeConverter.toEnvironmentType(originalIndex);
    EnvironmentType convertedBack = EnvironmentTypeConverter.toIndexEnvironmentType(dict);
    assertThat(convertedBack).isEqualTo(originalIndex);

    // PROD -> Dictionary -> Index
    originalIndex = EnvironmentType.PROD;
    dict = EnvironmentTypeConverter.toEnvironmentType(originalIndex);
    convertedBack = EnvironmentTypeConverter.toIndexEnvironmentType(dict);
    assertThat(convertedBack).isEqualTo(originalIndex);
  }
}
