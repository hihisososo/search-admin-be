package com.yjlee.search.clicklog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ClickLogServiceTest {

  @Autowired private ClickLogService clickLogService;

  private ClickLogRequest clickLogRequest;

  @BeforeEach
  void setUp() {
    clickLogRequest =
        ClickLogRequest.builder()
            .searchKeyword("노트북")
            .clickedProductId("PROD-12345")
            .indexName("products")
            .sessionId("550e8400-e29b-41d4-a716-446655440000")
            .build();
  }

  @Test
  @DisplayName("클릭 로그 저장 성공")
  void logClickSuccess() {
    ClickLogResponse response = clickLogService.logClick(clickLogRequest);

    assertThat(response).isNotNull();
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getMessage()).contains("성공적으로 저장");
    assertThat(response.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("sessionId 없이 저장")
  void logClickWithoutSessionId() {
    ClickLogRequest requestWithoutSession =
        ClickLogRequest.builder()
            .searchKeyword("마우스")
            .clickedProductId("PROD-67890")
            .indexName("products")
            .build();

    ClickLogResponse response = clickLogService.logClick(requestWithoutSession);

    assertThat(response).isNotNull();
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getMessage()).contains("성공적으로 저장");
  }

  @Test
  @DisplayName("null 요청 처리")
  void logClickNullRequest() {
    assertThatThrownBy(() -> clickLogService.logClick(null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("클릭 로그 저장 실패");
  }

  @Test
  @DisplayName("다양한 키워드 저장")
  void logClickVariousKeywords() {
    String[] keywords = {"노트북", "스마트폰", "태블릿", "이어폰", "키보드"};

    for (String keyword : keywords) {
      ClickLogRequest request =
          ClickLogRequest.builder()
              .searchKeyword(keyword)
              .clickedProductId("PROD-" + keyword.hashCode())
              .indexName("products")
              .build();

      ClickLogResponse response = clickLogService.logClick(request);

      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getMessage()).contains("성공적으로 저장");
    }
  }

  @Test
  @DisplayName("긴 키워드 처리")
  void logClickLongKeyword() {
    String longKeyword = "삼성 갤럭시 노트북 프로 360 15.6인치 인텔 코어 i7 16GB RAM 512GB SSD";

    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword(longKeyword)
            .clickedProductId("PROD-LONG-001")
            .indexName("products")
            .build();

    ClickLogResponse response = clickLogService.logClick(request);

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getTimestamp()).isNotNull();
  }
}
