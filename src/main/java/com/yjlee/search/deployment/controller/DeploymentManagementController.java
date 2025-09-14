package com.yjlee.search.deployment.controller;

import com.yjlee.search.deployment.dto.DeploymentHistoryListResponse;
import com.yjlee.search.deployment.dto.DeploymentOperationResponse;
import com.yjlee.search.deployment.dto.DeploymentRequest;
import com.yjlee.search.deployment.dto.EnvironmentListResponse;
import com.yjlee.search.deployment.dto.IndexingRequest;
import com.yjlee.search.deployment.dto.IndexingStartResponse;
import com.yjlee.search.deployment.service.DeploymentService;
import com.yjlee.search.deployment.service.EnvironmentQueryService;
import com.yjlee.search.deployment.service.IndexingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deployment")
@RequiredArgsConstructor
@Tag(name = "Deployment Management", description = "배포 관리 API")
public class DeploymentManagementController {

  private final EnvironmentQueryService environmentQueryService;
  private final IndexingService indexingService;
  private final DeploymentService deploymentService;

  @Operation(summary = "환경 정보 조회", description = "개발/운영 환경의 색인 정보를 조회합니다.")
  @GetMapping("/environments")
  public ResponseEntity<EnvironmentListResponse> getEnvironments() {
    return ResponseEntity.ok(environmentQueryService.getEnvironments());
  }

  @Operation(summary = "색인 실행", description = "개발 환경에서 비동기로 색인을 실행합니다.")
  @PostMapping("/indexing")
  public ResponseEntity<IndexingStartResponse> executeIndexing(
      @RequestBody IndexingRequest request) {
    return ResponseEntity.ok(indexingService.executeIndexing(request));
  }

  @Operation(summary = "배포 실행", description = "개발 환경에서 운영 환경으로 배포를 실행합니다.")
  @PostMapping("/deploy")
  public ResponseEntity<DeploymentOperationResponse> executeDeployment(
      @RequestBody DeploymentRequest request) {
    return ResponseEntity.ok(deploymentService.executeDeployment(request));
  }

  @Operation(summary = "배포 이력 조회", description = "배포 이력을 페이징하여 조회합니다.")
  @GetMapping("/history")
  public ResponseEntity<DeploymentHistoryListResponse> getDeploymentHistory(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(environmentQueryService.getDeploymentHistory(pageable));
  }
}
