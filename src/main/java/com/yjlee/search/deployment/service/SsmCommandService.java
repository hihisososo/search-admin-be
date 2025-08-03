package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.exception.DeploymentException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsmCommandService {

  private final SsmClient ssmClient;

  private static final int DEFAULT_WAIT_TIME_MS = 3000;
  private static final int LONG_WAIT_TIME_MS = 5000;
  private static final int MAX_ATTEMPTS_SHORT = 60;
  private static final int MAX_ATTEMPTS_LONG = 120;
  private static final String SHELL_SCRIPT_TYPE = "AWS-RunShellScript";

  public SsmCommandResult executeCommand(
      String instanceId, String command, String comment, int timeoutSeconds) {
    return executeCommand(
        instanceId, Collections.singletonList(command), comment, timeoutSeconds, false);
  }

  public SsmCommandResult executeCommand(
      String instanceId, List<String> commands, String comment, int timeoutSeconds) {
    return executeCommand(instanceId, commands, comment, timeoutSeconds, false);
  }

  public SsmCommandResult executeCommand(
      String instanceId,
      List<String> commands,
      String comment,
      int timeoutSeconds,
      boolean isLongRunning) {

    SendCommandRequest request =
        SendCommandRequest.builder()
            .instanceIds(instanceId)
            .documentName(SHELL_SCRIPT_TYPE)
            .parameters(Collections.singletonMap("commands", commands))
            .comment(comment)
            .timeoutSeconds(timeoutSeconds)
            .build();

    SendCommandResponse response = ssmClient.sendCommand(request);
    String commandId = response.command().commandId();

    log.info("SSM 명령 전송 완료. CommandId: {}, Comment: {}", commandId, comment);

    return waitForCommandCompletion(instanceId, commandId, isLongRunning);
  }

  private SsmCommandResult waitForCommandCompletion(
      String instanceId, String commandId, boolean isLongRunning) {

    int maxAttempts = isLongRunning ? MAX_ATTEMPTS_LONG : MAX_ATTEMPTS_SHORT;
    int waitTime = isLongRunning ? LONG_WAIT_TIME_MS : DEFAULT_WAIT_TIME_MS;

    for (int i = 0; i < maxAttempts; i++) {
      try {
        Thread.sleep(waitTime);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new DeploymentException("명령 실행 대기 중 인터럽트 발생", e);
      }

      GetCommandInvocationRequest invocationRequest =
          GetCommandInvocationRequest.builder().commandId(commandId).instanceId(instanceId).build();

      try {
        GetCommandInvocationResponse invocationResponse =
            ssmClient.getCommandInvocation(invocationRequest);

        CommandInvocationStatus status = invocationResponse.status();
        log.debug("명령 상태 확인: {}", status);

        if (status == CommandInvocationStatus.SUCCESS) {
          log.info("SSM 명령 실행 성공. CommandId: {}", commandId);
          return SsmCommandResult.success(
              invocationResponse.standardOutputContent(),
              invocationResponse.standardErrorContent());
        } else if (status == CommandInvocationStatus.FAILED
            || status == CommandInvocationStatus.CANCELLED
            || status == CommandInvocationStatus.TIMED_OUT) {
          log.error(
              "SSM 명령 실행 실패. Status: {}, Error: {}",
              status,
              invocationResponse.standardErrorContent());
          return SsmCommandResult.failure(
              status.toString(), invocationResponse.standardErrorContent());
        }
      } catch (SsmException e) {
        log.debug("명령 상태 조회 중 예외 발생 (진행 중일 수 있음): {}", e.getMessage());
      }
    }

    throw new DeploymentException(String.format("SSM 명령 실행 타임아웃. CommandId: %s", commandId));
  }

  public static class SsmCommandResult {
    private final boolean success;
    private final String output;
    private final String error;

    private SsmCommandResult(boolean success, String output, String error) {
      this.success = success;
      this.output = output;
      this.error = error;
    }

    public static SsmCommandResult success(String output, String error) {
      return new SsmCommandResult(true, output, error);
    }

    public static SsmCommandResult failure(String output, String error) {
      return new SsmCommandResult(false, output, error);
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
}
