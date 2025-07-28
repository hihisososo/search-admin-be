package com.yjlee.search.evaluation.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkDeleteRequest {

  @NotEmpty(message = "삭제할 ID 목록은 비어있을 수 없습니다")
  List<Long> ids;
}
