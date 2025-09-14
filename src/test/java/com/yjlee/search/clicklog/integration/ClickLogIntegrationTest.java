package com.yjlee.search.clicklog.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.test.base.BaseIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ClickLogApiIntegrationTest extends BaseIntegrationTest {

  @Test
  @DisplayName("클릭 로그 저장 성공")
  void logClickSuccess() throws Exception {
    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword("노트북")
            .clickedProductId("PROD-12345")
            .indexName("products")
            .sessionId(UUID.randomUUID().toString())
            .build();

    mockMvc
        .perform(
            post("/api/v1/click-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("필수 필드 누락 시 400 에러")
  void logClickMissingRequiredField() throws Exception {
    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword("노트북")
            .indexName("products")
            .sessionId(UUID.randomUUID().toString())
            .build();

    mockMvc
        .perform(
            post("/api/v1/click-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("빈 검색 키워드로 클릭 로그 저장")
  void logClickWithEmptyKeyword() throws Exception {
    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword("")
            .clickedProductId("PROD-12345")
            .indexName("products")
            .sessionId(UUID.randomUUID().toString())
            .build();

    mockMvc
        .perform(
            post("/api/v1/click-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("세션 ID 없이 클릭 로그 저장")
  void logClickWithoutSessionId() throws Exception {
    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword("노트북")
            .clickedProductId("PROD-12345")
            .indexName("products")
            .build();

    mockMvc
        .perform(
            post("/api/v1/click-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("JSON 형식 오류 시 400 에러")
  void logClickWithInvalidJson() throws Exception {
    String invalidJson = "{\"searchKeyword\": \"노트북\", \"clickedProductId\": }";

    mockMvc
        .perform(
            post("/api/v1/click-logs").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Content-Type 없이 요청 시 500 에러")
  void logClickWithoutContentType() throws Exception {
    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword("노트북")
            .clickedProductId("PROD-12345")
            .indexName("products")
            .sessionId(UUID.randomUUID().toString())
            .build();

    mockMvc
        .perform(post("/api/v1/click-logs").content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError());
  }
}
