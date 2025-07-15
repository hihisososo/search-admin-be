package com.yjlee.search;

import com.yjlee.search.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String errorId = generateErrorId();
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("잘못된 요청입니다");

    log.warn(
        "Validation failed. ErrorId: {}, Path: {}, Message: {}",
        errorId,
        request.getRequestURI(),
        msg);

    return ErrorResponse.builder()
        .code(400)
        .message(msg)
        .errorId(errorId)
        .path(request.getRequestURI())
        .build();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    String errorId = generateErrorId();

    log.warn(
        "Illegal argument. ErrorId: {}, Path: {}, Message: {}",
        errorId,
        request.getRequestURI(),
        ex.getMessage());

    return ErrorResponse.builder()
        .code(400)
        .message(ex.getMessage())
        .errorId(errorId)
        .path(request.getRequestURI())
        .build();
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorResponse handleEtc(Exception ex, HttpServletRequest request) {
    String errorId = generateErrorId();

    MDC.put("errorId", errorId);

    log.error(
        "Unexpected error occurred. ErrorId: {}, Path: {}", errorId, request.getRequestURI(), ex);

    MDC.remove("errorId");

    return ErrorResponse.builder()
        .code(500)
        .message("서버 내부 오류가 발생했습니다.")
        .errorId(errorId)
        .path(request.getRequestURI())
        .build();
  }

  private String generateErrorId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
