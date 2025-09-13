package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.EnvironmentType;

/** 사전 서비스 공통 인터페이스 각 사전은 이 인터페이스를 구현하여 배포 및 동기화 기능을 제공 */
public interface DictionaryService {

  /**
   * 개발 환경으로 배포 (CURRENT → DEV) 내부적으로 EC2 업로드, 캐시 업데이트, ES 동기화 등을 처리
   *
   * @param version 배포 버전 (EC2 업로드 시 사용)
   */
  void deployToDev(String version);

  /** 운영 환경으로 배포 (DEV → PROD) */
  void deployToProd();

  /**
   * 환경별 데이터 삭제
   *
   * @param environment 삭제할 환경
   */
  void deleteByEnvironmentType(EnvironmentType environment);

  /**
   * 실시간 동기화 (선택적) 캐시 업데이트, ES synonym set 업데이트 등
   *
   * @param environment 동기화할 환경
   */
  default void realtimeSync(EnvironmentType environment) {}
}
