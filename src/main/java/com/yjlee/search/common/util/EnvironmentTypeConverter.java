package com.yjlee.search.common.util;

import com.yjlee.search.common.enums.EnvironmentType;

public class EnvironmentTypeConverter {

  private static final EnvironmentType DEFAULT_ENVIRONMENT = EnvironmentType.PROD;

  public static EnvironmentType toEnvironmentType(EnvironmentType type) {
    return type != null ? type : DEFAULT_ENVIRONMENT;
  }

  public static EnvironmentType toIndexEnvironmentType(EnvironmentType type) {
    if (type == null || type == EnvironmentType.CURRENT) {
      return DEFAULT_ENVIRONMENT;
    }
    return type;
  }
}
