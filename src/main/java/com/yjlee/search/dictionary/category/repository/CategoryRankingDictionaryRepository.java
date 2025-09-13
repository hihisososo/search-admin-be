package com.yjlee.search.dictionary.category.repository;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRankingDictionaryRepository
    extends JpaRepository<CategoryRankingDictionary, Long> {

  Page<CategoryRankingDictionary> findByEnvironmentType(
      EnvironmentType environmentType, Pageable pageable);

  List<CategoryRankingDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      EnvironmentType environmentType);

  Page<CategoryRankingDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      EnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(String keyword, EnvironmentType environmentType);

  void deleteByEnvironmentType(EnvironmentType environmentType);
}
