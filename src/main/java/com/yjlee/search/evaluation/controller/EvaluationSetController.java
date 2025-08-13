package com.yjlee.search.evaluation.controller;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_DOCUMENT_PAGE_SIZE;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PAGE;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PAGE_SIZE;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PRODUCT_NAME;
import static com.yjlee.search.evaluation.constants.EvaluationConstants.DEFAULT_PRODUCT_SPECS;

import com.yjlee.search.evaluation.dto.AddProductMappingRequest;
import com.yjlee.search.evaluation.dto.AsyncTaskStartResponse;
import com.yjlee.search.evaluation.dto.BulkDeleteRequest;
import com.yjlee.search.evaluation.dto.CandidatePreviewRequest;
import com.yjlee.search.evaluation.dto.CandidatePreviewResponse;
import com.yjlee.search.evaluation.dto.CategoryListResponse;
import com.yjlee.search.evaluation.dto.EvaluationQueryListResponse;
import com.yjlee.search.evaluation.dto.EvaluationQueryListResponse.EvaluationQueryDto;
import com.yjlee.search.evaluation.dto.GenerateCandidatesRequest;
import com.yjlee.search.evaluation.dto.LLMQueryGenerateRequest;
import com.yjlee.search.evaluation.dto.QueryDocumentMappingResponse;
import com.yjlee.search.evaluation.dto.QuerySuggestResponse;
import com.yjlee.search.evaluation.dto.SimpleTextRequest;
import com.yjlee.search.evaluation.dto.UpdateProductMappingRequest;
import com.yjlee.search.evaluation.dto.UpdateQueryRequest;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.service.AsyncEvaluationService;
import com.yjlee.search.evaluation.service.CategoryService;
import com.yjlee.search.evaluation.service.EvaluationCandidateService;
import com.yjlee.search.evaluation.service.EvaluationQueryService;
import com.yjlee.search.evaluation.service.EvaluationStatisticsService;
import com.yjlee.search.evaluation.service.QuerySuggestService;
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
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/evaluation")
@RequiredArgsConstructor
@Tag(name = "Evaluation Set Management", description = "평가셋(쿼리/후보군) 관리 API")
public class EvaluationSetController {

  private final EvaluationQueryService evaluationQueryService;
  private final EvaluationCandidateService evaluationCandidateService;
  private final AsyncEvaluationService asyncEvaluationService;
  private final QuerySuggestService querySuggestService;
  private final CategoryService categoryService;
  private final EvaluationStatisticsService evaluationStatisticsService;
  private final SearchBasedGroundTruthService groundTruthService;

  @GetMapping("/queries")
  @Operation(summary = "평가 쿼리 리스트 조회")
  public ResponseEntity<EvaluationQueryListResponse> getQueries(
      @RequestParam(defaultValue = DEFAULT_PAGE + "") int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE + "") int size,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "DESC") String sortDirection) {

    List<EvaluationQueryDto> queriesWithStats =
        evaluationStatisticsService.getQueriesWithStats(sortBy, sortDirection);

    PaginationUtils.PagedResult<EvaluationQueryDto> pagedResult =
        PaginationUtils.paginate(queriesWithStats, page, size);

    var response =
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

  @PostMapping("/queries/preview-candidates")
  @Operation(summary = "쿼리 후보군 프리뷰 (저장 안 함)")
  public ResponseEntity<CandidatePreviewResponse> previewCandidates(
      @Valid @RequestBody CandidatePreviewRequest request) {
    var r =
        groundTruthService.previewCandidateIds(
            request.getQuery(),
            request.getUseVector() != null && request.getUseVector(),
            request.getUseMorph() == null || request.getUseMorph(),
            request.getUseBigram() == null || request.getUseBigram(),
            request.getPerMethodLimit() != null ? request.getPerMethodLimit() : 50,
            request.getVectorField(),
            request.getVectorMinScore());

    return ResponseEntity.ok(
        CandidatePreviewResponse.builder()
            .query(request.getQuery())
            .vectorIds(r.getVectorIds())
            .morphIds(r.getMorphIds())
            .bigramIds(r.getBigramIds())
            .build());
  }

  @GetMapping("/queries/recommend")
  @Operation(summary = "평가 쿼리 추천")
  public ResponseEntity<QuerySuggestResponse> suggestQueries(
      @RequestParam(defaultValue = "20") Integer count,
      @RequestParam(required = false) Integer minCandidates,
      @RequestParam(required = false) Integer maxCandidates) {
    return ResponseEntity.ok(
        querySuggestService.suggestQueries(count, minCandidates, maxCandidates));
  }

  @GetMapping("/queries/{queryId}/documents")
  @Operation(summary = "쿼리별 문서 매핑 조회")
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
            .map(
                m -> {
                  ProductDocument product =
                      evaluationCandidateService.getProductDetails(m.getProductId());
                  return QueryDocumentMappingResponse.ProductDocumentDto.builder()
                      .productId(m.getProductId())
                      .productName(product != null ? product.getNameRaw() : DEFAULT_PRODUCT_NAME)
                      .specs(product != null ? product.getSpecsRaw() : DEFAULT_PRODUCT_SPECS)
                      .relevanceStatus(m.getRelevanceStatus())
                      .evaluationReason(
                          m.getEvaluationReason() != null ? m.getEvaluationReason() : "")
                      .build();
                })
            .collect(Collectors.toList());

    var response =
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

  @PostMapping("/queries/{queryId}/documents")
  @Operation(summary = "쿼리에 상품 후보군 추가")
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
  @Operation(summary = "상품 후보군 수정")
  public ResponseEntity<Void> updateProductCandidate(
      @PathVariable Long candidateId, @Valid @RequestBody UpdateProductMappingRequest request) {
    evaluationCandidateService.updateProductMappingById(
        candidateId, request.getRelevanceStatus(), request.getEvaluationReason());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/queries/generate-async")
  @Operation(summary = "LLM 쿼리 생성 (비동기)")
  public ResponseEntity<AsyncTaskStartResponse> generateQueriesAsync(
      @Valid @RequestBody LLMQueryGenerateRequest request) {
    Long taskId = asyncEvaluationService.startLLMQueryGeneration(request);
    return ResponseEntity.ok(
        AsyncTaskStartResponse.builder()
            .taskId(taskId)
            .message("LLM 쿼리 생성 작업이 시작되었습니다. 작업 ID: " + taskId)
            .build());
  }

  @PostMapping("/candidates/generate-async")
  @Operation(summary = "후보군 생성 (비동기)")
  public ResponseEntity<AsyncTaskStartResponse> generateCandidatesAsync(
      @Valid @RequestBody GenerateCandidatesRequest request) {
    Long taskId = asyncEvaluationService.startCandidateGeneration(request);
    return ResponseEntity.ok(
        AsyncTaskStartResponse.builder()
            .taskId(taskId)
            .message("후보군 생성 작업이 시작되었습니다. 작업 ID: " + taskId)
            .build());
  }

  @PostMapping("/queries")
  @Operation(summary = "쿼리 생성")
  public ResponseEntity<EvaluationQuery> createQuery(
      @Valid @RequestBody SimpleTextRequest request) {
    EvaluationQuery createdQuery = evaluationQueryService.createQuery(request.getValue());
    return ResponseEntity.ok(createdQuery);
  }

  @PutMapping("/queries/{queryId}")
  @Operation(summary = "쿼리 수정")
  public ResponseEntity<EvaluationQuery> updateQuery(
      @PathVariable Long queryId, @Valid @RequestBody UpdateQueryRequest request) {
    EvaluationQuery updatedQuery =
        evaluationQueryService.updateQuery(queryId, request.getValue(), request.getReviewed());
    return ResponseEntity.ok(updatedQuery);
  }

  @DeleteMapping("/queries")
  @Operation(summary = "쿼리 일괄 삭제")
  public ResponseEntity<Void> deleteQueries(@Valid @RequestBody BulkDeleteRequest request) {
    evaluationQueryService.deleteQueries(request.getIds());
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/candidates")
  @Operation(summary = "후보군 일괄 삭제")
  public ResponseEntity<Void> deleteCandidates(@Valid @RequestBody BulkDeleteRequest request) {
    evaluationCandidateService.deleteProductMappings(request.getIds());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/categories")
  @Operation(summary = "카테고리 리스트")
  public ResponseEntity<CategoryListResponse> listCategories(
      @RequestParam(required = false, defaultValue = "100") Integer size) {
    return ResponseEntity.ok(categoryService.listCategories(size == null ? 100 : size));
  }
}
