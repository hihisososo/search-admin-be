package com.yjlee.search.search.service.builder.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yjlee.search.search.service.builder.model.QueryContext;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryProcessorTest {

  @Mock private TypoCorrectionService typoCorrectionService;

  @InjectMocks private QueryProcessor queryProcessor;

  @BeforeEach
  void setUp() {
    // 오타 교정 서비스 목킹
    when(typoCorrectionService.applyTypoCorrection(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  @DisplayName("10kg 검색 시 단위 확장이 정상 동작한다")
  void testUnitExpansionFor10kg() {
    // given
    String query = "10kg";

    // when
    QueryContext context = queryProcessor.analyzeQuery(query, false);

    // then
    assertThat(context.getOriginalQuery()).isEqualTo("10kg");
    assertThat(context.hasUnits()).isTrue();
    assertThat(context.getUnits()).contains("10kg");

    // 확장된 단위 확인
    assertThat(context.hasExpandedUnits()).isTrue();
    Set<String> expandedUnits = context.getExpandedUnits().get("10kg");
    assertThat(expandedUnits).isNotNull();
    assertThat(expandedUnits).contains("10kg", "10킬로그램", "10킬로", "10키로");

    // 단위 제거 후 쿼리가 비어있는지 확인
    assertThat(context.getQueryWithoutTerms()).isEmpty();
    assertThat(context.isQueryEmptyAfterRemoval()).isTrue();
  }

  @Test
  @DisplayName("삼성 10kg 세탁기 검색 시 단위 확장과 쿼리 분리가 정상 동작한다")
  void testUnitExpansionWithOtherTerms() {
    // given
    String query = "삼성 10kg 세탁기";

    // when
    QueryContext context = queryProcessor.analyzeQuery(query, false);

    // then
    assertThat(context.getOriginalQuery()).isEqualTo("삼성 10kg 세탁기");
    assertThat(context.hasUnits()).isTrue();
    assertThat(context.getUnits()).contains("10kg");

    // 확장된 단위 확인
    assertThat(context.hasExpandedUnits()).isTrue();
    Set<String> expandedUnits = context.getExpandedUnits().get("10kg");
    assertThat(expandedUnits).isNotNull();
    assertThat(expandedUnits).contains("10kg", "10킬로그램", "10킬로", "10키로");

    // 단위 제거 후 쿼리 확인
    assertThat(context.getQueryWithoutTerms()).isEqualTo("삼성 세탁기");
    assertThat(context.isQueryEmptyAfterRemoval()).isFalse();
  }

  @Test
  @DisplayName("여러 단위가 있을 때 각각 확장된다")
  void testMultipleUnitsExpansion() {
    // given
    String query = "10kg 500ml";

    // when
    QueryContext context = queryProcessor.analyzeQuery(query, false);

    // then
    assertThat(context.hasUnits()).isTrue();
    assertThat(context.getUnits()).hasSize(2);
    assertThat(context.getUnits()).contains("10kg", "500ml");

    // 각 단위가 확장되었는지 확인
    assertThat(context.hasExpandedUnits()).isTrue();

    Set<String> expandedKg = context.getExpandedUnits().get("10kg");
    assertThat(expandedKg).isNotNull();
    assertThat(expandedKg).contains("10kg", "10킬로그램", "10킬로", "10키로");

    Set<String> expandedMl = context.getExpandedUnits().get("500ml");
    assertThat(expandedMl).isNotNull();
    assertThat(expandedMl).contains("500ml", "500밀리리터", "500밀리");

    // 모든 단위 제거 후 쿼리가 비어있는지 확인
    assertThat(context.getQueryWithoutTerms()).isEmpty();
    assertThat(context.isQueryEmptyAfterRemoval()).isTrue();
  }
}
