package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.dictionary.common.model.DictionaryData;
import com.yjlee.search.dictionary.stopword.service.StopwordDictionaryService;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.dictionary.unit.service.UnitDictionaryService;
import com.yjlee.search.dictionary.user.service.UserDictionaryService;
import com.yjlee.search.search.service.category.CategoryRankingCacheService;
import com.yjlee.search.search.service.typo.TypoCorrectionCacheService;
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
  private final DictionaryDataLoader dataLoader;
  private final CategoryRankingCacheService categoryRankingCacheService;
  private final TypoCorrectionCacheService typoCorrectionCacheService;

  @Transactional
  public void deployToEnvironment(DictionaryData data, EnvironmentType targetEnvironment) {
    if (targetEnvironment == EnvironmentType.CURRENT) {
      throw new IllegalArgumentException("CURRENT 환경으로는 배포할 수 없습니다");
    }

    log.info("사전 데이터 {} 환경 배포 시작", targetEnvironment);

    userService.saveToEnvironment(data.getUserWords(), targetEnvironment);
    stopwordService.saveToEnvironment(data.getStopwords(), targetEnvironment);
    unitService.saveToEnvironment(data.getUnits(), targetEnvironment);
    synonymService.saveToEnvironment(data.getSynonyms(), targetEnvironment);
    categoryRankingService.saveToEnvironment(data.getCategoryRankings(), targetEnvironment);
    typoCorrectionService.saveToEnvironment(data.getTypoCorrections(), targetEnvironment);

    log.info("사전 데이터 {} 환경 배포 완료", targetEnvironment);
  }

  @Transactional
  public void moveDictionaryDevToProd() {
    log.info("사전 데이터 DEV -> PROD 이동 시작");

    DictionaryData data = dataLoader.loadAll(EnvironmentType.DEV);
    deployToEnvironment(data, EnvironmentType.PROD);
    deleteAllByEnvironment(EnvironmentType.DEV);

    log.info("사전 데이터 DEV -> PROD 이동 완료");
  }

  @Transactional
  public void uploadDictionaries(DictionaryData data) {
    log.info("사전 업로드 시작");

    synonymService.upload(data.getSynonyms(), data.getVersion());
    userService.upload(data.getUserWords(), data.getVersion());
    stopwordService.upload(data.getStopwords(), data.getVersion());
    unitService.upload(data.getUnits(), data.getVersion());

    log.info("사전 업로드 완료");
  }

  @Transactional
  public void sync(DictionaryData preloadedData, String synonymSetName, String version) {
    log.info("사전 동기화 시작 - version: {}", version);

    categoryRankingCacheService.syncWithPreloadedData(preloadedData.getCategoryRankings(), version);
    synonymService.sync(preloadedData.getSynonyms(), synonymSetName);
    typoCorrectionCacheService.syncWithPreloadedData(preloadedData.getTypoCorrections(), version);

    log.info("사전 동기화 완료");
  }

  @Transactional
  public void deleteAllByEnvironment(EnvironmentType environment) {
    if (environment == EnvironmentType.CURRENT) {
      throw new IllegalArgumentException("CURRENT 환경의 사전은 삭제할 수 없습니다");
    }

    log.info("{} 환경 모든 사전 삭제 시작", environment);

    categoryRankingService.deleteByEnvironmentType(environment);
    synonymService.deleteByEnvironmentType(environment);
    userService.deleteByEnvironmentType(environment);
    stopwordService.deleteByEnvironmentType(environment);
    typoCorrectionService.deleteByEnvironmentType(environment);
    unitService.deleteByEnvironmentType(environment);

    log.info("{} 환경 모든 사전 삭제 완료", environment);
  }
}
