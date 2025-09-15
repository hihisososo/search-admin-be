package com.yjlee.search.common.service;

import com.yjlee.search.common.domain.CommandResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CommandService {

  public CommandResult executeCommand(String instanceId, List<String> scripts) {
    log.info("Executing command on instance: {}", instanceId);
    return CommandResult.success("Command executed successfully", null);
  }
}
