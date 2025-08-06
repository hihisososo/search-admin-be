package com.yjlee.search.deployment.controller;

import com.yjlee.search.deployment.dto.*;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.service.DeploymentManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/deployment")
@RequiredArgsConstructor
@Tag(name = "Deployment Management", description = "배포 관리 API")
public class DeploymentManagementController {

  private final DeploymentManagementService deploymentManagementService;

  @Operation(summary = "환경 정보 조회", description = "개발/운영 환경의 색인 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "환경 정보 조회 성공"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @GetMapping("/environments")
  public ResponseEntity<EnvironmentListResponse> getEnvironments() {
    try {
      EnvironmentListResponse response = deploymentManagementService.getEnvironments();
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("환경 정보 조회 실패", e);
      throw new RuntimeException("환경 정보 조회에 실패했습니다.");
    }
  }

  @Operation(summary = "색인 실행", description = "개발 환경에서 색인을 실행합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "색인 시작 성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @PostMapping("/indexing")
  public ResponseEntity<DeploymentOperationResponse> executeIndexing(
      @RequestBody IndexingRequest request) {

    DeploymentOperationResponse response = deploymentManagementService.executeIndexing(request);

    if (response.isSuccess()) {
      return ResponseEntity.ok(response);
    } else {
      return ResponseEntity.badRequest().body(response);
    }
  }

  @Operation(summary = "배포 실행", description = "개발 환경에서 운영 환경으로 배포를 실행합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "배포 성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @PostMapping("/deploy")
  public ResponseEntity<DeploymentOperationResponse> executeDeployment(
      @RequestBody DeploymentRequest request) {

    try {
      DeploymentOperationResponse response = deploymentManagementService.executeDeployment(request);

      if (response.isSuccess()) {
        return ResponseEntity.ok(response);
      } else {
        return ResponseEntity.badRequest().body(response);
      }
    } catch (Exception e) {
      log.error("배포 실행 실패", e);
      return ResponseEntity.internalServerError()
          .body(DeploymentOperationResponse.failure("배포 실행 중 오류가 발생했습니다."));
    }
  }

  @Operation(summary = "배포 이력 조회", description = "배포 이력을 페이징하여 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "배포 이력 조회 성공"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @GetMapping("/history")
  public ResponseEntity<DeploymentHistoryListResponse> getDeploymentHistory(
      @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @Parameter(description = "배포 상태 필터") @RequestParam(required = false)
          DeploymentHistory.DeploymentStatus status,
      @Parameter(description = "배포 유형 필터") @RequestParam(required = false)
          DeploymentHistory.DeploymentType deploymentType) {

    try {
      log.info("배포 이력 조회 요청 - status: {}, deploymentType: {}, pageable: {}", 
              status, deploymentType, pageable);
      DeploymentHistoryListResponse response =
          deploymentManagementService.getDeploymentHistory(pageable, status, deploymentType);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("배포 이력 조회 실패", e);
      throw new RuntimeException("배포 이력 조회에 실패했습니다: " + e.getMessage(), e);
    }
  }
}
