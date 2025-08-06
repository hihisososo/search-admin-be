package com.yjlee.search.deployment.repository;

import com.yjlee.search.deployment.model.DeploymentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentHistoryRepository extends JpaRepository<DeploymentHistory, Long> {

  Page<DeploymentHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query(
      "SELECT dh FROM DeploymentHistory dh "
          + "WHERE (:status IS NULL OR dh.status = :status) "
          + "AND (:deploymentType IS NULL OR dh.deploymentType = :deploymentType)")
  Page<DeploymentHistory> findByFilters(
      @Param("status") DeploymentHistory.DeploymentStatus status,
      @Param("deploymentType") DeploymentHistory.DeploymentType deploymentType,
      Pageable pageable);
}
