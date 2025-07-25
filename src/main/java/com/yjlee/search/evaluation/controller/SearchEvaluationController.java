package com.yjlee.search.evaluation.controller;

import com.yjlee.search.evaluation.dto.GenerateQueryRequest;
import com.yjlee.search.evaluation.dto.SearchEvaluationRequest;
import com.yjlee.search.evaluation.dto.SearchEvaluationResponse;
import com.yjlee.search.evaluation.dto.TestPromptRequest;
import com.yjlee.search.evaluation.dto.TestPromptResponse;
import com.yjlee.search.evaluation.service.GroundTruthGenerationService;
import com.yjlee.search.evaluation.service.LLMService;
import com.yjlee.search.evaluation.service.QueryGenerationService;
import com.yjlee.search.evaluation.service.SearchEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "검색 평가", description = "2단계 LLM 기반 정확한 검색 평가 시스템")
@RestController
@RequestMapping("/api/v1/evaluation")
@RequiredArgsConstructor
public class SearchEvaluationController {

  private final QueryGenerationService queryGenerationService;
  private final GroundTruthGenerationService groundTruthGenerationService;
  private final SearchEvaluationService searchEvaluationService;
  private final LLMService llmService;

  @Operation(summary = "쿼리 생성", description = "쿼리 생성")
  @PostMapping("/generate-queries")
  public ResponseEntity<String> generateQueries(@RequestBody GenerateQueryRequest request) {
    try {
      queryGenerationService.generateCandidateQueries(request);
      return ResponseEntity.ok("쿼리 생성이 시작되었습니다");
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(summary = "정답셋 생성", description = "생성된 쿼리와 상품간의 정답셋을 LLM으로 생성합니다")
  @PostMapping("/generate-ground-truth")
  public ResponseEntity<String> generateGroundTruth() {
    try {
      groundTruthGenerationService.generateGroundTruth();
      return ResponseEntity.ok("정답셋 생성이 완료되었습니다");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(summary = "검색 평가", description = "정답셋을 기반으로 검색 품질(정확률, 재현률)을 평가합니다")
  @PostMapping("/evaluate")
  public ResponseEntity<SearchEvaluationResponse> evaluateSearch(
      @RequestBody SearchEvaluationRequest request) {
    try {
      SearchEvaluationResponse response = searchEvaluationService.evaluateSearch(request);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @Operation(summary = "모든 쿼리 평가", description = "저장된 모든 쿼리에 대해 일괄 평가를 수행하고 결과를 저장합니다")
  @PostMapping("/evaluate-all")
  public ResponseEntity<String> evaluateAllQueries() {
    try {
      String result = searchEvaluationService.evaluateAllQueries();
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(summary = "테스트 프롬프트", description = "테스트용 프롬프트를 LLM에 전송하고 응답을 받습니다")
  @PostMapping("/test-prompt")
  public ResponseEntity<TestPromptResponse> testPrompt(@RequestBody TestPromptRequest request) {
    try {
      long startTime = System.currentTimeMillis();
      String response = llmService.callLLMAPI(request.getPrompt());
      long endTime = System.currentTimeMillis();

      TestPromptResponse testResponse =
          TestPromptResponse.builder()
              .prompt(request.getPrompt())
              .response(response)
              .responseTimeMs(endTime - startTime)
              .build();

      return ResponseEntity.ok(testResponse);
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @Operation(summary = "파일 프롬프트 테스트", description = "파일로 프롬프트를 업로드하여 LLM 테스트")
  @PostMapping(value = "/test-prompt-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<TestPromptResponse> testPromptFile(
      @RequestParam("file") MultipartFile file) {
    try {
      if (file.isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      String prompt = new String(file.getBytes(), "UTF-8");

      long startTime = System.currentTimeMillis();
      String response = llmService.callLLMAPI(prompt);
      long endTime = System.currentTimeMillis();

      TestPromptResponse testResponse =
          TestPromptResponse.builder()
              .prompt(prompt)
              .response(response)
              .responseTimeMs(endTime - startTime)
              .build();

      return ResponseEntity.ok(testResponse);
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }
}
