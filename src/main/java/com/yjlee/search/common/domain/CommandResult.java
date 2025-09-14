package com.yjlee.search.common.domain;

public class CommandResult {
  private final boolean success;
  private final String output;
  private final String error;

  private CommandResult(boolean success, String output, String error) {
    this.success = success;
    this.output = output;
    this.error = error;
  }

  public static CommandResult success(String output, String error) {
    return new CommandResult(true, output, error);
  }

  public static CommandResult failure(String output, String error) {
    return new CommandResult(false, output, error);
  }

  public boolean isSuccess() {
    return success;
  }

  public String getOutput() {
    return output;
  }

  public String getError() {
    return error;
  }
}
