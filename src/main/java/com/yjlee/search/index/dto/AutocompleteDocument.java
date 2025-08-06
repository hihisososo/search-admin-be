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

  @JsonProperty("name_jamo")
  String nameJamo;

  @JsonProperty("name_chosung")
  String nameChosung;

  @JsonProperty("name_nori")
  String nameNori;

  @JsonProperty("name_jamo_no_space")
  String nameJamoNoSpace;
}
