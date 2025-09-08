package com.yjlee.search.dictionary.user.repository;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDictionaryRepository extends JpaRepository<UserDictionary, Long> {


  // environment_type 기반 조회
  Page<UserDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environmentType, Pageable pageable);

  List<UserDictionary> findByEnvironmentTypeOrderByKeywordAsc(
      DictionaryEnvironmentType environmentType);

  Page<UserDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(
      String keyword, DictionaryEnvironmentType environmentType);

  void deleteByEnvironmentType(DictionaryEnvironmentType environmentType);
}
