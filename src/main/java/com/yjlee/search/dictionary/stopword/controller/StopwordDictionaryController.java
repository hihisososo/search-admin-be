package com.yjlee.search.dictionary.stopword.controller;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.service.StopwordDictionaryService;
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

@Tag(name = "불용어 사전")
@RestController
@RequestMapping("/api/v1/dictionaries/stopwords")
@RequiredArgsConstructor
public class StopwordDictionaryController {

  private final StopwordDictionaryService stopwordDictionaryService;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<StopwordDictionaryListResponse> getStopwordDictionaries(
      @ParameterObject @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) EnvironmentType environment) {
    return stopwordDictionaryService.getList(pageable, search, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{dictionaryId}")
  public StopwordDictionaryResponse getStopwordDictionaryDetail(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return stopwordDictionaryService.get(dictionaryId, environment);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StopwordDictionaryResponse createStopwordDictionary(
      @RequestBody @Valid StopwordDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return stopwordDictionaryService.create(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{dictionaryId}")
  public StopwordDictionaryResponse updateStopwordDictionary(
      @PathVariable Long dictionaryId,
      @RequestBody @Valid StopwordDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return stopwordDictionaryService.update(dictionaryId, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{dictionaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteStopwordDictionary(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    stopwordDictionaryService.delete(dictionaryId, environment);
  }
}
