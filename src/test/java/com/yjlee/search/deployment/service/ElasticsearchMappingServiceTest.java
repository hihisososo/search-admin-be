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
class ElasticsearchMappingServiceTest {

  @Mock private ResourceLoader resourceLoader;
  @Mock private Resource resource;

  private ElasticsearchMappingService mappingService;

  @BeforeEach
  void setUp() {
    mappingService = new ElasticsearchMappingService(resourceLoader);
  }

  @Test
  @DisplayName("Product 매핑 파일 로드 성공")
  void loadProductMapping_Success() throws IOException {
    String expectedMapping = """
        {
          "properties": {
            "name": {
              "type": "text",
              "analyzer": "korean"
            },
            "price": {
              "type": "long"
            }
          }
        }""";

    when(resourceLoader.getResource("classpath:elasticsearch/product-mapping.json"))
        .thenReturn(resource);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(expectedMapping.getBytes()));

    String result = mappingService.loadProductMapping();

    assertThat(result).isEqualTo(expectedMapping);
  }

  @Test
  @DisplayName("Autocomplete 매핑 파일 로드 성공")
  void loadAutocompleteMapping_Success() throws IOException {
    String expectedMapping = """
        {
          "properties": {
            "suggest": {
              "type": "completion",
              "analyzer": "simple"
            }
          }
        }""";

    when(resourceLoader.getResource("classpath:elasticsearch/autocomplete-mapping.json"))
        .thenReturn(resource);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(expectedMapping.getBytes()));

    String result = mappingService.loadAutocompleteMapping();

    assertThat(result).isEqualTo(expectedMapping);
  }

  @Test
  @DisplayName("매핑 파일 로드 실패 시 예외 발생")
  void loadMapping_ThrowsExceptionOnIOError() throws IOException {
    when(resourceLoader.getResource("classpath:elasticsearch/product-mapping.json"))
        .thenReturn(resource);
    when(resource.getInputStream()).thenThrow(new IOException("File not found"));

    assertThatThrownBy(() -> mappingService.loadProductMapping())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("리소스 파일 로드 중 에러 발생")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("Resource가 null인 경우 처리")
  void loadMapping_HandlesNullResource() throws IOException {
    when(resourceLoader.getResource("classpath:elasticsearch/autocomplete-mapping.json"))
        .thenReturn(resource);
    when(resource.getInputStream()).thenThrow(new NullPointerException());

    assertThatThrownBy(() -> mappingService.loadAutocompleteMapping())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("리소스 파일 로드 중 에러 발생");
  }

  @Test
  @DisplayName("빈 매핑 파일 처리")
  void loadMapping_HandlesEmptyFile() throws IOException {
    String emptyMapping = "";

    when(resourceLoader.getResource("classpath:elasticsearch/product-mapping.json"))
        .thenReturn(resource);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(emptyMapping.getBytes()));

    String result = mappingService.loadProductMapping();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("UTF-8 인코딩된 한글 포함 매핑 파일 처리")
  void loadMapping_HandlesKoreanCharacters() throws IOException {
    String koreanMapping = """
        {
          "properties": {
            "상품명": {
              "type": "text",
              "analyzer": "한글분석기"
            }
          }
        }""";

    when(resourceLoader.getResource("classpath:elasticsearch/product-mapping.json"))
        .thenReturn(resource);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream(koreanMapping.getBytes()));

    String result = mappingService.loadProductMapping();

    assertThat(result).contains("상품명");
    assertThat(result).contains("한글분석기");
  }
}