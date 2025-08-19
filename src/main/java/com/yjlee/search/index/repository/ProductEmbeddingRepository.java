package com.yjlee.search.index.repository;

import com.yjlee.search.index.model.ProductEmbedding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

  Optional<ProductEmbedding> findByProductId(Long productId);

  @Query("SELECT pe FROM ProductEmbedding pe WHERE pe.productId IN :productIds")
  List<ProductEmbedding> findByProductIdIn(@Param("productIds") List<Long> productIds);

  boolean existsByProductId(Long productId);

  void deleteByProductId(Long productId);

  @Query("SELECT COUNT(pe) FROM ProductEmbedding pe")
  long countEmbeddings();
}
