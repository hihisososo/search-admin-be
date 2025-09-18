package com.yjlee.search.dictionary.user.controller;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.service.UserDictionaryService;
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

@Tag(name = "사용자 사전")
@RestController
@RequestMapping("/api/v1/dictionaries/users")
@RequiredArgsConstructor
public class UserDictionaryController {

  private final UserDictionaryService userDictionaryService;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<UserDictionaryListResponse> getUserDictionaries(
      @ParameterObject
          @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) EnvironmentType environment) {
    return userDictionaryService.getList(pageable, search, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{dictionaryId}")
  public UserDictionaryResponse getUserDictionaryDetail(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return userDictionaryService.get(dictionaryId, environment);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserDictionaryResponse createUserDictionary(
      @RequestBody @Valid UserDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return userDictionaryService.create(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{dictionaryId}")
  public UserDictionaryResponse updateUserDictionary(
      @PathVariable Long dictionaryId,
      @RequestBody @Valid UserDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return userDictionaryService.update(dictionaryId, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{dictionaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUserDictionary(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    userDictionaryService.delete(dictionaryId, environment);
  }
}
