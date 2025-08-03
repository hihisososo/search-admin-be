package com.yjlee.search.deployment.strategy;

import com.yjlee.search.deployment.model.IndexEnvironment;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnvironmentStrategyFactory {

  private final DevEnvironmentStrategy devEnvironmentStrategy;
  private final ProdEnvironmentStrategy prodEnvironmentStrategy;

  public EnvironmentDeploymentStrategy getStrategy(IndexEnvironment.EnvironmentType type) {
    Map<IndexEnvironment.EnvironmentType, EnvironmentDeploymentStrategy> strategies =
        Map.of(
            IndexEnvironment.EnvironmentType.DEV, devEnvironmentStrategy,
            IndexEnvironment.EnvironmentType.PROD, prodEnvironmentStrategy);

    EnvironmentDeploymentStrategy strategy = strategies.get(type);
    if (strategy == null) {
      throw new IllegalArgumentException("지원하지 않는 환경 타입입니다: " + type);
    }

    return strategy;
  }
}
