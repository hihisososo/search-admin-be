package com.yjlee.search;

import com.yjlee.search.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFoundException(Exception ex, HttpServletRequest request) {
    return buildErrorResponse(request, ex, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleBadRequest(Exception ex, HttpServletRequest request) {
    return buildErrorResponse(request, ex, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
    return buildErrorResponse(request, ex, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public ErrorResponse handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    log.warn(
        "{}: Path: {}, Method: {}",
        ex.getClass().getSimpleName(),
        request.getRequestURI(),
        ex.getMethod());
    return buildErrorResponse(request, ex, HttpStatus.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler({IOException.class, RuntimeException.class, Exception.class})
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorResponse handleServerError(Exception ex, HttpServletRequest request) {
    return buildErrorResponse(request, ex, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ErrorResponse buildErrorResponse(
      HttpServletRequest request, Exception ex, HttpStatus status) {
    log.error("{}: Path: {}", ex.getClass().getSimpleName(), request.getRequestURI(), ex);

    return ErrorResponse.builder()
        .code(status.value())
        .message(ex.getMessage())
        .path(request.getRequestURI())
        .build();
  }
}
