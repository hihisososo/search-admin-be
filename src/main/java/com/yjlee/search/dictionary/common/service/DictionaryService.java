package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.EnvironmentType;

public interface DictionaryService {

  // 색인 전 준비 (데이터 복사, 파일 업로드, synonym set 생성)
  void preIndexing();

  // 색인 후 정리 (DEV 실시간 동기화)
  default void postIndexing() {}

  // 배포 전 준비 (DEV → PROD 데이터 복사)
  void preDeploy();

  // 배포 후 정리 (DEV 삭제, PROD 실시간 동기화)
  default void postDeploy() {
    deleteByEnvironmentType(EnvironmentType.DEV);
    realtimeSync(EnvironmentType.PROD);
  }

  // 임시 환경으로 배포 (형태소 분석 테스트용)
  void deployToTemp();

  // 환경별 데이터 삭제
  void deleteByEnvironmentType(EnvironmentType environment);

  // 실시간 동기화
  void realtimeSync(EnvironmentType environment);
}
