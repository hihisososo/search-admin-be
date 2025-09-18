package com.yjlee.search.dictionary.unit.controller;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitSyncResponse;
import com.yjlee.search.dictionary.unit.service.UnitDictionaryService;
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

@Tag(name = "단위 사전")
@RestController
@RequestMapping("/api/v1/dictionaries/units")
@RequiredArgsConstructor
public class UnitDictionaryController {

  private final UnitDictionaryService unitDictionaryService;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<UnitDictionaryListResponse> getUnitDictionaries(
      @ParameterObject
          @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) EnvironmentType environment) {
    return unitDictionaryService.getList(pageable, search, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{dictionaryId}")
  public UnitDictionaryResponse getUnitDictionaryDetail(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return unitDictionaryService.get(dictionaryId, environment);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UnitDictionaryResponse createUnitDictionary(
      @RequestBody @Valid UnitDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return unitDictionaryService.create(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{dictionaryId}")
  public UnitDictionaryResponse updateUnitDictionary(
      @PathVariable Long dictionaryId,
      @RequestBody @Valid UnitDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return unitDictionaryService.update(dictionaryId, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{dictionaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUnitDictionary(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    unitDictionaryService.delete(dictionaryId, environment);
  }

  @Operation(summary = "실시간 반영 불가")
  @PostMapping("/realtime-sync")
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public UnitSyncResponse syncUnitDictionary(@RequestParam EnvironmentType environment) {
    return UnitSyncResponse.error(environment.getDescription());
  }
}
