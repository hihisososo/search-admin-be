package com.yjlee.search.dictionary.synonym.repository;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynonymDictionaryRepository extends JpaRepository<SynonymDictionary, Long> {

  Page<SynonymDictionary> findByEnvironmentType(EnvironmentType environmentType, Pageable pageable);

  List<SynonymDictionary> findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType environmentType);

  Page<SynonymDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      EnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(String keyword, EnvironmentType environmentType);

  void deleteByEnvironmentType(EnvironmentType environmentType);
}
