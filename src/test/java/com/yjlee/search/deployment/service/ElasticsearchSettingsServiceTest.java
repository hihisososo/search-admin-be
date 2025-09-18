package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@ExtendWith(MockitoExtension.class)
class ElasticsearchSettingsServiceTest {

  @Mock private ResourceLoader resourceLoader;
  @Mock private Resource resource;

  private ElasticsearchSettingsService settingsService;

  @BeforeEach
  void setUp() {
    settingsService = new ElasticsearchSettingsService(resourceLoader);
  }

  @Test
  @DisplayName("인덱스 설정 생성 - 템플릿 치환 검증")
  void createProductIndexSettings_ReplacesTemplatePlaceholders() throws IOException {
    String userDictPath = "/path/user_v202401011200.txt";
    String stopwordDictPath = "/path/stopword_v202401011200.txt";
    String unitDictPath = "/path/unit_v202401011200.txt";
    String synonymSetName = "synonym_v202401011200";
    String template = """
        {
          "analysis": {
            "analyzer": {
              "product_analyzer": {
                "user_dictionary": "{USER_DICT_PATH}",
                "stopwords_path": "{STOPWORD_DICT_PATH}",
                "unit_dictionary": "{UNIT_DICT_PATH}",
                "synonym_set_id": "{SYNONYM_SET_NAME}"
              }
            }
          }
        }""";

    when(resourceLoader.getResource("classpath:elasticsearch/product-settings.json"))
        .thenReturn(resource);
    when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(template.getBytes()));

    String result = settingsService.createProductIndexSettings(
        userDictPath, stopwordDictPath, unitDictPath, synonymSetName);

    assertThat(result).contains("/path/user_v202401011200.txt");
    assertThat(result).contains("/path/stopword_v202401011200.txt");
    assertThat(result).contains("/path/unit_v202401011200.txt");
    assertThat(result).contains("synonym_v202401011200");
    assertThat(result).doesNotContain("{USER_DICT_PATH}");
    assertThat(result).doesNotContain("{STOPWORD_DICT_PATH}");
    assertThat(result).doesNotContain("{UNIT_DICT_PATH}");
    assertThat(result).doesNotContain("{SYNONYM_SET_NAME}");
  }

  @Test
  @DisplayName("Autocomplete 인덱스 설정 생성 - 사용자 사전 경로만 치환")
  void createAutocompleteIndexSettings_ReplacesUserDictPath() throws IOException {
    // given
    String userDictPath = "/path/user_v202401011200.txt";
    String template = """
        {
          "analysis": {
            "tokenizer": {
              "autocomplete_tokenizer": {
                "user_dictionary": "{USER_DICT_PATH}"
              }
            }
          }
        }""";

    when(resourceLoader.getResource("classpath:elasticsearch/autocomplete-settings.json"))
        .thenReturn(resource);
    when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(template.getBytes()));

    // when
    String result = settingsService.createAutocompleteIndexSettings(userDictPath);

    // then
    assertThat(result).contains("/path/user_v202401011200.txt");
    assertThat(result).doesNotContain("{USER_DICT_PATH}");
  }

  @Test
  @DisplayName("리소스 파일 로드 실패 시 예외 발생")
  void loadResourceFile_ThrowsExceptionOnIOError() throws IOException {
    // given
    String userDictPath = "/path/user_v202401011200.txt";
    String stopwordDictPath = "/path/stopword_v202401011200.txt";
    String unitDictPath = "/path/unit_v202401011200.txt";
    String synonymSetName = "synonym_v202401011200";

    when(resourceLoader.getResource("classpath:elasticsearch/product-settings.json"))
        .thenReturn(resource);
    when(resource.getInputStream()).thenThrow(new IOException("File not found"));

    // when & then
    assertThatThrownBy(() -> settingsService.createProductIndexSettings(
            userDictPath, stopwordDictPath, unitDictPath, synonymSetName))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("리소스 로드 중 에러");
  }

  @Test
  @DisplayName("빈 템플릿 처리")
  void handleEmptyTemplate() throws IOException {
    // given
    String userDictPath = "/path/user_dict.txt";
    String template = "";

    when(resourceLoader.getResource("classpath:elasticsearch/autocomplete-settings.json"))
        .thenReturn(resource);
    when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(template.getBytes()));

    // when
    String result = settingsService.createAutocompleteIndexSettings(userDictPath);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("여러 개의 동일한 placeholder 치환")
  void replaceMultipleSamePlaceholders() throws IOException {
    // given
    String userDictPath = "/path/user_dict.txt";
    String template = """
        {
          "tokenizer1": "{USER_DICT_PATH}",
          "tokenizer2": "{USER_DICT_PATH}",
          "tokenizer3": "{USER_DICT_PATH}"
        }""";

    when(resourceLoader.getResource("classpath:elasticsearch/autocomplete-settings.json"))
        .thenReturn(resource);
    when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(template.getBytes()));

    // when
    String result = settingsService.createAutocompleteIndexSettings(userDictPath);

    // then
    assertThat(result).doesNotContain("{USER_DICT_PATH}");
    int count = result.split("/path/user_dict.txt", -1).length - 1;
    assertThat(count).isEqualTo(3);
  }
}