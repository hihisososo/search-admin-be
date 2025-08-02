package com.yjlee.search.search.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.yjlee.search.search")
public class SearchExceptionHandler {

  @ExceptionHandler(InvalidEnvironmentException.class)
  public ResponseEntity<ErrorResponse> handleInvalidEnvironmentException(
      InvalidEnvironmentException e) {
    log.error("Invalid environment error: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("INVALID_ENVIRONMENT", e.getMessage()));
  }

  @ExceptionHandler(SearchException.class)
  public ResponseEntity<ErrorResponse> handleSearchException(SearchException e) {
    log.error("Search error: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("SEARCH_ERROR", "검색 처리 중 오류가 발생했습니다"));
  }

  record ErrorResponse(String code, String message) {}
}
