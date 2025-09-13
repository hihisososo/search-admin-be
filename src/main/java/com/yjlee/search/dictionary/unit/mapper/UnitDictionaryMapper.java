package com.yjlee.search.dictionary.unit.mapper;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UnitDictionaryMapper {

  UnitDictionaryResponse toResponse(UnitDictionary entity);

  UnitDictionaryListResponse toListResponse(UnitDictionary entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "environmentType", source = "environment")
  @Mapping(target = "keyword", source = "request.keyword")
  UnitDictionary toEntity(UnitDictionaryCreateRequest request, EnvironmentType environment);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "environmentType", source = "targetEnvironment")
  @Mapping(target = "keyword", source = "source.keyword")
  UnitDictionary copyWithEnvironment(UnitDictionary source, EnvironmentType targetEnvironment);
}
