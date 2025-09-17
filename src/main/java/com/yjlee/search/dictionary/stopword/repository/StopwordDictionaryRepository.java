package com.yjlee.search.dictionary.stopword.repository;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StopwordDictionaryRepository extends JpaRepository<StopwordDictionary, Long> {

  // environment_type 기반 조회
  Page<StopwordDictionary> findByEnvironmentType(
      EnvironmentType environmentType, Pageable pageable);

  List<StopwordDictionary> findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType environmentType);

  Page<StopwordDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      EnvironmentType environmentType, String keyword, Pageable pageable);

  @Query(
      "SELECT s FROM StopwordDictionary s WHERE s.environmentType = :env "
          + "AND (:keyword IS NULL OR :keyword = '' OR LOWER(s.keyword) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<StopwordDictionary> findWithOptionalKeyword(
      @Param("env") EnvironmentType env, @Param("keyword") String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(String keyword, EnvironmentType environmentType);

  void deleteByEnvironmentType(EnvironmentType environmentType);
}
