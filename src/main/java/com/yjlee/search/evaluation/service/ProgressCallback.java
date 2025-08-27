package com.yjlee.search.evaluation.service;

@FunctionalInterface
public interface ProgressCallback {
  void updateProgress(int progress, String message);
}