package com.messagevisor.modules.icu;

import static org.assertj.core.api.Assertions.assertThat;

import com.messagevisor.sdk.DatafileContent;
import com.messagevisor.sdk.DatafileMessage;
import com.messagevisor.sdk.FormatPresets;
import com.messagevisor.sdk.LogLevel;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class IcuModuleTest {
  @Test
  void formatsVariablesNumbersPluralsSelectAndOrdinals() {
    DatafileContent datafile = new DatafileContent();
    datafile.setSchemaVersion("1");
    datafile.setMessagevisorVersion("0.0.1");
    datafile.setRevision("1");
    datafile.setTarget("web");
    datafile.setLocale("en-US");
    FormatPresets formats = new FormatPresets();
    formats.setNumber(Map.of("money", map("style", "currency", "currency", "USD")));
    datafile.setFormats(formats);
    DatafileMessage message = new DatafileMessage();
    datafile.setMessages(Map.of("summary", message, "ordinal", new DatafileMessage()));
    datafile.setTranslations(
        Map.of(
            "summary",
            "{name}: {amount, number, money}; {count, plural, =0 {none} one {# item} other {# items}}; {kind, select, vip {VIP} other {Regular}}",
            "ordinal",
            "{place, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}"));

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile)
                .addModule(IcuModule.create())
                .logLevel(LogLevel.FATAL)
                .build());

    String formatted =
        m.translate("summary", Map.of("name", "Ada", "amount", 12, "count", 2, "kind", "vip"));

    assertThat(formatted).contains("Ada").contains("2 items").contains("VIP");
    assertThat(m.translate("ordinal", Map.of("place", 1))).isEqualTo("1st");
  }

  @Test
  void delegatesNestedIcuQuotingOffsetsAndExactMatchesToIcu4j() {
    DatafileContent datafile = new DatafileContent();
    datafile.setSchemaVersion("1");
    datafile.setMessagevisorVersion("0.0.1");
    datafile.setRevision("1");
    datafile.setTarget("web");
    datafile.setLocale("en-US");
    FormatPresets formats = new FormatPresets();
    formats.setNumber(
        Map.of(
            "money",
            map("style", "currency", "currency", "USD"),
            "compact",
            map("notation", "compact", "compactDisplay", "short")));
    formats.setDate(Map.of("long", map("year", "numeric", "month", "long", "day", "numeric", "timeZone", "UTC")));
    datafile.setFormats(formats);
    datafile.setMessages(
        Map.of(
            "quoted", new DatafileMessage(),
            "offset", new DatafileMessage(),
            "nested", new DatafileMessage(),
            "date", new DatafileMessage()));
    datafile.setTranslations(
        Map.of(
            "quoted",
            "You have '{' {count, plural, one {# item} other {# items}} '}'",
            "offset",
            "{guests, plural, offset:1 =0 {Nobody came} one {You came alone} other {You and # guests came}}",
            "nested",
            "{kind, select, sale {{amount, number, money}} growth {{amount, number, compact}} other {n/a}}",
            "date",
            "Ships {when, date, long}"));

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile)
                .addModule(IcuModule.create())
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.translate("quoted", Map.of("count", 2))).contains("{").contains("2 items").contains("}");
    assertThat(m.translate("offset", Map.of("guests", 3))).isEqualTo("You and 2 guests came");
    assertThat(m.translate("nested", Map.of("kind", "sale", "amount", 12))).contains("$");
    assertThat(m.translate("nested", Map.of("kind", "growth", "amount", 1200))).containsIgnoringCase("K");
    assertThat(m.translate("date", Map.of("when", "2026-05-12T00:00:00Z"))).contains("May");
  }

  @Test
  void reportsInvalidMessagesThroughSdkDiagnosticPath() {
    DatafileContent datafile = new DatafileContent();
    datafile.setSchemaVersion("1");
    datafile.setMessagevisorVersion("0.0.1");
    datafile.setRevision("1");
    datafile.setTarget("web");
    datafile.setLocale("en-US");
    datafile.setMessages(Map.of("broken", new DatafileMessage()));
    datafile.setTranslations(Map.of("broken", "{count, plural, one {One}"));
    java.util.List<com.messagevisor.sdk.MessagevisorDiagnostic> diagnostics = new java.util.ArrayList<>();

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile)
                .addModule(IcuModule.create())
                .logLevel(LogLevel.ERROR)
                .onDiagnostic(diagnostics::add)
                .build());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> m.translate("broken", Map.of("count", 1)))
        .isInstanceOf(RuntimeException.class);
    assertThat(diagnostics).anyMatch(diagnostic -> diagnostic.code().equals("invalid_message"));
  }

  @Test
  void supportsRoundingPriorityWithoutFormatterDiagnostics() {
    DatafileContent datafile = new DatafileContent();
    datafile.setSchemaVersion("1");
    datafile.setMessagevisorVersion("0.0.1");
    datafile.setRevision("1");
    datafile.setTarget("web");
    datafile.setLocale("en-US");
    FormatPresets formats = new FormatPresets();
    formats.setNumber(
        Map.of("rounded", map("roundingPriority", "morePrecision", "maximumFractionDigits", 2)));
    datafile.setFormats(formats);
    datafile.setMessages(Map.of("amount", new DatafileMessage()));
    datafile.setTranslations(Map.of("amount", "{value, number, rounded}"));
    java.util.List<com.messagevisor.sdk.MessagevisorDiagnostic> diagnostics = new java.util.ArrayList<>();

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile)
                .addModule(IcuModule.create())
                .logLevel(LogLevel.WARN)
                .onDiagnostic(diagnostics::add)
                .build());

    assertThat(m.translate("amount", Map.of("value", 1.234))).isEqualTo("1.23");

    assertThat(diagnostics)
        .noneMatch(
            diagnostic ->
                diagnostic.code().equals("unsupported_formatter")
                    && diagnostic.message().contains("roundingPriority"));
  }

  private static Map<String, Object> map(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put(String.valueOf(entries[i]), entries[i + 1]);
    }
    return result;
  }
}
