package com.yjlee.search.deployment.strategy;

import com.yjlee.search.deployment.model.IndexEnvironment;

public interface EnvironmentDeploymentStrategy {

  boolean canDeploy(IndexEnvironment environment);

  void validateDeployment(IndexEnvironment sourceEnv, IndexEnvironment targetEnv);

  void prepareDeployment(IndexEnvironment environment);

  void postDeployment(IndexEnvironment environment);

  String getEnvironmentName();
}
