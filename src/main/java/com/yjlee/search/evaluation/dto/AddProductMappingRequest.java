package com.yjlee.search.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddProductMappingRequest {

  @NotBlank(message = "상품 ID는 필수입니다")
  private String productId;
}
