package com.yjlee.search.dictionary.synonym.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynonymDictionaryRepository extends JpaRepository<SynonymDictionary, Long> {


  // environment_type 기반 조회
  Page<SynonymDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  List<SynonymDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  Page<SynonymDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
