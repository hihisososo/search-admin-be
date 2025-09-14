package com.yjlee.search.deployment.repository;

import com.yjlee.search.deployment.model.DeploymentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentHistoryRepository extends JpaRepository<DeploymentHistory, Long> {

  Page<DeploymentHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
