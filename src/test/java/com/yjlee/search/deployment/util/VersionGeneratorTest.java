package com.yjlee.search.deployment.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VersionGeneratorTest {

  @Test
  @DisplayName("버전 생성 검증")
  void generateVersion_CorrectFormat() {
    String version = VersionGenerator.generateVersion();

    assertThat(version).startsWith("v");
    assertThat(version).hasSize(15);
    assertThat(version.substring(1)).matches("\\d{14}"); // 숫자 14자리
  }
}
