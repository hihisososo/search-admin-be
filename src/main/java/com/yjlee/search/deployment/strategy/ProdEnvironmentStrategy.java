package com.yjlee.search.deployment.strategy;

import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.service.ElasticsearchSynonymService;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.dictionary.stopword.service.StopwordDictionaryService;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.dictionary.user.service.UserDictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProdEnvironmentStrategy implements EnvironmentDeploymentStrategy {

  private final SynonymDictionaryService synonymDictionaryService;
  private final UserDictionaryService userDictionaryService;
  private final StopwordDictionaryService stopwordDictionaryService;
  private final TypoCorrectionDictionaryService typoCorrectionDictionaryService;
  private final CategoryRankingDictionaryService categoryRankingDictionaryService;
  private final ElasticsearchSynonymService elasticsearchSynonymService;

  @Override
  public boolean canDeploy(IndexEnvironment environment) {
    // 운영 환경은 항상 배포 가능
    return true;
  }

  @Override
  public void validateDeployment(IndexEnvironment sourceEnv, IndexEnvironment targetEnv) {
    // 운영 환경 배포는 추가 검증 필요 없음
    log.info("운영 환경 배포 검증 통과");
  }

  @Override
  public void prepareDeployment(IndexEnvironment environment) {
    log.info("운영 환경 배포 준비 - 사전 배포 시작");
    deployDictionariesToProd();
  }

  @Override
  public void postDeployment(IndexEnvironment environment) {
    log.info("운영 환경 배포 후처리 완료: {}", environment.getIndexName());
    // 필요시 추가 후처리 작업
  }

  @Override
  public String getEnvironmentName() {
    return "PROD";
  }

  private void deployDictionariesToProd() {
    try {
      // 사전 스냅샷 운영 환경 배포
      synonymDictionaryService.deployToProd();
      userDictionaryService.deployToProd();
      stopwordDictionaryService.deployToProd();
      typoCorrectionDictionaryService.deployToProd();
      categoryRankingDictionaryService.deployToProd();

      log.info("모든 사전 운영 환경 배포 및 synonym_set 업데이트 완료");
    } catch (Exception e) {
      log.error("사전 운영 환경 배포 실패", e);
      throw new RuntimeException("사전 운영 환경 배포 실패", e);
    }
  }
}
