package com.yjlee.search.common.service;

import com.yjlee.search.common.domain.CommandResult;
import java.util.List;

public interface CommandService {

  CommandResult executeCommand(String instanceId, List<String> commands);
}
