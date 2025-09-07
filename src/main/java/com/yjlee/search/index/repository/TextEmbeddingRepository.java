package com.yjlee.search.index.repository;

import com.yjlee.search.index.model.TextEmbedding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TextEmbeddingRepository extends JpaRepository<TextEmbedding, Long> {

  Optional<TextEmbedding> findByHash(String hash);

  @Query("SELECT te FROM TextEmbedding te WHERE te.hash IN :hashes")
  List<TextEmbedding> findByHashIn(@Param("hashes") List<String> hashes);

  boolean existsByHash(String hash);

  void deleteByHash(String hash);

  @Query("SELECT COUNT(te) FROM TextEmbedding te")
  long countEmbeddings();
}
