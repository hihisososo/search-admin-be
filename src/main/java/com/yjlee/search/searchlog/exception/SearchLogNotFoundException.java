package com.yjlee.search.searchlog.exception;

public class SearchLogNotFoundException extends RuntimeException {
  public SearchLogNotFoundException(String logId) {
    super("Search log not found: " + logId);
  }
}
