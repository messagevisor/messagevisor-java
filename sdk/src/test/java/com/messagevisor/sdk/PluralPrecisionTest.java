package com.messagevisor.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PluralPrecisionTest {
  @Test
  void visibleDigitsAndRoundingControlPluralOperandsForRootsAndChildren() {
    try (Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en").build());
        MessagevisorChild child = m.spawn(Map.of())) {
      List<Case> cases = List.of(
          new Case(1, Map.of("minimumFractionDigits", 2), "other"),
          new Case(1, Map.of("minimumSignificantDigits", 3), "other"),
          new Case(1.4, Map.of("maximumFractionDigits", 0), "one"),
          new Case(1.5, Map.of("maximumFractionDigits", 0), "other"),
          new Case(1.005, Map.of(), "other"),
          new Case(1.0001, Map.of(), "one"),
          new Case(1, Map.of("minimumFractionDigits", 2, "maximumSignificantDigits", 1), "one"),
          new Case(1, Map.of("minimumFractionDigits", 2, "trailingZeroDisplay", "stripIfInteger"), "one"),
          new Case(2, Map.of("type", "ordinal"), "two"),
          new Case(1.8, Map.of("maximumFractionDigits", 0, "roundingMode", "floor"), "one"));
      for (Case test : cases) {
        assertThat(m.formatPlural(test.value(), test.options())).as(test.toString()).isEqualTo(test.expected());
        assertThat(child.formatPlural(test.value(), test.options())).as(test.toString()).isEqualTo(test.expected());
      }
      assertThat(m.formatPlural(0, Map.of("locale", "ar"))).isEqualTo("zero");
      assertThat(child.formatPlural(0, Map.of(), EvaluationOptions.builder().locale("ar").build())).isEqualTo("zero");
      assertThat(m.getLocale()).isEqualTo("en");
      assertThat(child.getLocale()).isEqualTo("en");
    }
  }

  @Test
  void nonFiniteValuesSelectOtherWithoutTrapping() {
    for (String locale : List.of("en", "ru", "fr", "ar", "cy")) {
      try (Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale(locale).build())) {
        for (double value : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
          assertThat(m.formatPlural(value)).as(locale).isEqualTo("other");
          assertThat(m.formatPlural(value, true)).as(locale).isEqualTo("other");
        }
      }
    }
  }

  @Test
  void invalidPluralOptionsReportTheEffectiveLocaleAndPreserveThrowing() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    try (Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en")
        .logLevel(LogLevel.ERROR).onDiagnostic(diagnostics::add).build())) {
      for (Map<String, Object> options : List.of(Map.<String, Object>of("type", "unknown"),
          Map.<String, Object>of("minimumFractionDigits", 3, "maximumFractionDigits", 1),
          Map.<String, Object>of("minimumSignificantDigits", 0))) {
        diagnostics.clear();
        assertThatThrownBy(() -> m.formatPlural(1, options, EvaluationOptions.builder().locale("nl").build()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
          assertThat(diagnostic.code()).isEqualTo("invalid_format");
          assertThat(diagnostic.details()).containsEntry("locale", "nl").containsEntry("type", "plural");
        });
      }
    }
  }

  private record Case(double value, Map<String, Object> options, String expected) {}
}
