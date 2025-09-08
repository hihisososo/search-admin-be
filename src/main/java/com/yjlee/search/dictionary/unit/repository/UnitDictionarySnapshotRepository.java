package com.yjlee.search.dictionary.unit.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.unit.model.UnitDictionarySnapshot;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitDictionarySnapshotRepository
    extends JpaRepository<UnitDictionarySnapshot, Long> {
  Page<UnitDictionarySnapshot> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  Page<UnitDictionarySnapshot> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  List<UnitDictionarySnapshot> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
