package com.yjlee.search.evaluation.exception;

public class ReportNotFoundException extends RuntimeException {
  public ReportNotFoundException(Long reportId) {
    super("Report not found: " + reportId);
  }
}
