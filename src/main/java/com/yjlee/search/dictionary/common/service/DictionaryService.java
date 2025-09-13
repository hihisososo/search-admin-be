package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.EnvironmentType;

public interface DictionaryService {

  // 개발 환경으로 배포
  void deployToDev(String version);

  // 운영 환경으로 배포
  void deployToProd();

  // 임시 환경으로 배포 (형태소 분석 테스트용)
  void deployToTemp();

  // 환경별 데이터 삭제
  void deleteByEnvironmentType(EnvironmentType environment);

  // 실시간 동기화
  default void realtimeSync(EnvironmentType environment) {}
}
