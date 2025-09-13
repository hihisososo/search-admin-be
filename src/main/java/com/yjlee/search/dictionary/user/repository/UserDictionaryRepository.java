package com.yjlee.search.dictionary.user.repository;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDictionaryRepository extends JpaRepository<UserDictionary, Long> {

  // environment_type 기반 조회
  Page<UserDictionary> findByEnvironmentType(EnvironmentType environmentType, Pageable pageable);

  List<UserDictionary> findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType environmentType);

  Page<UserDictionary> findByEnvironmentTypeAndKeywordContainingIgnoreCase(
      EnvironmentType environmentType, String keyword, Pageable pageable);

  boolean existsByKeywordAndEnvironmentType(String keyword, EnvironmentType environmentType);

  void deleteByEnvironmentType(EnvironmentType environmentType);
}
