package com.yjlee.search.analysis.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.analysis.domain.TokenInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MermaidDiagramGeneratorTest {

  @Test
  @DisplayName("빈 토큰 리스트로 다이어그램 생성")
  void shouldGenerateEmptyDiagram() {
    String diagram = MermaidDiagramGenerator.generate(Collections.emptyList());

    assertThat(diagram).contains("graph LR");
    assertThat(diagram).contains("START");
    assertThat(diagram).contains("END");
    assertThat(diagram).contains("-->");
  }

  @Test
  @DisplayName("단순 토큰 체인 다이어그램 생성")
  void shouldGenerateSimpleTokenChain() {
    List<TokenInfo> tokens = Arrays.asList(
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
            .build()
    );

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
  @DisplayName("동의어 토큰 포함 다이어그램 생성")
  void shouldGenerateDiagramWithSynonyms() {
    List<TokenInfo> tokens = Arrays.asList(
        TokenInfo.builder()
            .token("맥북")
            .type("word")
            .position(0)
            .positionLength(1)
            .startOffset(0)
            .endOffset(2)
            .build(),
        TokenInfo.builder()
            .token("macbook")
            .type("SYNONYM")
            .position(0)
            .positionLength(1)
            .startOffset(0)
            .endOffset(2)
            .build(),
        TokenInfo.builder()
            .token("프로")
            .type("word")
            .position(1)
            .positionLength(1)
            .startOffset(3)
            .endOffset(5)
            .build()
    );

    String diagram = MermaidDiagramGenerator.generate(tokens);

    assertThat(diagram).contains("graph LR");
    assertThat(diagram).contains("0((\"START\"))");
    assertThat(diagram).contains("2((\"END\"))");
    assertThat(diagram).contains("0 -->|맥북| 1");
    assertThat(diagram).contains("0 -->|macbook| 1");
    assertThat(diagram).contains("1 -->|프로| 2");
  }

  @Test
  @DisplayName("multi-position 토큰 다이어그램 생성")
  void shouldGenerateDiagramWithMultiPositionToken() {
    List<TokenInfo> tokens = Arrays.asList(
        TokenInfo.builder()
            .token("갤럭시")
            .type("word")
            .position(0)
            .positionLength(2)
            .startOffset(0)
            .endOffset(3)
            .build(),
        TokenInfo.builder()
            .token("s24")
            .type("word")
            .position(2)
            .positionLength(1)
            .startOffset(4)
            .endOffset(7)
            .build()
    );

    String diagram = MermaidDiagramGenerator.generate(tokens);

    assertThat(diagram).contains("graph LR");
    assertThat(diagram).contains("0((\"START\"))");
    assertThat(diagram).contains("2((\"END\"))");
    assertThat(diagram).contains("0 -->|갤럭시| 1");
    assertThat(diagram).contains("1 -->|s24| 2");
  }

  @Test
  @DisplayName("복잡한 토큰 구조 다이어그램 생성")
  void shouldGenerateComplexDiagram() {
    List<TokenInfo> tokens = Arrays.asList(
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
            .build()
    );

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

  @Test
  @DisplayName("단일 토큰 다이어그램 생성")
  void shouldGenerateSingleTokenDiagram() {
    List<TokenInfo> tokens = Arrays.asList(
        TokenInfo.builder()
            .token("테스트")
            .type("word")
            .position(0)
            .positionLength(1)
            .startOffset(0)
            .endOffset(3)
            .build()
    );

    String diagram = MermaidDiagramGenerator.generate(tokens);

    assertThat(diagram).contains("0((\"START\"))");
    assertThat(diagram).contains("1((\"END\"))");
    assertThat(diagram).contains("0 -->|테스트| 1");
    assertThat(diagram).doesNotContain("AND");
  }

  @Test
  @DisplayName("additional 타입 토큰 다이어그램 생성")
  void shouldGenerateDiagramWithAdditionalTokens() {
    List<TokenInfo> tokens = Arrays.asList(
        TokenInfo.builder()
            .token("아이폰")
            .type("word")
            .position(0)
            .positionLength(1)
            .startOffset(0)
            .endOffset(3)
            .build(),
        TokenInfo.builder()
            .token("iphone")
            .type("additional")
            .position(0)
            .positionLength(1)
            .startOffset(0)
            .endOffset(3)
            .build()
    );

    String diagram = MermaidDiagramGenerator.generate(tokens);

    assertThat(diagram).contains("0 -->|아이폰| 1");
    assertThat(diagram).contains("0 -->|iphone| 1");
  }

  @Test
  @DisplayName("포지션 매핑 정확성 확인")
  void shouldMapPositionsCorrectly() {
    List<TokenInfo> tokens = Arrays.asList(
        TokenInfo.builder()
            .token("A")
            .type("word")
            .position(0)
            .positionLength(1)
            .startOffset(0)
            .endOffset(1)
            .build(),
        TokenInfo.builder()
            .token("B")
            .type("word")
            .position(3)
            .positionLength(1)
            .startOffset(2)
            .endOffset(3)
            .build(),
        TokenInfo.builder()
            .token("C")
            .type("word")
            .position(5)
            .positionLength(1)
            .startOffset(4)
            .endOffset(5)
            .build()
    );

    String diagram = MermaidDiagramGenerator.generate(tokens);

    assertThat(diagram).contains("0 -->|A| 1");
    assertThat(diagram).contains("1 -->|B| 2");
    assertThat(diagram).contains("2 -->|C| 3");
  }
}