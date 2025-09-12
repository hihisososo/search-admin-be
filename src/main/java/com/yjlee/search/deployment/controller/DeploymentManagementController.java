package com.yjlee.search.deployment.controller;

import com.yjlee.search.deployment.dto.*;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.service.SimpleDeploymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deployment")
@RequiredArgsConstructor
@Tag(name = "Deployment Management", description = "배포 관리 API")
public class DeploymentManagementController {

  private final SimpleDeploymentService deploymentService;

  @Operation(summary = "환경 정보 조회", description = "개발/운영 환경의 색인 정보를 조회합니다.")
  @GetMapping("/environments")
  public ResponseEntity<EnvironmentListResponse> getEnvironments() {
    return ResponseEntity.ok(deploymentService.getEnvironments());
  }

  @Operation(summary = "색인 실행", description = "개발 환경에서 비동기로 색인을 실행합니다.")
  @PostMapping("/indexing")
  public ResponseEntity<IndexingStartResponse> executeIndexing(
      @RequestBody IndexingRequest request) {
    IndexingStartResponse response = deploymentService.executeIndexingWithResponse(request);
    if (response.getTaskId() != null) {
      return ResponseEntity.ok(response);
    } else {
      return ResponseEntity.badRequest().body(response);
    }
  }

  @Operation(summary = "배포 실행", description = "개발 환경에서 운영 환경으로 배포를 실행합니다.")
  @PostMapping("/deploy")
  public ResponseEntity<DeploymentOperationResponse> executeDeployment(
      @RequestBody DeploymentRequest request) {
    DeploymentOperationResponse response = deploymentService.executeDeployment(request);
    if (response.isSuccess()) {
      return ResponseEntity.ok(response);
    } else {
      return ResponseEntity.badRequest().body(response);
    }
  }

  @Operation(summary = "배포 이력 조회", description = "배포 이력을 페이징하여 조회합니다.")
  @GetMapping("/history")
  public ResponseEntity<DeploymentHistoryListResponse> getDeploymentHistory(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @Parameter(description = "배포 상태 필터") @RequestParam(required = false)
          DeploymentHistory.DeploymentStatus status,
      @Parameter(description = "배포 유형 필터") @RequestParam(required = false)
          DeploymentHistory.DeploymentType deploymentType) {
    return ResponseEntity.ok(
        deploymentService.getDeploymentHistory(pageable, status, deploymentType));
  }

  @Operation(summary = "사용하지 않는 인덱스 목록 조회", description = "현재 개발/운영 환경에 연결되지 않은 인덱스 목록을 조회합니다.")
  @GetMapping("/indices/unused")
  public ResponseEntity<UnusedIndicesResponse> getUnusedIndices() {
    return ResponseEntity.ok(deploymentService.getUnusedIndices());
  }

  @Operation(
      summary = "사용하지 않는 인덱스 삭제",
      description = "현재 개발/운영 환경에 연결되지 않은 인덱스를 삭제합니다. 안전을 위해 confirmDelete=true 파라미터가 필요합니다.")
  @DeleteMapping("/indices/unused")
  public ResponseEntity<DeleteUnusedIndicesResponse> deleteUnusedIndices(
      @Parameter(
              description = "삭제 확인 플래그 (안전을 위해 명시적으로 true 필요)",
              required = true,
              example = "true")
          @RequestParam(required = false, defaultValue = "false")
          boolean confirmDelete) {
    DeleteUnusedIndicesResponse response = deploymentService.deleteUnusedIndicesWithConfirmation(confirmDelete);
    if (confirmDelete && !response.isSuccess()) {
      return ResponseEntity.ok(response);
    } else if (!confirmDelete) {
      return ResponseEntity.badRequest().body(response);
    }
    return ResponseEntity.ok(response);
  }
}
