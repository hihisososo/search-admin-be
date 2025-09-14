package com.yjlee.search.common.service;

import com.yjlee.search.common.domain.CommandResult;
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
public class SsmCommandService implements CommandService {

  private final SsmClient ssmClient;

  private static final int DEFAULT_WAIT_TIME_MS = 3000;
  private static final int MAX_ATTEMPTS = 60;
  private static final int DEFAULT_TIMEOUT_SECONDS = 300;
  private static final String SHELL_SCRIPT_TYPE = "AWS-RunShellScript";

  @Override
  public CommandResult executeCommand(String instanceId, List<String> commands) {

    SendCommandRequest request =
        SendCommandRequest.builder()
            .instanceIds(instanceId)
            .documentName(SHELL_SCRIPT_TYPE)
            .parameters(Collections.singletonMap("commands", commands))
            .comment("SSM Command Execution")
            .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
            .build();

    SendCommandResponse response = ssmClient.sendCommand(request);
    String commandId = response.command().commandId();

    log.info("SSM 명령 전송 완료. CommandId: {}", commandId);

    return waitForCommandCompletion(instanceId, commandId);
  }

  private CommandResult waitForCommandCompletion(String instanceId, String commandId) {

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      try {
        Thread.sleep(DEFAULT_WAIT_TIME_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("명령 실행 대기 중 인터럽트 발생", e);
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
          return CommandResult.success(
              invocationResponse.standardOutputContent(),
              invocationResponse.standardErrorContent());
        } else if (status == CommandInvocationStatus.FAILED
            || status == CommandInvocationStatus.CANCELLED
            || status == CommandInvocationStatus.TIMED_OUT) {
          log.error(
              "SSM 명령 실행 실패. Status: {}, Error: {}",
              status,
              invocationResponse.standardErrorContent());
          return CommandResult.failure(
              status.toString(), invocationResponse.standardErrorContent());
        }
      } catch (SsmException e) {
        log.debug("명령 상태 조회 중 예외 발생 (진행 중일 수 있음): {}", e.getMessage());
      }
    }

    throw new RuntimeException(String.format("SSM 명령 실행 타임아웃. CommandId: %s", commandId));
  }
}
