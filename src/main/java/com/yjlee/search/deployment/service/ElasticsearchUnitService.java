package com.yjlee.search.deployment.service;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchUnitService {

  private final UnitDictionaryRepository unitDictionaryRepository;

  /**
   * 단위 사전 내용을 가져옴 (인덱스 생성 시 사용)
   *
   * @param environment 환경 타입
   * @return 단위 사전 내용
   */
  public String getUnitDictionaryContent(EnvironmentType environment) {
    log.info("단위 사전 내용 조회 - 환경: {}", environment);

    // DB에서 환경별 단위 사전 조회
    List<UnitDictionary> dictionaries =
        unitDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);

    if (dictionaries.isEmpty()) {
      log.warn("단위 사전 데이터가 없습니다 - 환경: {}", environment);
      return "";
    }

    // 단위 사전 내용 생성 (형식: 원본단위,정규화단위)
    return dictionaries.stream().map(dict -> dict.getKeyword()).collect(Collectors.joining("\n"));
  }

  /**
   * 단위 사전 업데이트 (실시간 반영 불가 - 인덱스 재생성 필요)
   *
   * @param version 버전 정보
   * @param environment 환경 타입
   */
  public void updateUnitDictionary(String version, EnvironmentType environment) throws IOException {
    log.warn("단위 사전은 실시간 반영이 불가능합니다. 인덱스를 재생성해야 합니다.");
    throw new IOException("단위 사전은 실시간 반영이 불가능합니다. 인덱스를 재생성해야 합니다.");
  }
}
