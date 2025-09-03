package com.yjlee.search.dictionary.typo.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionarySnapshot;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypoCorrectionDictionarySnapshotRepository
    extends JpaRepository<TypoCorrectionDictionarySnapshot, Long> {

  // 특정 환경의 스냅샷 조회 (페이징)
  Page<TypoCorrectionDictionarySnapshot> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  // 특정 환경에서 키워드 검색 (페이징)
  Page<TypoCorrectionDictionarySnapshot>
      findByEnvironmentTypeAndKeywordContainingIgnoreCaseOrderByKeywordAsc(
          DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  // 특정 환경의 스냅샷 개수
  long countByEnvironmentType(DictionaryEnvironmentType environmentType);

  // 특정 환경에서 키워드 검색 결과 개수
  long countByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword);

  // 특정 환경의 모든 스냅샷 조회 (페이징 없음)
  List<TypoCorrectionDictionarySnapshot> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  // 환경별 스냅샷 존재 여부 확인
  boolean existsByEnvironmentType(DictionaryEnvironmentType environmentType);

  // 특정 환경의 모든 스냅샷 삭제
  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);

  // 특정 환경의 스냅샷 조회 (페이징)
  Page<TypoCorrectionDictionarySnapshot> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  // 특정 환경에서 키워드 검색 (페이징)
  Page<TypoCorrectionDictionarySnapshot> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);
}
