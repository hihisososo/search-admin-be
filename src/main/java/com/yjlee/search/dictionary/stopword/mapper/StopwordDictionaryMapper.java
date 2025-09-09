package com.yjlee.search.dictionary.stopword.mapper;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StopwordDictionaryMapper {

  StopwordDictionaryResponse toResponse(StopwordDictionary entity);

  StopwordDictionaryListResponse toListResponse(StopwordDictionary entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "environmentType", source = "environment")
  @Mapping(target = "description", source = "request.description")
  @Mapping(target = "keyword", source = "request.keyword")
  StopwordDictionary toEntity(
      StopwordDictionaryCreateRequest request, DictionaryEnvironmentType environment);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "environmentType", source = "targetEnvironment")
  @Mapping(target = "keyword", source = "source.keyword")
  @Mapping(target = "description", source = "source.description")
  StopwordDictionary copyWithEnvironment(
      StopwordDictionary source, DictionaryEnvironmentType targetEnvironment);
}
