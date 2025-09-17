package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.util.VersionGenerator;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.dictionary.common.model.DictionaryData;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DictionaryDataLoader {

  private final SynonymDictionaryRepository synonymRepository;
  private final StopwordDictionaryRepository stopwordRepository;
  private final UserDictionaryRepository userRepository;
  private final UnitDictionaryRepository unitRepository;
  private final TypoCorrectionDictionaryRepository typoRepository;
  private final CategoryRankingDictionaryRepository categoryRepository;

  @Transactional(readOnly = true)
  public DictionaryData loadAll(EnvironmentType environment) {
    log.info("사전 데이터 로드 시작: environment={}", environment);

    String version = VersionGenerator.generateVersion();

    DictionaryData data =
        DictionaryData.builder()
            .synonyms(loadSynonyms(environment))
            .stopwords(loadStopwords(environment))
            .userWords(loadUserWords(environment))
            .typoCorrections(loadTypoCorrections(environment))
            .units(loadUnits(environment))
            .categoryRankings(loadCategoryRankings(environment))
            .version(version)
            .build();

    log.info(
        "사전 데이터 로드 완료: version={}, synonyms={}, stopwords={}, userWords={}, typos={}, units={}, categories={}",
        version,
        data.getSynonyms().size(),
        data.getStopwords().size(),
        data.getUserWords().size(),
        data.getTypoCorrections().size(),
        data.getUnits().size(),
        data.getCategoryRankings().size());

    return data;
  }

  @Transactional(readOnly = true)
  public DictionaryData loadAll(EnvironmentType environment, String version) {
    log.info("사전 데이터 로드 시작: environment={}, version={}", environment, version);

    DictionaryData data =
        DictionaryData.builder()
            .synonyms(loadSynonyms(environment))
            .stopwords(loadStopwords(environment))
            .userWords(loadUserWords(environment))
            .typoCorrections(loadTypoCorrections(environment))
            .units(loadUnits(environment))
            .categoryRankings(loadCategoryRankings(environment))
            .version(version)
            .build();

    return data;
  }

  private List<SynonymDictionary> loadSynonyms(EnvironmentType environment) {
    return synonymRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  private List<StopwordDictionary> loadStopwords(EnvironmentType environment) {
    return stopwordRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  private List<UserDictionary> loadUserWords(EnvironmentType environment) {
    return userRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  private List<TypoCorrectionDictionary> loadTypoCorrections(EnvironmentType environment) {
    return typoRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  private List<UnitDictionary> loadUnits(EnvironmentType environment) {
    return unitRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  private List<CategoryRankingDictionary> loadCategoryRankings(EnvironmentType environment) {
    return categoryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }
}
