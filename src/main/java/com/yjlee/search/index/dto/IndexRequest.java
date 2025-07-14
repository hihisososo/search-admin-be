package com.yjlee.search.index.dto;

import com.yjlee.search.validation.ValidIndexName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

@Getter
@ToString
@Builder
@Jacksonized
public class IndexRequest {
  @NotBlank(message = "색인명은 필수입니다.")
  @ValidIndexName
  private final String name;

  @NotBlank(message = "dataSource는 필수입니다.")
  @Pattern(regexp = "^(db|json)$", message = "dataSource는 'db' 또는 'json'만 허용됩니다.")
  private final String dataSource;

  private final String jdbcUrl;

  @Size(max = 100, message = "JDBC 사용자명은 100자를 초과할 수 없습니다.")
  @Pattern(regexp = "^[a-zA-Z0-9_.-]*$", message = "JDBC 사용자명에는 영문, 숫자, 점, 하이픈, 언더스코어만 사용 가능합니다.")
  private final String jdbcUser;

  @Size(max = 255, message = "JDBC 패스워드는 255자를 초과할 수 없습니다.")
  private final String jdbcPassword;

  private final String jdbcQuery;

  private final Map<String, Object> mapping;

  private final Map<String, Object> settings;
}
