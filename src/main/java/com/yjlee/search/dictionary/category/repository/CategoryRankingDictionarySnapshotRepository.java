package com.yjlee.search.dictionary.category.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionarySnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRankingDictionarySnapshotRepository
    extends JpaRepository<CategoryRankingDictionarySnapshot, Long> {

  Page<CategoryRankingDictionarySnapshot> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  Page<CategoryRankingDictionarySnapshot> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  Optional<CategoryRankingDictionarySnapshot> findByOriginalDictionaryIdAndEnvironmentType(
      Long originalId, DictionaryEnvironmentType environmentType);

  Optional<CategoryRankingDictionarySnapshot> findByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  List<CategoryRankingDictionarySnapshot> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  List<CategoryRankingDictionarySnapshot> findByEnvironmentType(
      DictionaryEnvironmentType environmentType);
}
