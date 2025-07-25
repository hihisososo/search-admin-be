package com.yjlee.search.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductInfoDto {
  private final String id;
  private final String nameRaw;
  private final String specsRaw;
}
