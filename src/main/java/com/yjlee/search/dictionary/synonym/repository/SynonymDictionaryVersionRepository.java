package com.yjlee.search.dictionary.synonym.repository;

import com.yjlee.search.dictionary.synonym.model.SynonymDictionaryVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynonymDictionaryVersionRepository
    extends JpaRepository<SynonymDictionaryVersion, Long> {

  // 버전명으로 조회
  Optional<SynonymDictionaryVersion> findByVersion(String version);

  // 버전 존재 여부 확인
  boolean existsByVersion(String version);

  // 모든 버전 조회 (최신순)
  Page<SynonymDictionaryVersion> findAllByOrderByCreatedAtDesc(Pageable pageable);

  // 모든 버전 목록 조회 (페이징 없음)
  List<SynonymDictionaryVersion> findAllByOrderByCreatedAtDesc();

  // 최신 버전 조회
  Optional<SynonymDictionaryVersion> findFirstByOrderByCreatedAtDesc();
}
