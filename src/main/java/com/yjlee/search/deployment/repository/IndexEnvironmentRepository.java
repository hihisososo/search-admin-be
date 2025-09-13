package com.yjlee.search.deployment.repository;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexEnvironmentRepository extends JpaRepository<IndexEnvironment, Long> {

  Optional<IndexEnvironment> findByEnvironmentType(EnvironmentType environmentType);

  boolean existsByEnvironmentType(EnvironmentType environmentType);
}
