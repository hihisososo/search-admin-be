package com.yjlee.search.dictionary.synonym.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynonymDictionaryRepository extends JpaRepository<SynonymDictionary, Long> {

  // 키워드 존재 여부 확인
  boolean existsByKeyword(String keyword);

  // 키워드로 검색 (페이징)
  Page<SynonymDictionary> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);

  // 키워드 검색 결과 개수
  long countByKeywordContainingIgnoreCase(String keyword);

  // 전체 조회 (키워드 오름차순 정렬)
  List<SynonymDictionary> findAllByOrderByKeywordAsc();

  // 특정 base로 시작하는 규칙 제거/검색
  void deleteByKeywordStartingWithIgnoreCase(String keywordPrefix);

  // environment_type 기반 조회
  Page<SynonymDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  List<SynonymDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  Page<SynonymDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  Optional<SynonymDictionary> findByIdAndEnvironmentType(
      Long id, DictionaryEnvironmentType environmentType);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
