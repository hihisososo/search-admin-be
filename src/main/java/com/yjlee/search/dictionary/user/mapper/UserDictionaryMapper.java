package com.yjlee.search.dictionary.user.mapper;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserDictionaryMapper {

  UserDictionaryResponse toResponse(UserDictionary entity);

  UserDictionaryListResponse toListResponse(UserDictionary entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "environmentType", source = "environment")
  @Mapping(target = "description", source = "request.description")
  @Mapping(target = "keyword", source = "request.keyword")
  UserDictionary toEntity(UserDictionaryCreateRequest request, EnvironmentType environment);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "environmentType", source = "targetEnvironment")
  @Mapping(target = "keyword", source = "source.keyword")
  @Mapping(target = "description", source = "source.description")
  UserDictionary copyWithEnvironment(UserDictionary source, EnvironmentType targetEnvironment);
}
