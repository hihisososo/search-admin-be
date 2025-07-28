package com.yjlee.search.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimpleTextRequest {

  @NotBlank(message = "값은 필수입니다")
  private String value;

  public static SimpleTextRequest of(String value) {
    return SimpleTextRequest.builder().value(value).build();
  }
}
