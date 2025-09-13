package com.yjlee.search.dictionary.unit.repository;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitDictionaryRepository extends JpaRepository<UnitDictionary, Long> {

  // environment_type 기반 조회
  Page<UnitDictionary> findByEnvironmentType(EnvironmentType environmentType, Pageable pageable);

  List<UnitDictionary> findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType environmentType);

  Page<UnitDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      EnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(String keyword, EnvironmentType environmentType);

  void deleteByEnvironmentType(EnvironmentType environmentType);
}
