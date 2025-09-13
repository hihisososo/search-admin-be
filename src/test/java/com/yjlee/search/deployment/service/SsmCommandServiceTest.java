package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

@ExtendWith(MockitoExtension.class)
class SsmCommandServiceTest {

  @Mock private SsmClient ssmClient;

  @InjectMocks private SsmCommandService ssmCommandService;

  private static final String TEST_INSTANCE_ID = "i-1234567890";
  private static final String TEST_COMMAND = "echo 'test'";
  private static final String TEST_COMMENT = "테스트 명령";
  private static final int TEST_TIMEOUT = 300;

  @BeforeEach
  void setUp() {
    // 기본 설정
  }

  @Test
  @DisplayName("SSM 명령 실행 성공")
  void executeCommand_Success() throws Exception {
    // Given
    String commandId = "cmd-12345";
    SendCommandResponse sendResponse =
        SendCommandResponse.builder()
            .command(Command.builder().commandId(commandId).build())
            .build();

    GetCommandInvocationResponse invocationResponse =
        GetCommandInvocationResponse.builder()
            .status(CommandInvocationStatus.SUCCESS)
            .standardOutputContent("Command executed successfully")
            .standardErrorContent("")
            .build();

    when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResponse);
    when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class)))
        .thenReturn(invocationResponse);

    // When
    SsmCommandService.SsmCommandResult result =
        ssmCommandService.executeCommand(
            TEST_INSTANCE_ID, TEST_COMMAND, TEST_COMMENT, TEST_TIMEOUT);

    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutput()).isEqualTo("Command executed successfully");
    assertThat(result.getError()).isEmpty();

    verify(ssmClient, times(1)).sendCommand(any(SendCommandRequest.class));
    verify(ssmClient, atLeastOnce()).getCommandInvocation(any(GetCommandInvocationRequest.class));
  }

  @Test
  @DisplayName("SSM 명령 실행 실패")
  void executeCommand_Failed() throws Exception {
    // Given
    String commandId = "cmd-12345";
    SendCommandResponse sendResponse =
        SendCommandResponse.builder()
            .command(Command.builder().commandId(commandId).build())
            .build();

    GetCommandInvocationResponse invocationResponse =
        GetCommandInvocationResponse.builder()
            .status(CommandInvocationStatus.FAILED)
            .standardOutputContent("")
            .standardErrorContent("Command execution failed")
            .build();

    when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResponse);
    when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class)))
        .thenReturn(invocationResponse);

    // When
    SsmCommandService.SsmCommandResult result =
        ssmCommandService.executeCommand(
            TEST_INSTANCE_ID, TEST_COMMAND, TEST_COMMENT, TEST_TIMEOUT);

    // Then
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getError()).isEqualTo("Command execution failed");
  }

  @Test
  @DisplayName("SSM 명령 타임아웃")
  void executeCommand_Timeout() throws Exception {
    // Given
    String commandId = "cmd-12345";
    SendCommandResponse sendResponse =
        SendCommandResponse.builder()
            .command(Command.builder().commandId(commandId).build())
            .build();

    GetCommandInvocationResponse invocationResponse =
        GetCommandInvocationResponse.builder().status(CommandInvocationStatus.IN_PROGRESS).build();

    when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResponse);
    when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class)))
        .thenReturn(invocationResponse);

    // When & Then
    assertThatThrownBy(
            () ->
                ssmCommandService.executeCommand(
                    TEST_INSTANCE_ID, TEST_COMMAND, TEST_COMMENT, TEST_TIMEOUT))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("SSM 명령 실행 타임아웃");
  }

  @Test
  @DisplayName("여러 명령 실행")
  void executeCommand_MultipleCommands() throws Exception {
    // Given
    List<String> commands = List.of("echo 'test1'", "echo 'test2'", "echo 'test3'");
    String commandId = "cmd-12345";

    SendCommandResponse sendResponse =
        SendCommandResponse.builder()
            .command(Command.builder().commandId(commandId).build())
            .build();

    GetCommandInvocationResponse invocationResponse =
        GetCommandInvocationResponse.builder()
            .status(CommandInvocationStatus.SUCCESS)
            .standardOutputContent("All commands executed")
            .standardErrorContent("")
            .build();

    when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResponse);
    when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class)))
        .thenReturn(invocationResponse);

    // When
    SsmCommandService.SsmCommandResult result =
        ssmCommandService.executeCommand(TEST_INSTANCE_ID, commands, TEST_COMMENT, TEST_TIMEOUT);

    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutput()).isEqualTo("All commands executed");
  }

  @Test
  @DisplayName("긴 실행 시간 명령")
  void executeCommand_LongRunning() throws Exception {
    // Given
    String commandId = "cmd-12345";
    SendCommandResponse sendResponse =
        SendCommandResponse.builder()
            .command(Command.builder().commandId(commandId).build())
            .build();

    GetCommandInvocationResponse invocationResponse =
        GetCommandInvocationResponse.builder()
            .status(CommandInvocationStatus.SUCCESS)
            .standardOutputContent("Long running command completed")
            .standardErrorContent("")
            .build();

    when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResponse);
    when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class)))
        .thenThrow(SsmException.builder().message("Not ready").build())
        .thenThrow(SsmException.builder().message("Not ready").build())
        .thenReturn(invocationResponse);

    // When
    SsmCommandService.SsmCommandResult result =
        ssmCommandService.executeCommand(
            TEST_INSTANCE_ID,
            Collections.singletonList(TEST_COMMAND),
            TEST_COMMENT,
            TEST_TIMEOUT,
            true);

    // Then
    assertThat(result.isSuccess()).isTrue();
    verify(ssmClient, times(3)).getCommandInvocation(any(GetCommandInvocationRequest.class));
  }
}
