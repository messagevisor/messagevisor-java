package com.messagevisor.modules.icu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messagevisor.sdk.EvaluationOptions;
import com.messagevisor.sdk.FormatPresets;
import com.messagevisor.sdk.LogLevel;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorDiagnostic;
import com.messagevisor.sdk.MessagevisorOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class IcuSemanticsTest {
  @Test
  void executesEveryCanonicalIcuSemanticCase() throws Exception {
    com.fasterxml.jackson.databind.JsonNode fixture;
    try (var input = IcuSemanticsTest.class.getResourceAsStream("/conformance/sdk-v1.json")) {
      fixture = com.messagevisor.sdk.JsonSupport.MAPPER.readTree(input);
    }
    assertThat(fixture.path("fixtureVersion").asInt()).isGreaterThanOrEqualTo(5);
    assertThat(fixture.path("icuSemantics").size()).isPositive();
    for (var test : fixture.path("icuSemantics")) {
      List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
      try (Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale(test.path("locale").asText())
          .timeZone("UTC").addModule(IcuModule.create()).logLevel(LogLevel.ERROR).onDiagnostic(diagnostics::add).build())) {
        Map<String, Object> values = com.messagevisor.sdk.JsonSupport.MAPPER.convertValue(test.path("values"),
            new com.fasterxml.jackson.core.type.TypeReference<>() {});
        if (test.has("error")) {
          assertThatThrownBy(() -> m.formatMessage(test.path("message").asText(), values))
              .as(test.path("id").asText()).isInstanceOf(RuntimeException.class);
          assertThat(diagnostics).extracting(MessagevisorDiagnostic::code)
              .as(test.path("id").asText()).containsExactly(test.path("error").asText());
        } else {
          assertThat(m.formatMessage(test.path("message").asText(), values))
              .as(test.path("id").asText()).isEqualTo(test.path("expected").asText());
          assertThat(diagnostics).as(test.path("id").asText()).isEmpty();
        }
      }
    }
  }

  private Messagevisor sdk(List<MessagevisorDiagnostic> diagnostics) {
    return Messagevisor.create(MessagevisorOptions.builder().locale("en-US").timeZone("UTC")
        .addModule(IcuModule.create()).logLevel(LogLevel.ERROR).onDiagnostic(diagnostics::add).build());
  }

  @Test
  void neverEvaluatesFormattersOrMissingValuesInUnusedBranches() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m = sdk(diagnostics);
    FormatPresets formats = new FormatPresets();
    formats.setNumber(Map.of("money", Map.of("style", "currency", "currency", "USD")));
    formats.setTime(Map.of("clock", Map.of("hour", "numeric")));
    EvaluationOptions options = EvaluationOptions.builder().formats(formats).build();
    for (String argument : List.of("{absent}", "{bad, date}", "{bad, time, short}",
        "{bad, time, clock}", "{bad, number, money}", "{bad, number, ::currency/USD}",
        "{absent, select, yes {Yes} other {Other}}", "{absent, plural, one {One} other {Other}}")) {
      assertThat(m.formatMessage("{kind, select, yes {YES} other {" + argument + "}}",
          Map.of("kind", "yes", "bad", "invalid"), options)).as(argument).isEqualTo("YES");
      assertThat(m.formatMessage("{n, plural, one {ONE} other {" + argument + "}}",
          Map.of("n", 1, "bad", "invalid"), options)).as(argument).isEqualTo("ONE");
      assertThat(m.formatMessage("{n, selectordinal, one {FIRST} other {" + argument + "}}",
          Map.of("n", 1, "bad", "invalid"), options)).as(argument).isEqualTo("FIRST");
    }
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void quotingIsParsedByIcuAndInsertedValuesAreNeverReparsed() {
    Messagevisor m = sdk(new ArrayList<>());
    assertThat(m.formatMessage("'{quoted {d, date}}'", Map.of("d", "invalid")))
        .isEqualTo("{quoted {d, date}}");
    assertThat(m.formatMessage("'{missing, select, yes {{d, date}} other {no}}'", Map.of()))
        .isEqualTo("{missing, select, yes {{d, date}} other {no}}");
    assertThat(m.formatMessage("You don't have access; you don''t have access", Map.of()))
        .isEqualTo("You don't have access; you don't have access");
    assertThat(m.formatMessage("'{name}' {name}", Map.of("name", "{d, date}")))
        .isEqualTo("{name} {d, date}");
    assertThat(m.formatMessage("{n, plural, one {'#' #} other {'#' #}}", Map.of("n", 2)))
        .isEqualTo("# 2");
    assertThat(m.formatMessage("''{name}''", Map.of("name", "Ada"))).isEqualTo("'Ada'");
  }

  @Test
  void missingArgumentsInTheSelectedPathThrowAndReportInvalidMessage() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m = sdk(diagnostics);
    for (String argument : List.of("{missing}", "{missing, number}", "{missing, date}",
        "{missing, time}", "{missing, select, yes {Yes} other {Other}}",
        "{missing, plural, one {One} other {Other}}",
        "{missing, selectordinal, one {First} other {Other}}")) {
      for (String pattern : List.of(argument, "{kind, select, yes {" + argument + "} other {Safe}}")) {
        diagnostics.clear();
        assertThatThrownBy(() -> m.formatMessage(pattern, Map.of("kind", "yes")))
            .as(pattern).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing");
        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
          assertThat(diagnostic.code()).isEqualTo("invalid_message");
          assertThat(diagnostic.details()).containsEntry("source", "formatMessage").containsEntry("locale", "en-US");
        });
      }
    }
    var child = m.spawn(Map.of());
    assertThatThrownBy(() -> child.formatMessage("{missing}", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    child.close();
  }

  @Test
  void presentEmptyNullAndFalseValuesAreNotMissingArguments() {
    Messagevisor m = sdk(new ArrayList<>());
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("value", null);
    assertThat(m.formatMessage("A{value}B", values)).isEqualTo("AB");
    values.put("value", "");
    assertThat(m.formatMessage("A{value}B", values)).isEqualTo("AB");
    values.put("value", false);
    assertThat(m.formatMessage("A{value}B", values)).isEqualTo("AB");
    values.put("value", 0);
    assertThat(m.formatMessage("A{value}B", values)).isEqualTo("A0B");
  }

  @Test
  void pluralOffsetsExactFractionsAndEmptyBranchesPreserveMeaning() {
    Messagevisor m = sdk(new ArrayList<>());
    String pattern = "{n, plural, offset:1 =1 {exact} one {one:#} other {other:#}}";
    assertThat(m.formatMessage(pattern, Map.of("n", 1))).isEqualTo("exact");
    assertThat(m.formatMessage(pattern, Map.of("n", 2))).isEqualTo("one:1");
    assertThat(m.formatMessage(pattern, Map.of("n", 2.5))).isEqualTo("other:1.5");
    assertThat(m.formatMessage("{n, plural, =1 {integer} other {fraction}}", Map.of("n", 1.5)))
        .isEqualTo("fraction");
    assertThat(m.formatMessage("{n, plural, one {} other {fallback}}", Map.of("n", 1))).isEmpty();
    assertThat(m.formatMessage("{kind, select, yes {} other {fallback}}", Map.of("kind", "yes"))).isEmpty();
    assertThat(m.formatMessage("{n, plural, one {one} other {other}}", Map.of("n", 1.5),
        EvaluationOptions.builder().locale("ru").build())).isEqualTo("other");
    assertThat(m.formatMessage("{n, plural, one {one} other {other}}", Map.of("n", 1.5),
        EvaluationOptions.builder().locale("fr").build())).isEqualTo("one");
    assertThat(m.formatMessage("{n, plural, two {two} other {other}}", Map.of("n", 2),
        EvaluationOptions.builder().locale("cy").build())).isEqualTo("two");
    assertThat(m.formatMessage("{n, plural, one {one} other {other}}", Map.of("n", Double.NaN)))
        .isEqualTo("other");
  }

  @Test
  void builtInStylesSkeletonsAndSelectedDateArgumentsStillUseNativeFormatters() {
    Messagevisor m = sdk(new ArrayList<>());
    assertThat(m.formatMessage("{n, number, percent}", Map.of("n", .5))).isEqualTo("50%");
    assertThat(m.formatMessage("{n, number, integer}", Map.of("n", 1.6))).isEqualTo("2");
    assertThat(m.formatMessage("{n, number, ::currency/USD}", Map.of("n", .5))).isEqualTo("$0.50");
    assertThat(m.formatMessage("{kind, select, yes {{d, time, ::HHmm}} other {Safe}}",
        Map.of("kind", "yes", "d", "1970-01-01T00:00:00Z"))).isEqualTo("00:00");
    assertThatThrownBy(() -> m.formatMessage("{kind, select, yes {{d, date}} other {Safe}}",
        Map.of("kind", "yes", "d", "invalid"))).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> m.formatMessage("{kind, select, yes {Safe} other {{broken}}", Map.of("kind", "yes")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
