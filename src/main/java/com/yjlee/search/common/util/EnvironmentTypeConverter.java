package com.yjlee.search.common.util;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;

public class EnvironmentTypeConverter {

  /**
   * IndexEnvironment.EnvironmentType을 DictionaryEnvironmentType으로 변환
   *
   * @param indexEnvType 인덱스 환경 타입
   * @return 사전 환경 타입
   */
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

  /**
   * DictionaryEnvironmentType을 IndexEnvironment.EnvironmentType으로 변환
   *
   * @param dictEnvType 사전 환경 타입
   * @return 인덱스 환경 타입 (CURRENT는 PROD로 매핑)
   */
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
