package com.yjlee.search.dictionary.typo.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.typo.dto.*;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "updatedAt") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      @RequestParam(required = false) DictionaryEnvironmentType environment) {
    return typoCorrectionDictionaryService.getTypoCorrectionDictionaries(
        page, size, search, sortBy, sortDir, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{dictionaryId}")
  public TypoCorrectionDictionaryResponse getTypoCorrectionDictionaryDetail(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    return typoCorrectionDictionaryService.getTypoCorrectionDictionaryDetail(
        dictionaryId, environment);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TypoCorrectionDictionaryResponse createTypoCorrectionDictionary(
      @RequestBody @Valid TypoCorrectionDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    return typoCorrectionDictionaryService.createTypoCorrectionDictionary(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{dictionaryId}")
  public TypoCorrectionDictionaryResponse updateTypoCorrectionDictionary(
      @PathVariable Long dictionaryId,
      @RequestBody @Valid TypoCorrectionDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    return typoCorrectionDictionaryService.updateTypoCorrectionDictionary(
        dictionaryId, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{dictionaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTypoCorrectionDictionary(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    typoCorrectionDictionaryService.deleteTypoCorrectionDictionary(dictionaryId, environment);
  }

  @Operation(summary = "실시간 반영")
  @PostMapping("/realtime-sync")
  public TypoSyncResponse syncTypoCorrectionDictionary(
      @RequestParam DictionaryEnvironmentType environment) {
    searchService.updateTypoCorrectionCacheRealtime(environment);
    return TypoSyncResponse.success(environment.getDescription());
  }
}
