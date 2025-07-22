package com.yjlee.search.dictionary.stopword.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionarySnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopwordDictionarySnapshotRepository
    extends JpaRepository<StopwordDictionarySnapshot, Long> {

  // 특정 환경의 스냅샷 조회 (페이징)
  Page<StopwordDictionarySnapshot> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  // 특정 환경에서 키워드 검색 (페이징)
  Page<StopwordDictionarySnapshot>
      findByEnvironmentTypeAndKeywordContainingIgnoreCaseOrderByKeywordAsc(
          DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  // 특정 환경의 스냅샷 개수
  long countByEnvironmentType(DictionaryEnvironmentType environmentType);

  // 특정 환경에서 키워드 검색 결과 개수
  long countByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword);

  // 특정 환경의 모든 스냅샷 조회 (페이징 없음)
  List<StopwordDictionarySnapshot> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  // 환경별 스냅샷 존재 여부 확인
  boolean existsByEnvironmentType(DictionaryEnvironmentType environmentType);

  // 특정 환경의 스냅샷 단건 조회 (환경당 하나씩만 존재하는 경우)
  Optional<StopwordDictionarySnapshot> findByEnvironmentTypeAndOriginalDictionaryId(
      DictionaryEnvironmentType environmentType, Long originalDictionaryId);

  // 특정 환경의 모든 스냅샷 삭제
  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
