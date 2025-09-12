package com.yjlee.search.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TempIndexRefreshResponse {
  private String status;
  private String message;
  private String indexName;
}