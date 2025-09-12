package com.yjlee.search.common.util;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;

public class EnvironmentTypeConverter {

  public static DictionaryEnvironmentType toDictionaryEnvironmentType(
      IndexEnvironment.EnvironmentType indexEnvType) {
    if (indexEnvType == null) {
      return DictionaryEnvironmentType.CURRENT;
    }

    return switch (indexEnvType) {
      case DEV -> DictionaryEnvironmentType.DEV;
      case PROD -> DictionaryEnvironmentType.PROD;
    };
  }

  public static IndexEnvironment.EnvironmentType toIndexEnvironmentType(
      DictionaryEnvironmentType dictEnvType) {
    if (dictEnvType == null) {
      return IndexEnvironment.EnvironmentType.PROD;
    }

    return switch (dictEnvType) {
      case CURRENT, PROD -> IndexEnvironment.EnvironmentType.PROD;
      case DEV -> IndexEnvironment.EnvironmentType.DEV;
    };
  }
}
