package com.yjlee.search.dictionary.category.mapper;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.dto.CategoryMappingDto;
import com.yjlee.search.dictionary.category.dto.CategoryRankingDictionaryListResponse;
import com.yjlee.search.dictionary.category.dto.CategoryRankingDictionaryResponse;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryRankingDictionaryMapper {
    
    @Mapping(target = "categoryMappings", expression = "java(CategoryRankingDictionaryResponse.convertMappings(entity.getCategoryMappings()))")
    CategoryRankingDictionaryResponse toResponse(CategoryRankingDictionary entity);
    
    @Mapping(target = "categoryCount", expression = "java(entity.getCategoryMappings() != null ? entity.getCategoryMappings().size() : 0)")
    CategoryRankingDictionaryListResponse toListResponse(CategoryRankingDictionary entity);
    
    CategoryMapping toMapping(CategoryMappingDto dto);
    
    List<CategoryMapping> toMappings(List<CategoryMappingDto> dtos);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "environmentType", source = "targetEnvironment")
    @Mapping(target = "keyword", source = "source.keyword")
    @Mapping(target = "categoryMappings", source = "source.categoryMappings")
    @Mapping(target = "description", source = "source.description")
    CategoryRankingDictionary copyWithEnvironment(CategoryRankingDictionary source, DictionaryEnvironmentType targetEnvironment);
}