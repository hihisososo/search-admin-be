package com.yjlee.search.deployment.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSsmCommandService extends SsmCommandService {

  private static final Logger log = LoggerFactory.getLogger(TestSsmCommandService.class);

  // 테스트 실행 기록 저장
  private final ConcurrentHashMap<String, CommandExecutionRecord> executionHistory =
      new ConcurrentHashMap<>();
  private final AtomicInteger commandCounter = new AtomicInteger(0);

  // 테스트 설정
  private boolean simulateFailure = false;
  private boolean simulateTimeout = false;
  private int delayMillis = 100; // 명령 실행 시뮬레이션 지연

  public TestSsmCommandService() {
    super(null); // SsmClient는 사용하지 않음
  }

  @Override
  public SsmCommandResult executeCommand(
      String instanceId, String command, String comment, int timeoutSeconds) {
    return executeCommand(instanceId, List.of(command), comment, timeoutSeconds, false);
  }

  @Override
  public SsmCommandResult executeCommand(
      String instanceId, List<String> commands, String comment, int timeoutSeconds) {
    return executeCommand(instanceId, commands, comment, timeoutSeconds, false);
  }

  @Override
  public SsmCommandResult executeCommand(
      String instanceId,
      List<String> commands,
      String comment,
      int timeoutSeconds,
      boolean isLongRunning) {

    String commandId = "test-cmd-" + commandCounter.incrementAndGet();
    log.info(
        "[TEST] SSM 명령 실행 시뮬레이션 - Instance: {}, Commands: {}, Comment: {}",
        instanceId,
        commands.size(),
        comment);

    // 실행 기록 저장
    CommandExecutionRecord record =
        new CommandExecutionRecord(commandId, instanceId, commands, comment, timeoutSeconds);
    executionHistory.put(commandId, record);

    // 실행 지연 시뮬레이션
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // 실패 시뮬레이션
    if (simulateFailure) {
      log.info("[TEST] 명령 실행 실패 시뮬레이션");
      return SsmCommandResult.failure("FAILED", "Test simulated failure for command: " + commandId);
    }

    // 타임아웃 시뮬레이션
    if (simulateTimeout) {
      log.info("[TEST] 명령 실행 타임아웃 시뮬레이션");
      throw new com.yjlee.search.deployment.exception.DeploymentException(
          "Test simulated timeout for command: " + commandId);
    }

    // 성공 응답
    String output =
        String.format(
            "[TEST] Command executed successfully\n"
                + "Instance: %s\n"
                + "Commands: %s\n"
                + "Comment: %s\n",
            instanceId, String.join("; ", commands), comment);

    log.info("[TEST] 명령 실행 성공 시뮬레이션 - CommandId: {}", commandId);
    return SsmCommandResult.success(output, "");
  }

  // 테스트 헬퍼 메서드들
  public void setSimulateFailure(boolean simulateFailure) {
    this.simulateFailure = simulateFailure;
  }

  public void setSimulateTimeout(boolean simulateTimeout) {
    this.simulateTimeout = simulateTimeout;
  }

  public void setDelayMillis(int delayMillis) {
    this.delayMillis = delayMillis;
  }

  public void reset() {
    this.simulateFailure = false;
    this.simulateTimeout = false;
    this.delayMillis = 100;
    this.executionHistory.clear();
    this.commandCounter.set(0);
  }

  public CommandExecutionRecord getLastExecution() {
    return executionHistory.values().stream().reduce((first, second) -> second).orElse(null);
  }

  public int getExecutionCount() {
    return commandCounter.get();
  }

  public boolean wasCommandExecuted(String instanceId) {
    return executionHistory.values().stream()
        .anyMatch(record -> record.instanceId.equals(instanceId));
  }

  // 실행 기록 클래스
  public static class CommandExecutionRecord {
    public final String commandId;
    public final String instanceId;
    public final List<String> commands;
    public final String comment;
    public final int timeoutSeconds;
    public final long timestamp;

    public CommandExecutionRecord(
        String commandId,
        String instanceId,
        List<String> commands,
        String comment,
        int timeoutSeconds) {
      this.commandId = commandId;
      this.instanceId = instanceId;
      this.commands = List.copyOf(commands);
      this.comment = comment;
      this.timeoutSeconds = timeoutSeconds;
      this.timestamp = System.currentTimeMillis();
    }
  }
}
