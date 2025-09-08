package com.yjlee.search.dictionary.typo.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypoCorrectionDictionaryRepository
    extends JpaRepository<TypoCorrectionDictionary, Long> {


  // environment_type 기반 조회
  Page<TypoCorrectionDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  List<TypoCorrectionDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  Page<TypoCorrectionDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
