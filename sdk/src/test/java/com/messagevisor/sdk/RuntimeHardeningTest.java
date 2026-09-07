package com.messagevisor.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RuntimeHardeningTest {
  @Test
  void mergesStoredFormatFamiliesAndReplacesWholePresetsWithoutChangingEvaluationLayers() {
    DatafileContent first = TestDatafiles.enUs();
    FormatPresets defaults = new FormatPresets();
    defaults.setNumber(Map.of("fixed", Map.of("minimumFractionDigits", 1)));
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(first)
        .defaultFormats(Map.of("en-US", defaults)).logLevel(LogLevel.FATAL).build());
    DatafileContent update = TestDatafiles.enUs();
    FormatPresets formats = new FormatPresets();
    formats.setNumber(Map.of("fixed", Map.of("maximumFractionDigits", 3), "new", Map.of("style", "percent")));
    update.setFormats(formats);
    update.setRevision("updated");
    m.setDatafile(update);
    assertThat(m.getDatafile().getFormats().getDate()).isEqualTo(first.getFormats().getDate());
    assertThat(m.getDatafile().getFormats().getNumber()).containsKeys("money", "new");
    assertThat(m.getDatafile().getFormats().getNumber().get("fixed"))
        .containsExactlyEntriesOf(Map.of("maximumFractionDigits", 3));
    assertThat(m.formatNumber(1, "fixed")).isEqualTo("1.0");
    FormatPresets call = new FormatPresets();
    call.setNumber(Map.of("fixed", Map.of("minimumFractionDigits", 2)));
    assertThat(m.formatNumber(1, "fixed", EvaluationOptions.builder().formats(call).build())).isEqualTo("1.00");
    DatafileContent noFormats = TestDatafiles.enUs();
    noFormats.setFormats(null);
    m.setDatafile(noFormats);
    assertThat(m.getDatafile().getFormats().getNumber()).containsKeys("money", "new");
    DatafileContent replacement = TestDatafiles.enUs();
    replacement.setFormats(null);
    m.setDatafile(replacement, true);
    assertThat(m.getDatafile().getFormats().getNumber()).isEmpty();
  }

  @Test
  void constructorModulesSeeInitialRevisionAndInitializationAfterSetup() {
    List<String> trace = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).locale("nl-NL")
        .onDiagnostic(diagnostic -> trace.add(diagnostic.code()))
        .addModule(new MessagevisorModule() {
          @Override public void setup(MessagevisorModuleApi api) {
            trace.add("setup:" + api.getRevision(null));
            api.onDiagnostic(diagnostic -> trace.add("module:" + diagnostic.code()), null);
          }
        }).build());
    assertThat(trace).containsExactly("setup:en-1", "module:sdk_initialized", "sdk_initialized");
    assertThat(m.getLocale()).isEqualTo("en-US");
  }

  @Test
  void rejectsMalformedShapesWithoutChangingStateOrEmittingChange() throws Exception {
    Map<String, Object> valid = JsonSupport.MAPPER.convertValue(TestDatafiles.enUs(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
    List<Object> invalid = new ArrayList<>(List.of("null", "[]", "{}", "{invalid", 42));
    for (String field : List.of("schemaVersion", "messagevisorVersion", "revision", "target", "locale", "segments", "messages", "translations")) {
      Map<String, Object> missing = new LinkedHashMap<>(valid);
      missing.remove(field);
      invalid.add(missing);
      Map<String, Object> wrong = new LinkedHashMap<>(valid);
      wrong.put(field, 1);
      invalid.add(wrong);
      Map<String, Object> nullValue = new LinkedHashMap<>(valid);
      nullValue.put(field, null);
      invalid.add(nullValue);
    }
    for (String field : List.of("formats", "segments", "messages", "translations")) {
      Map<String, Object> wrong = new LinkedHashMap<>(valid);
      wrong.put(field, List.of());
      invalid.add(wrong);
    }
    for (Map<String, Object> change : List.of(Map.<String, Object>of("schemaVersion", "2"),
        Map.<String, Object>of("locale", ""), Map.<String, Object>of("direction", "sideways"))) {
      Map<String, Object> wrong = new LinkedHashMap<>(valid);
      wrong.putAll(change);
      invalid.add(wrong);
    }
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    List<MessagevisorEvent> changes = new ArrayList<>();
    List<MessagevisorEvent> errors = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(TestDatafiles.enUs())
        .onDiagnostic(diagnostics::add).logLevel(LogLevel.ERROR).build());
    MessagevisorSnapshot snapshot = m.getSnapshot();
    m.on(EventName.CHANGE, changes::add);
    m.on(EventName.ERROR, errors::add);
    for (Object value : invalid) {
      m.setDatafile(value);
      m.setDatafile(JsonSupport.MAPPER.writeValueAsString(value));
    }
    m.setDatafile(null);
    assertThat(diagnostics).hasSize(invalid.size() * 2 + 1).allSatisfy(diagnostic -> {
      assertThat(diagnostic.code()).isEqualTo("invalid_datafile");
      assertThat(diagnostic.message()).isEqualTo("could not parse datafile");
    });
    assertThat(errors).hasSameSizeAs(diagnostics);
    assertThat(changes).isEmpty();
    assertThat(m.getSnapshot()).isEqualTo(snapshot);
    Map<String, Object> emptyIdentities = new LinkedHashMap<>(valid);
    emptyIdentities.put("revision", "");
    emptyIdentities.put("target", "");
    emptyIdentities.put("messagevisorVersion", "");
    m.setDatafile(emptyIdentities);
    assertThat(m.getRevision()).isEmpty();
  }

  @Test
  void dictionaryNamesWorkOrMissNormallyAcrossRuntimeLookups() {
    for (String key : List.of("__proto__", "constructor", "toString", "hasOwnProperty")) {
      DatafileContent datafile = TestDatafiles.enUs();
      datafile.setLocale(key);
      Segment segment = new Segment();
      segment.setConditions(Map.of("attribute", key + ".value", "operator", "equals", "value", "yes"));
      datafile.setSegments(Map.of(key, segment));
      MessageOverride override = new MessageOverride();
      override.setKey(key);
      override.setSegments(key);
      override.setTranslation("matched");
      DatafileMessage message = new DatafileMessage();
      message.setOverrides(List.of(override));
      datafile.setMessages(Map.of(key, message));
      datafile.setTranslations(Map.of(key, "base"));
      Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(datafile)
          .context(Map.of(key, Map.of("value", "yes"))).logLevel(LogLevel.FATAL).build());
      assertThat(m.translate(key)).isEqualTo("matched");
      m.setContext(Map.of(), true);
      assertThat(m.translate(key)).isEqualTo("base");
      Messagevisor missing = Messagevisor.create(MessagevisorOptions.builder().locale("en-US")
          .logLevel(LogLevel.FATAL).build());
      assertThat(missing.translate(key)).isEqualTo(key);
      assertThat(missing.formatNumber(12, key)).isEqualTo("12");
      Messagevisor defaults = Messagevisor.create(MessagevisorOptions.builder().locale(key)
          .defaultTranslations(Map.of(key, Map.of(key, "default"))).logLevel(LogLevel.FATAL).build());
      assertThat(defaults.translate(key)).isEqualTo("default");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void moduleHookFailuresRetainOriginalExceptionAndEmitOneError() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) PortableConformanceTest.fixture().get("hardening");
    Map<String, Object> setup = (Map<String, Object>) contract.get("moduleSetup");
    Map<String, Object> errorsContract = (Map<String, Object>) contract.get("errors");
    for (String hook : List.of("format", "transform")) {
      IllegalStateException failure = new IllegalStateException(hook);
      List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
      List<MessagevisorEvent> errors = new ArrayList<>();
      Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en-US")
          .onDiagnostic(diagnostics::add).logLevel(LogLevel.ERROR)
          .addModule(new MessagevisorModule() {
            @Override public String name() { return "broken"; }
            @Override public Object format(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
              if (hook.equals("format")) throw failure;
              return null;
            }
            @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
              throw failure;
            }
          }).build());
      m.on(EventName.ERROR, errors::add);
      assertThatThrownBy(() -> m.formatMessage("message", Map.of(), EvaluationOptions.builder().locale("nl-NL").build()))
          .isSameAs(failure);
      assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
        assertThat(diagnostic.code()).isEqualTo(setup.get(hook + "FailureCode"));
        assertThat(setup.get("preservesOriginalError")).isEqualTo(true);
        assertThat(diagnostic.originalError()).isSameAs(failure);
        assertThat(diagnostic.moduleName()).isEqualTo("broken");
        assertThat(diagnostic.details()).containsEntry("hook", hook).containsEntry("locale", "nl-NL")
            .containsEntry("source", "formatMessage");
      });
      assertThat(errors).hasSize(((Number) errorsContract.get("errorEventsPerDiagnostic")).intValue());
      assertThat(errorsContract.get("throwsOriginalError")).isEqualTo(true);
    }
  }

  @Test
  void directTimesHonorPrecedenceAndRejectUnknownZonesWithoutChangingState() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(TestDatafiles.enUs())
        .timeZone("America/Los_Angeles").onDiagnostic(diagnostics::add).logLevel(LogLevel.ERROR).build());
    String instant = "2026-05-12T08:30:45Z";
    assertThat(m.formatTime(instant, Map.of())).contains("1:30:45");
    assertThat(m.formatTime(instant, "short")).contains("8:30");
    assertThat(m.formatTime(instant, "short", EvaluationOptions.builder().timeZone("Asia/Tokyo").build())).contains("5:30");
    MessagevisorChild child = m.spawn(Map.of(), new MessagevisorSpawnOptions(null, null, "Asia/Tokyo"));
    assertThat(child.formatTime(instant, Map.of())).contains("5:30:45");
    assertThat(child.formatTime(instant, "short")).contains("8:30");
    assertThatThrownBy(() -> m.formatTime(instant, Map.of(), EvaluationOptions.builder().timeZone("Not/A_Zone").build()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(diagnostics).extracting(MessagevisorDiagnostic::code).containsExactly("invalid_format");
    assertThat(m.getTimeZone()).isEqualTo("America/Los_Angeles");
    diagnostics.clear();
    MessagevisorSnapshot snapshot = m.getSnapshot();
    assertThatThrownBy(() -> m.setLocale("absent")).isInstanceOf(MessagevisorException.class);
    assertThat(diagnostics).extracting(MessagevisorDiagnostic::code).containsExactly("missing_datafile");
    assertThat(m.getSnapshot()).isEqualTo(snapshot);
  }

  @Test
  void explicitHourCycleKeepsNativePatternWidth() {
    for (String locale : List.of("en-US", "ar-AE", "ar-SA", "bn")) {
      var nativeLocale = com.ibm.icu.util.ULocale.forLanguageTag(locale);
      String pattern = com.ibm.icu.text.DateTimePatternGenerator.getInstance(nativeLocale)
          .getBestPattern("Hmm", com.ibm.icu.text.DateTimePatternGenerator.MATCH_HOUR_FIELD_LENGTH);
      var formatter = new com.ibm.icu.text.SimpleDateFormat(pattern, nativeLocale);
      formatter.setTimeZone(com.ibm.icu.util.TimeZone.getTimeZone("UTC"));
      var date = java.util.Date.from(java.time.Instant.parse("2026-05-12T08:30:00Z"));
      Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale(locale).timeZone("UTC").logLevel(LogLevel.FATAL).build());
      assertThat(m.formatTime(date, Map.of("hour", "numeric", "minute", "2-digit", "hour12", false)))
          .isEqualTo(formatter.format(date));
    }
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void nativeListPartsPreserveLiteralSeparatorsAndInvalidListsThrow() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) PortableConformanceTest.fixture().get("hardening");
    Map<String, Object> fallbacks = (Map<String, Object>) contract.get("fallbacks");
    List<String> input = (List<String>) fallbacks.get("listInput");
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en-US")
        .onDiagnostic(diagnostics::add).logLevel(LogLevel.WARN).build());
    for (String locale : List.of("en-US", "nl-NL", "ja-JP", "ar")) {
      for (List<String> values : List.of(List.<String>of(), List.of("A"), List.of("A", "B"), List.of("A", "A", "C"))) {
        Map<String, Object> options = Map.of("locale", locale);
        assertThat(m.formatListToParts(values, options).stream().map(FormatPart::value).collect(java.util.stream.Collectors.joining()))
            .isEqualTo(m.formatList(values, options));
      }
    }
    assertThat(m.formatListToParts(List.of())).isEmpty();
    assertThat(m.formatListToParts(input)).containsExactly(
        new FormatPart("element", "A"), new FormatPart("literal", " and "), new FormatPart("element", "B"));
    assertThat(diagnostics).isEmpty();
    assertThatThrownBy(() -> m.formatList((List) List.of(1))).isInstanceOf(IllegalArgumentException.class);
    assertThat(diagnostics).extracting(MessagevisorDiagnostic::code).containsExactly("invalid_format");
  }

  @Test
  void hostTimeZoneIsCapturedOnceAndSharedWithChildren() {
    com.ibm.icu.util.TimeZone original = com.ibm.icu.util.TimeZone.getDefault();
    try {
      com.ibm.icu.util.TimeZone.setDefault(com.ibm.icu.util.TimeZone.getTimeZone("UTC"));
      Messagevisor root = Messagevisor.create(MessagevisorOptions.builder().locale("en-US").logLevel(LogLevel.FATAL).build());
      com.ibm.icu.util.TimeZone.setDefault(com.ibm.icu.util.TimeZone.getTimeZone("Asia/Tokyo"));
      MessagevisorChild child = root.spawn(Map.of());
      assertThat(root.formatTime("2026-05-12T08:30:45Z", Map.of())).contains("8:30:45");
      assertThat(child.formatTime("2026-05-12T08:30:45Z", Map.of())).contains("8:30:45");
      assertThat(root.getTimeZone()).isNull();
    } finally {
      com.ibm.icu.util.TimeZone.setDefault(original);
    }
  }
}
