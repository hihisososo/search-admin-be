package com.yjlee.search.dictionary.category.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRankingDictionaryRepository
    extends JpaRepository<CategoryRankingDictionary, Long> {

  Optional<CategoryRankingDictionary> findByKeyword(String keyword);

  boolean existsByKeyword(String keyword);

  Page<CategoryRankingDictionary> findByKeywordContainingIgnoreCase(
      String keyword, Pageable pageable);

  List<CategoryRankingDictionary> findAllByOrderByKeywordAsc();

  @Query(
      value =
          "SELECT DISTINCT cm->>'category' as category "
              + "FROM category_ranking_dictionaries c, "
              + "jsonb_array_elements(c.category_mappings::jsonb) cm "
              + "ORDER BY category",
      nativeQuery = true)
  List<String> findDistinctCategories();

  // environment_type 기반 조회
  Page<CategoryRankingDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  List<CategoryRankingDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  Page<CategoryRankingDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  Optional<CategoryRankingDictionary> findByIdAndEnvironmentType(
      Long id, DictionaryEnvironmentType environmentType);

  Optional<CategoryRankingDictionary> findByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
