package com.yjlee.search.dictionary.category.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRankingDictionaryRepository
    extends JpaRepository<CategoryRankingDictionary, Long> {


  // environment_type 기반 조회
  Page<CategoryRankingDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  List<CategoryRankingDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  Page<CategoryRankingDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
