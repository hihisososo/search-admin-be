package com.yjlee.search.search.service.builder.query;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.dto.PriceRangeDto;
import com.yjlee.search.search.dto.ProductFiltersDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FilterQueryBuilder {

  public List<Query> buildFilterQueries(ProductFiltersDto filters) {
    if (filters == null) {
      return new ArrayList<>();
    }

    List<Query> filterQueries = new ArrayList<>();

    addTermsFilter(filterQueries, ESFields.BRAND_NAME, filters.getBrand());
    addTermsFilter(filterQueries, ESFields.CATEGORY_NAME, filters.getCategory());
    addPriceRangeFilter(filterQueries, filters.getPriceRange());

    return filterQueries;
  }

  private void addTermsFilter(List<Query> filterQueries, String field, List<String> values) {
    Optional.ofNullable(values)
        .filter(list -> !list.isEmpty())
        .ifPresent(
            list -> {
              Query termsQuery =
                  Query.of(
                      q ->
                          q.terms(
                              t ->
                                  t.field(field)
                                      .terms(
                                          terms ->
                                              terms.value(
                                                  list.stream().map(FieldValue::of).toList()))));
              filterQueries.add(termsQuery);
            });
  }

  private void addPriceRangeFilter(List<Query> filterQueries, PriceRangeDto priceRange) {
    Optional.ofNullable(priceRange)
        .filter(pr -> pr.getFrom() != null || pr.getTo() != null)
        .ifPresent(
            pr -> {
              Query rangeQuery =
                  Query.of(
                      q ->
                          q.range(
                              r ->
                                  r.number(
                                      n -> {
                                        n.field(ESFields.PRICE);

                                        Optional.ofNullable(pr.getFrom())
                                            .map(Number::doubleValue)
                                            .ifPresent(n::gte);

                                        Optional.ofNullable(pr.getTo())
                                            .map(Number::doubleValue)
                                            .ifPresent(n::lte);

                                        return n;
                                      })));
              filterQueries.add(rangeQuery);
            });
  }
}
