package com.yjlee.search.evaluation.controller;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_DOCUMENT_PAGE_SIZE;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PAGE;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PAGE_SIZE;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PRODUCT_NAME;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PRODUCT_SPECS;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_SORT_BY;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_SORT_DIRECTION;

import com.yjlee.search.evaluation.dto.AddProductMappingRequest;
import com.yjlee.search.evaluation.dto.AsyncTaskListResponse;
import com.yjlee.search.evaluation.dto.AsyncTaskResponse;
import com.yjlee.search.evaluation.dto.AsyncTaskStartResponse;
import com.yjlee.search.evaluation.dto.BulkDeleteRequest;
import com.yjlee.search.evaluation.dto.EvaluationExecuteRequest;
import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.dto.EvaluationQueryListResponse;
import com.yjlee.search.evaluation.dto.GenerateCandidatesRequest;
import com.yjlee.search.evaluation.dto.GenerateQueriesRequest;
import com.yjlee.search.evaluation.dto.LLMEvaluationRequest;
import com.yjlee.search.evaluation.dto.QueryDocumentMappingResponse;
import com.yjlee.search.evaluation.dto.SimpleTextRequest;
import com.yjlee.search.evaluation.dto.UpdateProductMappingRequest;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.service.AsyncEvaluationService;
import com.yjlee.search.evaluation.service.AsyncTaskService;
import com.yjlee.search.evaluation.service.EvaluationCandidateService;
import com.yjlee.search.evaluation.service.EvaluationQueryService;
import com.yjlee.search.evaluation.service.EvaluationReportService;
import com.yjlee.search.evaluation.service.EvaluationStatisticsService;
import com.yjlee.search.evaluation.service.LLMCandidateEvaluationService;
import com.yjlee.search.evaluation.service.QueryGenerationService;
import com.yjlee.search.evaluation.service.SearchBasedGroundTruthService;
import com.yjlee.search.evaluation.util.PaginationUtils;
import com.yjlee.search.index.dto.ProductDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/evaluation")
@RequiredArgsConstructor
@Tag(name = "검색 평가 페이지", description = "검색 평가 관리 API")
public class EvaluationPageController {

  private final EvaluationQueryService evaluationQueryService;
  private final EvaluationCandidateService evaluationCandidateService;
  private final EvaluationStatisticsService evaluationStatisticsService;
  private final EvaluationReportService evaluationReportService;
  private final QueryGenerationService queryGenerationService;
  private final SearchBasedGroundTruthService groundTruthService;
  private final LLMCandidateEvaluationService llmEvaluationService;
  private final AsyncEvaluationService asyncEvaluationService;
  private final AsyncTaskService asyncTaskService;

  @GetMapping("/queries")
  @Operation(summary = "평가 쿼리 리스트 조회", description = "왼쪽 패널에 표시될 쿼리 리스트를 조회합니다")
  public ResponseEntity<EvaluationQueryListResponse> getQueries(
      @RequestParam(defaultValue = DEFAULT_PAGE + "") int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE + "") int size,
      @RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
      @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection) {

    List<EvaluationQueryListResponse.EvaluationQueryDto> allQueryDtos =
        evaluationStatisticsService.getQueriesWithStats(sortBy, sortDirection);

    PaginationUtils.PagedResult<EvaluationQueryListResponse.EvaluationQueryDto> pagedResult =
        PaginationUtils.paginate(allQueryDtos, page, size);

    EvaluationQueryListResponse response =
        EvaluationQueryListResponse.builder()
            .queries(pagedResult.getContent())
            .totalCount(pagedResult.getTotalCount())
            .totalPages(pagedResult.getTotalPages())
            .currentPage(pagedResult.getCurrentPage())
            .size(pagedResult.getSize())
            .hasNext(pagedResult.isHasNext())
            .hasPrevious(pagedResult.isHasPrevious())
            .build();

    return ResponseEntity.ok(response);
  }

  @GetMapping("/queries/{queryId}/documents")
  @Operation(summary = "쿼리별 문서 매핑 조회", description = "선택된 쿼리에 대한 문서 리스트를 조회합니다")
  public ResponseEntity<QueryDocumentMappingResponse> getQueryDocuments(
      @PathVariable Long queryId,
      @RequestParam(defaultValue = DEFAULT_PAGE + "") int page,
      @RequestParam(defaultValue = DEFAULT_DOCUMENT_PAGE_SIZE + "") int size) {

    Optional<EvaluationQuery> queryOpt = evaluationQueryService.getQueryById(queryId);
    if (queryOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    EvaluationQuery query = queryOpt.get();
    List<QueryProductMapping> allMappings =
        evaluationCandidateService.getQueryMappings(query.getQuery());

    PaginationUtils.PagedResult<QueryProductMapping> pagedResult =
        PaginationUtils.paginate(allMappings, page, size);

    List<QueryDocumentMappingResponse.ProductDocumentDto> documents =
        pagedResult.getContent().stream()
            .map(this::convertToProductDocumentDto)
            .collect(Collectors.toList());

    QueryDocumentMappingResponse response =
        QueryDocumentMappingResponse.builder()
            .query(query.getQuery())
            .documents(documents)
            .totalCount(pagedResult.getTotalCount())
            .totalPages(pagedResult.getTotalPages())
            .currentPage(pagedResult.getCurrentPage())
            .size(pagedResult.getSize())
            .hasNext(pagedResult.isHasNext())
            .hasPrevious(pagedResult.isHasPrevious())
            .build();

    return ResponseEntity.ok(response);
  }

  private QueryDocumentMappingResponse.ProductDocumentDto convertToProductDocumentDto(
      QueryProductMapping mapping) {
    ProductDocument product = evaluationCandidateService.getProductDetails(mapping.getProductId());
    return QueryDocumentMappingResponse.ProductDocumentDto.builder()
        .productId(mapping.getProductId())
        .productName(product != null ? product.getNameRaw() : DEFAULT_PRODUCT_NAME)
        .specs(product != null ? product.getSpecsRaw() : DEFAULT_PRODUCT_SPECS)
        .relevanceStatus(mapping.getRelevanceStatus())
        .evaluationReason(
            mapping.getEvaluationReason() != null ? mapping.getEvaluationReason() : "")
        .build();
  }

  @PostMapping("/queries/{queryId}/documents")
  @Operation(summary = "쿼리에 상품 후보군 추가", description = "선택된 쿼리에 상품을 추가합니다")
  public ResponseEntity<Void> addProductToQuery(
      @PathVariable Long queryId, @Valid @RequestBody AddProductMappingRequest request) {
    Optional<EvaluationQuery> queryOpt = evaluationQueryService.getQueryById(queryId);
    if (queryOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    EvaluationQuery query = queryOpt.get();
    evaluationCandidateService.addProductMapping(query.getQuery(), request.getProductId());
    return ResponseEntity.ok().build();
  }

  @PutMapping("/candidates/{candidateId}")
  @Operation(summary = "상품 후보군 수정", description = "상품의 연관성과 이유를 수정합니다")
  public ResponseEntity<Void> updateProductCandidate(
      @PathVariable Long candidateId, @Valid @RequestBody UpdateProductMappingRequest request) {
    evaluationCandidateService.updateProductMappingById(
        candidateId, request.getRelevanceStatus(), request.getEvaluationReason());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/queries/generate-async")
  @Operation(summary = "랜덤 쿼리 생성 (비동기)", description = "지정된 개수만큼 랜덤 쿼리를 비동기로 생성합니다")
  public ResponseEntity<AsyncTaskStartResponse> generateQueriesAsync(
      @Valid @RequestBody GenerateQueriesRequest request) {
    Long taskId = asyncEvaluationService.startQueryGeneration(request);
    return ResponseEntity.ok(
        AsyncTaskStartResponse.builder()
            .taskId(taskId)
            .message("쿼리 생성 작업이 시작되었습니다. 작업 ID: " + taskId)
            .build());
  }

  @PostMapping("/candidates/generate-async")
  @Operation(summary = "검색 기반 후보군 생성 (비동기)", description = "선택된 쿼리들에 대한 상품 후보군을 비동기로 생성합니다")
  public ResponseEntity<AsyncTaskStartResponse> generateCandidatesAsync(
      @RequestBody GenerateCandidatesRequest request) {
    Long taskId = asyncEvaluationService.startCandidateGeneration(request);
    return ResponseEntity.ok(
        AsyncTaskStartResponse.builder()
            .taskId(taskId)
            .message("후보군 생성 작업이 시작되었습니다. 작업 ID: " + taskId)
            .build());
  }

  @PostMapping("/candidates/evaluate-llm-async")
  @Operation(summary = "LLM 자동 후보군 평가 (비동기)", description = "선택된 쿼리들의 후보군을 비동기로 LLM 평가합니다")
  public ResponseEntity<AsyncTaskStartResponse> evaluateCandidatesWithLLMAsync(
      @RequestBody LLMEvaluationRequest request) {
    Long taskId = asyncEvaluationService.startLLMEvaluation(request);
    return ResponseEntity.ok(
        AsyncTaskStartResponse.builder()
            .taskId(taskId)
            .message("LLM 평가 작업이 시작되었습니다. 작업 ID: " + taskId)
            .build());
  }

  // 비동기 작업 상태 조회 API들
  @GetMapping("/tasks/{taskId}")
  @Operation(summary = "비동기 작업 상태 조회", description = "특정 작업의 진행 상태를 조회합니다")
  public ResponseEntity<AsyncTaskResponse> getTaskStatus(@PathVariable Long taskId) {
    return asyncTaskService
        .getTask(taskId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/tasks")
  @Operation(summary = "비동기 작업 리스트 조회", description = "최근 작업들의 리스트를 조회합니다")
  public ResponseEntity<AsyncTaskListResponse> getTasks(
      @RequestParam(defaultValue = DEFAULT_PAGE + "") int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE + "") int size) {
    AsyncTaskListResponse response = asyncTaskService.getRecentTasks(page, size);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/tasks/running")
  @Operation(summary = "실행 중인 작업 조회", description = "현재 실행 중이거나 대기 중인 작업들을 조회합니다")
  public ResponseEntity<List<AsyncTaskResponse>> getRunningTasks() {
    List<AsyncTaskResponse> runningTasks = asyncTaskService.getRunningTasks();
    return ResponseEntity.ok(runningTasks);
  }

  @PostMapping("/evaluate")
  @Operation(summary = "평가 실행", description = "precision, recall을 계산하고 평가 리포트를 생성합니다")
  public ResponseEntity<EvaluationExecuteResponse> executeEvaluation(
      @Valid @RequestBody EvaluationExecuteRequest request) {
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(
            request.getReportName(), request.getRetrievalSize());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/reports")
  @Operation(summary = "평가 리포트 리스트 조회", description = "생성된 평가 리포트 리스트를 조회합니다")
  public ResponseEntity<List<EvaluationReport>> getReports() {
    List<EvaluationReport> reports = evaluationReportService.getAllReports();
    return ResponseEntity.ok(reports);
  }

  @GetMapping("/reports/{reportId}")
  @Operation(summary = "평가 리포트 상세 조회", description = "특정 평가 리포트의 상세 정보를 조회합니다")
  public ResponseEntity<EvaluationReport> getReport(@PathVariable Long reportId) {
    EvaluationReport report = evaluationReportService.getReportById(reportId);
    if (report == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(report);
  }

  @PostMapping("/queries")
  @Operation(summary = "쿼리 생성", description = "새로운 평가 쿼리를 생성합니다")
  public ResponseEntity<EvaluationQuery> createQuery(
      @Valid @RequestBody SimpleTextRequest request) {
    EvaluationQuery createdQuery = evaluationQueryService.createQuery(request.getValue());
    return ResponseEntity.ok(createdQuery);
  }

  @PutMapping("/queries/{queryId}")
  @Operation(summary = "쿼리 수정", description = "기존 쿼리를 수정합니다")
  public ResponseEntity<EvaluationQuery> updateQuery(
      @PathVariable Long queryId, @Valid @RequestBody SimpleTextRequest request) {
    EvaluationQuery updatedQuery = evaluationQueryService.updateQuery(queryId, request.getValue());
    return ResponseEntity.ok(updatedQuery);
  }

  @DeleteMapping("/queries")
  @Operation(summary = "쿼리 일괄 삭제", description = "여러 쿼리를 한 번에 삭제합니다")
  public ResponseEntity<Void> deleteQueries(@Valid @RequestBody BulkDeleteRequest request) {
    evaluationQueryService.deleteQueries(request.getIds());
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/candidates")
  @Operation(summary = "후보군 일괄 삭제", description = "여러 후보군을 한 번에 삭제합니다")
  public ResponseEntity<Void> deleteCandidates(@Valid @RequestBody BulkDeleteRequest request) {
    evaluationCandidateService.deleteProductMappings(request.getIds());
    return ResponseEntity.ok().build();
  }
}
