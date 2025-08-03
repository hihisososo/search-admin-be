package com.yjlee.search.clicklog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import com.yjlee.search.clicklog.service.ClickLogService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClickLogController.class)
class ClickLogControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ClickLogService clickLogService;

  private ClickLogRequest validRequest;
  private ClickLogResponse successResponse;
  private ClickLogResponse failureResponse;

  @BeforeEach
  void setUp() {
    validRequest = ClickLogRequest.builder()
        .searchKeyword("노트북")
        .clickedProductId("PROD-12345")
        .indexName("products")
        .build();

    successResponse = ClickLogResponse.builder()
        .success(true)
        .message("클릭 로그가 성공적으로 저장되었습니다.")
        .timestamp(LocalDateTime.now().toString())
        .build();

    failureResponse = ClickLogResponse.builder()
        .success(false)
        .message("클릭 로그 저장 중 오류가 발생했습니다: 연결 실패")
        .timestamp(LocalDateTime.now().toString())
        .build();
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 클릭 로그 저장 성공")
  void logClick_Success() throws Exception {
    // given
    when(clickLogService.logClick(any(ClickLogRequest.class)))
        .thenReturn(successResponse);

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("클릭 로그가 성공적으로 저장되었습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 클릭 로그 저장 실패")
  void logClick_Failure() throws Exception {
    // given
    when(clickLogService.logClick(any(ClickLogRequest.class)))
        .thenReturn(failureResponse);

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("클릭 로그 저장 중 오류가 발생했습니다: 연결 실패"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 유효하지 않은 요청 (빈 키워드)")
  void logClick_InvalidRequest_EmptyKeyword() throws Exception {
    // given
    ClickLogRequest invalidRequest = ClickLogRequest.builder()
        .searchKeyword("")  // 빈 문자열
        .clickedProductId("PROD-12345")
        .indexName("products")
        .build();

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 유효하지 않은 요청 (null 상품 ID)")
  void logClick_InvalidRequest_NullProductId() throws Exception {
    // given
    ClickLogRequest invalidRequest = ClickLogRequest.builder()
        .searchKeyword("노트북")
        .clickedProductId(null)  // null
        .indexName("products")
        .build();

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 유효하지 않은 요청 (인덱스명 누락)")
  void logClick_InvalidRequest_MissingIndexName() throws Exception {
    // given
    String requestJson = """
        {
            "searchKeyword": "노트북",
            "clickedProductId": "PROD-12345"
        }
        """;

    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/click-logs - 빈 요청 본문")
  void logClick_EmptyRequestBody() throws Exception {
    // when & then
    mockMvc.perform(post("/api/v1/click-logs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }
}