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
public class SsmCommandService {

  private final SsmClient ssmClient;

  private static final int DEFAULT_WAIT_TIME_MS = 3000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 300;
  private static final String SHELL_SCRIPT_TYPE = "AWS-RunShellScript";

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

    try {
      Thread.sleep(DEFAULT_WAIT_TIME_MS);
      GetCommandInvocationRequest invocationRequest =
          GetCommandInvocationRequest.builder().commandId(commandId).instanceId(instanceId).build();

      GetCommandInvocationResponse invocationResponse =
          ssmClient.getCommandInvocation(invocationRequest);

      CommandInvocationStatus status = invocationResponse.status();
      log.debug("명령 상태 확인: {}", status);

      if (status == CommandInvocationStatus.SUCCESS) {
        log.info("SSM 명령 실행 성공. CommandId: {}", commandId);
        return CommandResult.success(
            invocationResponse.standardOutputContent(), invocationResponse.standardErrorContent());
      }
      log.error(
          "SSM 명령 실행 실패. Status: {}, Error: {}", status, invocationResponse.standardErrorContent());
      return CommandResult.failure(status.toString(), invocationResponse.standardErrorContent());
    } catch (Exception e) {
      throw new RuntimeException("명령 상태 조회 중 에러", e);
    }
  }
}
