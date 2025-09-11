package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PromptTemplateLoader 테스트")
class PromptTemplateLoaderTest {

  private PromptTemplateLoader promptTemplateLoader;

  @BeforeEach
  void setUp() {
    promptTemplateLoader = new PromptTemplateLoader();
  }

  @Test
  @DisplayName("템플릿 파일 로드 성공")
  void testLoadTemplate() {
    // when
    String content = promptTemplateLoader.loadTemplate("test-template.txt");

    // then
    assertThat(content).isEqualTo("Hello {name}! Welcome to {place}.");
  }

  @Test
  @DisplayName("존재하지 않는 템플릿 파일 로드 시 빈 문자열 반환")
  void testLoadTemplateNotFound() {
    // when
    String content = promptTemplateLoader.loadTemplate("non-existent.txt");

    // then
    assertThat(content).isEmpty();
  }

  @Test
  @DisplayName("템플릿 변수 치환 - 단일 변수")
  void testLoadTemplateWithSingleVariable() {
    // given
    Map<String, String> variables = new HashMap<>();
    variables.put("name", "John");
    variables.put("place", "Seoul");

    // when
    String result = promptTemplateLoader.loadTemplate("test-template.txt", variables);

    // then
    assertThat(result).isEqualTo("Hello John! Welcome to Seoul.");
  }

  @Test
  @DisplayName("템플릿 변수 치환 - 일부 변수만 제공")
  void testLoadTemplateWithPartialVariables() {
    // given
    Map<String, String> variables = new HashMap<>();
    variables.put("name", "Alice");

    // when
    String result = promptTemplateLoader.loadTemplate("test-template.txt", variables);

    // then
    assertThat(result).isEqualTo("Hello Alice! Welcome to {place}.");
  }

  @Test
  @DisplayName("템플릿 변수 치환 - 빈 변수 맵")
  void testLoadTemplateWithEmptyVariables() {
    // given
    Map<String, String> variables = new HashMap<>();

    // when
    String result = promptTemplateLoader.loadTemplate("test-template.txt", variables);

    // then
    assertThat(result).isEqualTo("Hello {name}! Welcome to {place}.");
  }

  @Test
  @DisplayName("템플릿 변수 치환 - 존재하지 않는 템플릿")
  void testLoadTemplateWithVariablesNotFound() {
    // given
    Map<String, String> variables = new HashMap<>();
    variables.put("name", "Bob");

    // when
    String result = promptTemplateLoader.loadTemplate("non-existent.txt", variables);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("템플릿 변수 치환 - 특수 문자 포함")
  void testLoadTemplateWithSpecialCharacters() {
    // given
    Map<String, String> variables = new HashMap<>();
    variables.put("name", "John's \"Test\"");
    variables.put("place", "Seoul & Busan");

    // when
    String result = promptTemplateLoader.loadTemplate("test-template.txt", variables);

    // then
    assertThat(result).isEqualTo("Hello John's \"Test\"! Welcome to Seoul & Busan.");
  }

  @Test
  @DisplayName("템플릿 변수 치환 - 빈 값 처리")
  void testLoadTemplateWithEmptyValue() {
    // given
    Map<String, String> variables = new HashMap<>();
    variables.put("name", "");
    variables.put("place", "Seoul");

    // when
    String result = promptTemplateLoader.loadTemplate("test-template.txt", variables);

    // then
    assertThat(result).isEqualTo("Hello ! Welcome to Seoul.");
  }
}