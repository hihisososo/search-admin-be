package com.yjlee.search.async.service;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;

public interface TaskWorker {

  AsyncTaskType getSupportedTaskType();

  void execute(AsyncTask task);
}
