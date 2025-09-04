package com.yjlee.search.deployment.controller;

import com.yjlee.search.deployment.dto.*;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.service.DeploymentManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
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
    EnvironmentListResponse response = deploymentManagementService.getEnvironments();
    return ResponseEntity.ok(response);
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
    DeploymentOperationResponse response = deploymentManagementService.executeDeployment(request);

    if (response.isSuccess()) {
      return ResponseEntity.ok(response);
    } else {
      return ResponseEntity.badRequest().body(response);
    }
  }

  @Operation(summary = "배포 이력 조회", description = "배포 이력을 페이징하여 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "배포 이력 조회 성공"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @GetMapping("/history")
  public ResponseEntity<DeploymentHistoryListResponse> getDeploymentHistory(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @Parameter(description = "배포 상태 필터") @RequestParam(required = false)
          DeploymentHistory.DeploymentStatus status,
      @Parameter(description = "배포 유형 필터") @RequestParam(required = false)
          DeploymentHistory.DeploymentType deploymentType) {

    log.info(
        "배포 이력 조회 요청 - status: {}, deploymentType: {}, pageable: {}",
        status,
        deploymentType,
        pageable);
    DeploymentHistoryListResponse response =
        deploymentManagementService.getDeploymentHistory(pageable, status, deploymentType);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용하지 않는 인덱스 목록 조회", description = "현재 개발/운영 환경에 연결되지 않은 인덱스 목록을 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @GetMapping("/indices/unused")
  public ResponseEntity<UnusedIndicesResponse> getUnusedIndices() {
    log.info("미사용 인덱스 목록 조회 요청");
    UnusedIndicesResponse response = deploymentManagementService.getUnusedIndices();
    log.info("미사용 인덱스 조회 완료 - {}개 발견", response.getDeletableCount());
    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "사용하지 않는 인덱스 삭제",
      description = "현재 개발/운영 환경에 연결되지 않은 인덱스를 삭제합니다. 안전을 위해 confirmDelete=true 파라미터가 필요합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (confirmDelete 파라미터 누락)"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @DeleteMapping("/indices/unused")
  public ResponseEntity<DeleteUnusedIndicesResponse> deleteUnusedIndices(
      @Parameter(
              description = "삭제 확인 플래그 (안전을 위해 명시적으로 true 필요)",
              required = true,
              example = "true")
          @RequestParam(required = false, defaultValue = "false")
          boolean confirmDelete) {

    if (!confirmDelete) {
      log.warn("미사용 인덱스 삭제 요청 거부 - confirmDelete=false");
      return ResponseEntity.badRequest()
          .body(DeleteUnusedIndicesResponse.of(new ArrayList<>(), new ArrayList<>(), 0));
    }

    log.info("미사용 인덱스 삭제 요청 - confirmDelete=true");
    DeleteUnusedIndicesResponse response = deploymentManagementService.deleteUnusedIndices();

    if (response.isSuccess()) {
      log.info("미사용 인덱스 삭제 완료 - {}개 삭제", response.getDeletedCount());
      return ResponseEntity.ok(response);
    } else {
      log.warn(
          "미사용 인덱스 삭제 부분 실패 - 성공: {}, 실패: {}",
          response.getDeletedCount(),
          response.getFailedCount());
      return ResponseEntity.ok(response); // 부분 성공도 200으로 반환
    }
  }
}
