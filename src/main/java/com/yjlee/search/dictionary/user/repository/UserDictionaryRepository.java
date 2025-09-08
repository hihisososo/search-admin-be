package com.yjlee.search.dictionary.user.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDictionaryRepository extends JpaRepository<UserDictionary, Long> {

  // 키워드로 검색 (페이징)
  Page<UserDictionary> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);

  // 키워드 검색 결과 개수
  long countByKeywordContainingIgnoreCase(String keyword);

  // 전체 조회 (키워드 오름차순 정렬)
  List<UserDictionary> findAllByOrderByKeywordAsc();

  // environment_type 기반 조회
  Page<UserDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  List<UserDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  Page<UserDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  Optional<UserDictionary> findByIdAndEnvironmentType(
      Long id, DictionaryEnvironmentType environmentType);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
