package com.yjlee.search.analysis.exception;

public class TempIndexNotFoundException extends RuntimeException {

  private static final String DEFAULT_MESSAGE = "임시 인덱스가 존재하지 않습니다";

  public TempIndexNotFoundException() {
    super(DEFAULT_MESSAGE);
  }

  public TempIndexNotFoundException(String message) {
    super(message);
  }
}
