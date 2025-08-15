package com.yjlee.search.evaluation.service;

@FunctionalInterface
public interface TaskProgressListener {
  void onProgress(int done, int total);
}


