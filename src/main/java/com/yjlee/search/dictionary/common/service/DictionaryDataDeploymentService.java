package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.dictionary.common.model.DictionaryData;
import com.yjlee.search.dictionary.stopword.service.StopwordDictionaryService;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.dictionary.unit.service.UnitDictionaryService;
import com.yjlee.search.dictionary.user.service.UserDictionaryService;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryDataDeploymentService {

  private final CategoryRankingDictionaryService categoryRankingService;
  private final SynonymDictionaryService synonymService;
  private final UserDictionaryService userService;
  private final StopwordDictionaryService stopwordService;
  private final TypoCorrectionDictionaryService typoCorrectionService;
  private final UnitDictionaryService unitService;

  @Transactional
  public void copyToDevEnvironment(DictionaryData data) {
    log.info("사전 데이터 DEV 환경 복사 시작");

    // 기존 DEV 환경 데이터 삭제
    deleteAllByEnvironment(EnvironmentType.DEV);

    // 메모리 데이터를 DEV 환경으로 저장
    userService.saveToEnvironment(data.getUserWords(), EnvironmentType.DEV);
    stopwordService.saveToEnvironment(data.getStopwords(), EnvironmentType.DEV);
    unitService.saveToEnvironment(data.getUnits(), EnvironmentType.DEV);
    synonymService.saveToEnvironment(data.getSynonyms(), EnvironmentType.DEV);
    categoryRankingService.saveToEnvironment(data.getCategoryRankings(), EnvironmentType.DEV);
    typoCorrectionService.saveToEnvironment(data.getTypoCorrections(), EnvironmentType.DEV);

    log.info("사전 데이터 DEV 환경 복사 완료");
  }

  @Transactional
  public void copyFromDevToProd() {
    log.info("사전 데이터 DEV → PROD 복사 시작");

    // 기존 PROD 환경 데이터 삭제
    deleteAllByEnvironment(EnvironmentType.PROD);

    // DEV에서 PROD로 복사
    userService.copyToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
    stopwordService.copyToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
    unitService.copyToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
    synonymService.copyToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
    categoryRankingService.copyToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
    typoCorrectionService.copyToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);

    log.info("사전 데이터 DEV → PROD 복사 완료");
  }

  @Transactional
  public void preIndexingAll(DictionaryData data) {
    log.info("색인 전 사전 준비작업 시작");
    executeAll(getPreIndexingOperations(), data);
    log.info("색인 전 사전 준비작업 완료");
  }

  @Transactional
  public void realtimeSyncAll(EnvironmentType environment) {
    log.info("모든 사전 실시간 동기화 시작");
    executeAll(getRealtimeSyncOperations(), environment);
    log.info("모든 사전 실시간 동기화 완료");
  }

  @Transactional
  public void syncWithPreloadedData(
      DictionaryData preloadedData, String synonymSetName, String version) {
    log.info("Preloaded 데이터로 사전 동기화 시작 - version: {}", version);

    // 카테고리 랭킹 동기화
    categoryRankingService.syncWithPreloadedData(preloadedData.getCategoryRankings(), version);

    // 동의어 동기화
    synonymService.syncWithPreloadedData(preloadedData.getSynonyms(), synonymSetName);

    // 오타 교정 동기화
    typoCorrectionService.syncWithPreloadedData(preloadedData.getTypoCorrections(), version);

    log.info("Preloaded 데이터로 사전 동기화 완료");
  }

  @Transactional
  public void deleteAllByEnvironment(EnvironmentType environment) {
    if (environment == EnvironmentType.CURRENT) {
      throw new IllegalArgumentException("CURRENT 환경의 사전은 삭제할 수 없습니다");
    }

    log.info("{} 환경 모든 사전 삭제 시작", environment);
    executeAll(getDeleteOperations(), environment);
    log.info("{} 환경 모든 사전 삭제 완료", environment);
  }

  private List<Consumer<DictionaryData>> getPreIndexingOperations() {
    return Arrays.asList(
        synonymService::preIndexing,
        userService::preIndexing,
        stopwordService::preIndexing,
        unitService::preIndexing);
  }

  private List<Consumer<EnvironmentType>> getRealtimeSyncOperations() {
    return Arrays.asList(
        categoryRankingService::realtimeSync,
        synonymService::realtimeSync,
        typoCorrectionService::realtimeSync);
  }

  private List<Consumer<EnvironmentType>> getDeleteOperations() {
    return Arrays.asList(
        categoryRankingService::deleteByEnvironmentType,
        synonymService::deleteByEnvironmentType,
        userService::deleteByEnvironmentType,
        stopwordService::deleteByEnvironmentType,
        typoCorrectionService::deleteByEnvironmentType,
        unitService::deleteByEnvironmentType);
  }

  private <T> void executeAll(List<Consumer<T>> operations, T parameter) {
    operations.forEach(op -> op.accept(parameter));
  }
}
