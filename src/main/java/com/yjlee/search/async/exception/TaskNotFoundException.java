package com.yjlee.search.async.exception;

public class TaskNotFoundException extends RuntimeException {
  public TaskNotFoundException(Long taskId) {
    super("Task not found: " + taskId);
  }
}