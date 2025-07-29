package com.yjlee.search.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

  @Test
  @DisplayName("ErrorResponse 빌더 패턴으로 생성")
  void should_create_error_response_with_builder() {
    LocalDateTime now = LocalDateTime.now();
    
    ErrorResponse response = ErrorResponse.builder()
        .code(404)
        .message("Resource not found")
        .errorId("ERR-12345")
        .path("/api/resource/123")
        .build();

    assertThat(response.getCode()).isEqualTo(404);
    assertThat(response.getMessage()).isEqualTo("Resource not found");
    assertThat(response.getErrorId()).isEqualTo("ERR-12345");
    assertThat(response.getPath()).isEqualTo("/api/resource/123");
    assertThat(response.getTimestamp()).isNotNull();
    assertThat(response.getTimestamp()).isAfterOrEqualTo(now);
  }

  @Test
  @DisplayName("timestamp 기본값 자동 설정")
  void should_set_default_timestamp_automatically() {
    LocalDateTime beforeCreation = LocalDateTime.now();
    
    ErrorResponse response = ErrorResponse.builder()
        .code(500)
        .message("Internal server error")
        .build();

    LocalDateTime afterCreation = LocalDateTime.now();

    assertThat(response.getTimestamp()).isNotNull();
    assertThat(response.getTimestamp()).isAfterOrEqualTo(beforeCreation);
    assertThat(response.getTimestamp()).isBeforeOrEqualTo(afterCreation);
  }

  @Test
  @DisplayName("명시적 timestamp 설정")
  void should_allow_explicit_timestamp() {
    LocalDateTime customTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
    
    ErrorResponse response = ErrorResponse.builder()
        .code(400)
        .message("Bad request")
        .timestamp(customTimestamp)
        .build();

    assertThat(response.getTimestamp()).isEqualTo(customTimestamp);
  }

  @Test
  @DisplayName("toString 메서드 동작 확인")
  void should_generate_proper_string_representation() {
    ErrorResponse response = ErrorResponse.builder()
        .code(403)
        .message("Forbidden")
        .errorId("ERR-403")
        .path("/api/admin")
        .build();

    String stringRepresentation = response.toString();

    assertThat(stringRepresentation).contains("code=403");
    assertThat(stringRepresentation).contains("message=Forbidden");
    assertThat(stringRepresentation).contains("errorId=ERR-403");
    assertThat(stringRepresentation).contains("path=/api/admin");
    assertThat(stringRepresentation).contains("timestamp=");
  }

  @Test
  @DisplayName("필수 필드만으로 생성")
  void should_create_with_only_required_fields() {
    ErrorResponse response = ErrorResponse.builder()
        .code(200)
        .message("OK")
        .build();

    assertThat(response.getCode()).isEqualTo(200);
    assertThat(response.getMessage()).isEqualTo("OK");
    assertThat(response.getErrorId()).isNull();
    assertThat(response.getPath()).isNull();
    assertThat(response.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("모든 필드가 올바르게 설정됨")
  void should_set_all_fields_correctly() {
    ErrorResponse response = ErrorResponse.builder()
        .code(422)
        .message("Unprocessable entity")
        .errorId("VALIDATION-001")
        .path("/api/users")
        .build();

    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo(422);
    assertThat(response.getMessage()).isEqualTo("Unprocessable entity");
    assertThat(response.getErrorId()).isEqualTo("VALIDATION-001");
    assertThat(response.getPath()).isEqualTo("/api/users");
  }
}