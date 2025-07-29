package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptTemplateLoaderTest {

  private PromptTemplateLoader loader;

  @BeforeEach
  void setUp() {
    loader = new PromptTemplateLoader();
  }

  @Test
  @DisplayName("존재하는 템플릿 파일 로드")
  void should_load_existing_template() {
    String result = loader.loadTemplate("query-generation.txt");

    assertThat(result).isNotEmpty();
    assertThat(result).contains("검색");
    assertThat(result).contains("쿼리");
  }

  @Test
  @DisplayName("존재하지 않는 템플릿 파일 로드시 빈 문자열 반환")
  void should_return_empty_string_for_non_existing_template() {
    String result = loader.loadTemplate("non-existing-template.txt");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("변수를 포함한 템플릿 로드 및 치환")
  void should_load_template_and_replace_variables() {
    Map<String, String> variables = new HashMap<>();
    variables.put("QUERY", "노트북");
    variables.put("PRODUCT_COUNT", "3");

    String result = loader.loadTemplate("bulk-product-relevance-evaluation.txt", variables);

    assertThat(result).isNotEmpty();
    assertThat(result).contains("검색 쿼리: \"노트북\"");
    assertThat(result).contains("3개 상품");
  }

  @Test
  @DisplayName("빈 변수 맵으로 템플릿 로드")
  void should_handle_empty_variables_map() {
    Map<String, String> variables = new HashMap<>();

    String result = loader.loadTemplate("query-generation.txt", variables);

    assertThat(result).isNotEmpty();
  }

  @Test
  @DisplayName("null 변수로 템플릿 로드시 치환하지 않음")
  void should_not_replace_null_variables() {
    Map<String, String> variables = new HashMap<>();
    variables.put("nonExistingKey", "value");

    String result = loader.loadTemplate("query-generation.txt", variables);

    assertThat(result).isNotEmpty();
    assertThat(result).doesNotContain("value");
  }
}
