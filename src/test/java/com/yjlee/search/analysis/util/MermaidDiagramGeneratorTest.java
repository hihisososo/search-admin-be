package com.yjlee.search.analysis.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.analysis.domain.TokenInfo;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MermaidDiagramGeneratorTest {

  @Test
  @DisplayName("단순 다이어그램 생성")
  void shouldGenerateSimpleTokenChain() {
    List<TokenInfo> tokens =
        Arrays.asList(
            TokenInfo.builder()
                .token("아이폰")
                .type("word")
                .position(0)
                .positionLength(1)
                .startOffset(0)
                .endOffset(3)
                .build(),
            TokenInfo.builder()
                .token("15")
                .type("word")
                .position(1)
                .positionLength(1)
                .startOffset(4)
                .endOffset(6)
                .build(),
            TokenInfo.builder()
                .token("프로")
                .type("word")
                .position(2)
                .positionLength(1)
                .startOffset(7)
                .endOffset(9)
                .build());

    String diagram = MermaidDiagramGenerator.generate(tokens);

    assertThat(diagram).contains("graph LR");
    assertThat(diagram).contains("0((\"START\"))");
    assertThat(diagram).contains("3((\"END\"))");
    assertThat(diagram).contains("1((\"AND\"))");
    assertThat(diagram).contains("2((\"AND\"))");
    assertThat(diagram).contains("0 -->|아이폰| 1");
    assertThat(diagram).contains("1 -->|15| 2");
    assertThat(diagram).contains("2 -->|프로| 3");
  }

  @Test
  @DisplayName("복잡한 토큰 구조 다이어그램 생성")
  void shouldGenerateComplexDiagram() {
    List<TokenInfo> tokens =
        Arrays.asList(
            TokenInfo.builder()
                .token("삼성")
                .type("word")
                .position(0)
                .positionLength(1)
                .startOffset(0)
                .endOffset(2)
                .build(),
            TokenInfo.builder()
                .token("samsung")
                .type("SYNONYM")
                .position(0)
                .positionLength(1)
                .startOffset(0)
                .endOffset(2)
                .build(),
            TokenInfo.builder()
                .token("갤럭시")
                .type("word")
                .position(1)
                .positionLength(2)
                .startOffset(3)
                .endOffset(6)
                .build(),
            TokenInfo.builder()
                .token("galaxy")
                .type("SYNONYM")
                .position(1)
                .positionLength(2)
                .startOffset(3)
                .endOffset(6)
                .build(),
            TokenInfo.builder()
                .token("s24")
                .type("word")
                .position(3)
                .positionLength(1)
                .startOffset(7)
                .endOffset(10)
                .build());

    String diagram = MermaidDiagramGenerator.generate(tokens);

    assertThat(diagram).contains("graph LR");
    assertThat(diagram).contains("classDef startEnd");
    assertThat(diagram).contains("classDef andNode");
    assertThat(diagram).contains("0((\"START\"))");
    assertThat(diagram).contains("3((\"END\"))");
    assertThat(diagram).contains("0 -->|삼성| 1");
    assertThat(diagram).contains("0 -->|samsung| 1");
    assertThat(diagram).contains("1 -->|갤럭시| 2");
    assertThat(diagram).contains("1 -->|galaxy| 2");
    assertThat(diagram).contains("2 -->|s24| 3");
  }
}
