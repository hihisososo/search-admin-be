package com.yjlee.search.dictionary.category.repository;

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
          "SELECT DISTINCT JSON_UNQUOTE(JSON_EXTRACT(cm.category_mapping, '$.category')) as category "
              + "FROM category_ranking_dictionaries c, "
              + "JSON_TABLE(c.category_mappings, '$[*]' COLUMNS(category_mapping JSON PATH '$')) cm "
              + "ORDER BY category",
      nativeQuery = true)
  List<String> findDistinctCategories();
}
