package com.yjlee.search.dictionary.typo.controller;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.typo.dto.*;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "오타교정 사전")
@RestController
@RequestMapping("/api/v1/dictionaries/typos")
@RequiredArgsConstructor
public class TypoCorrectionDictionaryController {

  private final TypoCorrectionDictionaryService typoCorrectionDictionaryService;
  private final SearchService searchService;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<TypoCorrectionDictionaryListResponse> getTypoCorrectionDictionaries(
      @ParameterObject @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) EnvironmentType environment) {
    return typoCorrectionDictionaryService.getTypoCorrectionDictionaries(
        pageable, search, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{dictionaryId}")
  public TypoCorrectionDictionaryResponse getTypoCorrectionDictionaryDetail(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return typoCorrectionDictionaryService.getTypoCorrectionDictionaryDetail(
        dictionaryId, environment);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TypoCorrectionDictionaryResponse createTypoCorrectionDictionary(
      @RequestBody @Valid TypoCorrectionDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return typoCorrectionDictionaryService.createTypoCorrectionDictionary(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{dictionaryId}")
  public TypoCorrectionDictionaryResponse updateTypoCorrectionDictionary(
      @PathVariable Long dictionaryId,
      @RequestBody @Valid TypoCorrectionDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return typoCorrectionDictionaryService.updateTypoCorrectionDictionary(
        dictionaryId, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{dictionaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTypoCorrectionDictionary(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    typoCorrectionDictionaryService.deleteTypoCorrectionDictionary(dictionaryId, environment);
  }

  @Operation(summary = "실시간 반영")
  @PostMapping("/realtime-sync")
  public TypoSyncResponse syncTypoCorrectionDictionary(@RequestParam EnvironmentType environment) {
    searchService.updateTypoCorrectionCacheRealtime(environment);
    return TypoSyncResponse.success(environment.getDescription());
  }
}
