package com.yjlee.search.index.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutocompleteDocument {

  String name;

  @JsonProperty("name_icu")
  String nameIcu;
}