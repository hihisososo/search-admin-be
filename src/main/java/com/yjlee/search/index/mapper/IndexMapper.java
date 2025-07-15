package com.yjlee.search.index.mapper;

import com.yjlee.search.index.dto.*;
import com.yjlee.search.index.model.IndexMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface IndexMapper {
  @Mapping(target = "docCount", source = "stats.docCount")
  @Mapping(target = "size", source = "stats.size")
  IndexListResponse toIndexListResponse(IndexMetadata metadata, IndexStatsDto stats);

  IndexDownloadResponse toIndexDownloadResponse(String presignedUrl, long indexId);

  IndexResponse toIndexResponse(IndexMetadata metadata);

  @Mapping(target = "docCount", source = "stats.docCount")
  @Mapping(target = "size", source = "stats.size")
  IndexResponse toIndexResponse(IndexMetadata metadata, IndexStatsDto stats);
}
