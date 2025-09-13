package com.yjlee.search.common.util;

import com.yjlee.search.common.enums.EnvironmentType;

public class EnvironmentTypeConverter {

  public static EnvironmentType toEnvironmentType(EnvironmentType type) {
    if (type == null) {
      return EnvironmentType.CURRENT;
    }
    return type;
  }

  public static EnvironmentType toIndexEnvironmentType(EnvironmentType type) {
    if (type == null) {
      return EnvironmentType.PROD;
    }
    if (type == EnvironmentType.CURRENT) {
      return EnvironmentType.PROD;
    }
    return type;
  }
}
