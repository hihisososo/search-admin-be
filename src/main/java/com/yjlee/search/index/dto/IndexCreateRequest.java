package com.yjlee.search.index.dto;

import com.yjlee.search.validation.ValidIndexName;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class IndexCreateRequest {
  @NotBlank(message = "색인명은 필수입니다.")
  @ValidIndexName
  String name;

  String description;
  Map<String, Object> mappings;
  Map<String, Object> settings;
}
