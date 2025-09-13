package com.yjlee.search.dictionary.typo.repository;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypoCorrectionDictionaryRepository
    extends JpaRepository<TypoCorrectionDictionary, Long> {

  // environment_type 기반 조회
  Page<TypoCorrectionDictionary> findByEnvironmentType(
      EnvironmentType environmentType, Pageable pageable);

  List<TypoCorrectionDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      EnvironmentType environmentType);

  Page<TypoCorrectionDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      EnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(String keyword, EnvironmentType environmentType);

  void deleteByEnvironmentType(EnvironmentType environmentType);
}
