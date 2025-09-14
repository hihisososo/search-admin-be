package com.yjlee.search.test.mock;

import com.yjlee.search.common.domain.CommandResult;
import com.yjlee.search.common.service.CommandService;
import java.util.List;

public class TestSsmCommandService implements CommandService {

  @Override
  public CommandResult executeCommand(String instanceId, List<String> commands) {
    return CommandResult.success("Test command executed", "");
  }
}
