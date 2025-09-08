package com.yjlee.search.index.repository;

import com.yjlee.search.index.model.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

  @Query(
      "SELECT DISTINCT p.categoryName FROM Product p WHERE p.categoryName IS NOT NULL ORDER BY p.categoryName")
  List<String> findDistinctCategoryNames();
}
