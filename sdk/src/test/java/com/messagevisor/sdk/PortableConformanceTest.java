package com.messagevisor.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
final class PortableConformanceTest {
  @SuppressWarnings("unchecked")
  static Map<String, Object> fixture() throws Exception {
    try (InputStream input = PortableConformanceTest.class.getResourceAsStream("/conformance/sdk-v1.json")) {
      return JsonSupport.MAPPER.readValue(input, new TypeReference<>() {});
    }
  }

  @Test
  void declaresEveryCanonicalSectionThatHasExecutableConsumers() throws Exception {
    // ICU cases execute in module-icu's IcuSemanticsTest; all other behavioural
    // sections execute here, RuntimeHardeningTest, or IcuModuleTest's zone tests.
    // The variability section describes intentional native presentation differences.
    assertThat(fixture()).containsOnlyKeys("fixtureVersion", "description", "runtimeVariability",
        "icuSemantics", "pluralSemantics", "hardening", "portableRegex", "conditions", "segments",
        "translations", "datafiles", "modules", "diagnostics", "events");
  }

  @Test
  void executesEveryCanonicalPluralSemanticCase() throws Exception {
    List<Map<String, Object>> cases = (List<Map<String, Object>>) fixture().get("pluralSemantics");
    assertThat(cases).isNotEmpty();
    for (Map<String, Object> test : cases) {
      try (Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale((String) test.get("locale"))
          .logLevel(LogLevel.FATAL).build())) {
        Map<String, Object> options = (Map<String, Object>) test.getOrDefault("options", Map.of());
        double value = ((Number) test.get("value")).doubleValue();
        assertThat(m.formatPlural(value, options)).as(test.toString()).isEqualTo(test.get("expected"));
        try (MessagevisorChild child = m.spawn(Map.of())) {
          assertThat(child.formatPlural(value, options)).as(test.toString()).isEqualTo(test.get("expected"));
        }
      }
    }
  }

  @Test
  void executesHardeningFormatMergeAndValidationFixtures() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("hardening");
    Map<String, Object> merge = (Map<String, Object>) contract.get("formatMerge");
    DatafileContent first = fixtureDatafile();
    first.setFormats(JsonSupport.MAPPER.convertValue(merge.get("initial"), FormatPresets.class));
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(first)
        .logLevel(LogLevel.ERROR).onDiagnostic(diagnostics::add).build());
    MessagevisorChild child = m.spawn(Map.of());
    DatafileContent incoming = fixtureDatafile();
    incoming.setFormats(JsonSupport.MAPPER.convertValue(merge.get("incoming"), FormatPresets.class));
    m.setDatafile(incoming);
    FormatPresets expected = JsonSupport.MAPPER.convertValue(merge.get("expected"), FormatPresets.class);
    assertThat(JsonSupport.MAPPER.<com.fasterxml.jackson.databind.JsonNode>valueToTree(m.getDatafile().getFormats()))
        .isEqualTo(JsonSupport.MAPPER.valueToTree(expected));
    assertThat(child.formatNumber(0.5, "money")).isEqualTo("50%");
    if (Boolean.TRUE.equals(merge.get("omittedFormatsPreserveStored"))) {
      m.setDatafile(fixtureDatafile());
      assertThat(JsonSupport.MAPPER.<com.fasterxml.jackson.databind.JsonNode>valueToTree(m.getDatafile().getFormats()))
          .isEqualTo(JsonSupport.MAPPER.valueToTree(expected));
    }
    if (Boolean.TRUE.equals(merge.get("replaceRemovesStoredFormats"))) {
      m.setDatafile(fixtureDatafile(), true);
      assertThat(m.getDatafile().getFormats().getNumber()).isEmpty();
    }
    Map<String, Object> validation = (Map<String, Object>) contract.get("datafileValidation");
    Map<String, Object> valid = JsonSupport.MAPPER.convertValue(fixtureDatafile(), new TypeReference<>() {});
    valid.remove("direction");
    List<Object> invalid = new ArrayList<>((List<Object>) validation.get("invalidInputs"));
    for (Map<String, Object> field : (List<Map<String, Object>>) validation.get("invalidFields")) {
      Map<String, Object> value = new java.util.LinkedHashMap<>(valid);
      value.put((String) field.get("field"), field.get("value"));
      invalid.add(value);
    }
    for (String category : List.of("requiredStrings", "requiredMaps")) {
      for (String field : (List<String>) validation.get(category)) {
        Map<String, Object> value = new java.util.LinkedHashMap<>(valid);
        value.remove(field);
        invalid.add(value);
        for (Object wrong : new Object[] {null, category.equals("requiredStrings") ? 1 : "wrong", List.of()}) {
          Map<String, Object> invalidValue = new java.util.LinkedHashMap<>(valid);
          invalidValue.put(field, wrong);
          invalid.add(invalidValue);
        }
      }
    }
    for (String field : (List<String>) validation.get("nonemptyStrings")) {
      Map<String, Object> value = new java.util.LinkedHashMap<>(valid);
      value.put(field, "");
      invalid.add(value);
    }
    assertThat(valid.get("schemaVersion")).isEqualTo(validation.get("schemaVersion"));
    assertThat(validation.get("throws")).isEqualTo(false);
    assertThat(validation.get("changesState")).isEqualTo(false);
    MessagevisorSnapshot snapshot = m.getSnapshot();
    List<MessagevisorEvent> errors = new ArrayList<>();
    List<MessagevisorEvent> changes = new ArrayList<>();
    m.on(EventName.ERROR, errors::add);
    m.on(EventName.CHANGE, changes::add);
    for (Object value : invalid) m.setDatafile(value);
    assertThat(diagnostics).hasSameSizeAs(invalid).allSatisfy(diagnostic -> {
      assertThat(diagnostic.code()).isEqualTo(validation.get("code"));
      assertThat(diagnostic.message()).isEqualTo(validation.get("message"));
    });
    assertThat(errors).hasSameSizeAs(invalid);
    assertThat(changes).isEmpty();
    assertThat(m.getSnapshot()).isEqualTo(snapshot);
  }

  @Test
  void executesHardeningDictionaryAndSetupFixtures() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("hardening");
    for (String key : (List<String>) contract.get("dictionaryKeys")) {
      DatafileContent datafile = fixtureDatafile();
      Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(datafile).logLevel(LogLevel.FATAL).build());
      assertThat(m.translate(key)).isEqualTo(key);
      assertThatThrownBy(() -> m.getDatafile(key)).isInstanceOf(MessagevisorException.class);
      assertThat(ConditionEvaluator.evaluateSegment(key, new ConditionEvaluator.EvaluateOptions(Map.of(), Map.of(), null, null))).isFalse();
      datafile = fixtureDatafile();
      datafile.setMessages(Map.of(key, new DatafileMessage()));
      datafile.setTranslations(Map.of(key, "own"));
      datafile.getFormats().setNumber(Map.of(key, Map.of("style", "percent")));
      m.setDatafile(datafile);
      assertThat(m.translate(key)).isEqualTo("own");
      assertThat(m.formatNumber(0.5, key)).isEqualTo("50%");
      datafile = fixtureDatafile();
      datafile.setLocale(key);
      m.setDatafile(datafile);
      assertThat(m.getSnapshot().datafileRevisionsByLocale()).containsKey(key);
    }
    Map<String, Object> setup = (Map<String, Object>) contract.get("moduleSetup");
    assertThat(setup.get("setupBeforeInitialized")).isEqualTo(true);
    DatafileContent datafile = fixtureDatafile();
    datafile.setRevision((String) setup.get("initialRevision"));
    List<String> trace = new ArrayList<>();
    Messagevisor.create(MessagevisorOptions.builder().datafile(datafile).logLevel(LogLevel.FATAL)
        .addModule(new MessagevisorModule() {
          @Override public void setup(MessagevisorModuleApi api) {
            trace.add(api.getRevision());
            api.onDiagnostic(diagnostic -> trace.add(diagnostic.code()), null);
          }
        }).build());
    assertThat(trace).containsExactly((String) setup.get("initialRevision"), "sdk_initialized");
  }

  @Test
  void executesHardeningNativeFailureAndTimeZoneContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("hardening");
    Map<String, Object> errorsContract = (Map<String, Object>) contract.get("errors");
    Map<String, Object> zones = (Map<String, Object>) contract.get("timeZones");
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    List<MessagevisorEvent> errors = new ArrayList<>();
    DatafileContent datafile = fixtureDatafile();
    datafile.getFormats().setRelative(Map.of("test", Map.of()));
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(datafile)
        .timeZone((String) zones.get("instance")).logLevel(LogLevel.ERROR).onDiagnostic(diagnostics::add).build());
    m.on(EventName.ERROR, errors::add);
    List<Runnable> calls = List.of(() -> m.setLocale("missing"),
        () -> m.formatDate("invalid", Map.of()),
        () -> m.formatDate(Double.NaN, Map.of()),
        () -> m.formatTime("invalid", Map.of()),
        () -> m.formatTime(Double.POSITIVE_INFINITY, Map.of()),
        () -> m.formatDateTimeRange("invalid", 0, Map.of(), EvaluationOptions.builder().build()),
        () -> m.formatRelativeTime(Double.POSITIVE_INFINITY, "day", "test"),
        () -> m.formatRelativeTime(1, "wrong", "test"),
        () -> m.formatDisplayName("!", Map.of("type", "region")));
    for (int index = 0; index < calls.size(); index++) {
      assertThatThrownBy(calls.get(index)::run).isInstanceOf(RuntimeException.class);
      assertThat(diagnostics).hasSize(index + 1);
      assertThat(diagnostics.get(index).code()).isEqualTo(errorsContract.get(index == 0 ? "missingSetLocaleCode" : "invalidFormatterValueCode"));
      assertThat(errors).hasSize((index + 1) * ((Number) errorsContract.get("errorEventsPerDiagnostic")).intValue());
    }
    Map<String, Object> format = Map.of("hour", "2-digit", "minute", "2-digit", "second", "2-digit", "hour12", false);
    String instant = (String) zones.get("instant");
    String locale = m.getLocale();
    assertThat(m.formatTime(instant, format)).isEqualTo(MessagevisorFormatters.formatTime(instant, locale, format, (String) zones.get("instance")));
    Map<String, Object> preset = new java.util.LinkedHashMap<>(format);
    preset.put("timeZone", zones.get("preset"));
    assertThat(m.formatTime(instant, preset)).isEqualTo(MessagevisorFormatters.formatTime(instant, locale, format, (String) zones.get("preset")));
    assertThat(m.formatTime(instant, preset, EvaluationOptions.builder().timeZone((String) zones.get("call")).build()))
        .isEqualTo(MessagevisorFormatters.formatTime(instant, locale, format, (String) zones.get("call")));
  }

  @Test
  void executesConditionAndSegmentFixturesDirectly() throws Exception {
    Map<String, Object> fixture = fixture();
    for (Map<String, Object> item : (java.util.List<Map<String, Object>>) fixture.get("conditions")) {
      var options = new ConditionEvaluator.EvaluateOptions(
          (Map<String, Object>) item.get("context"), Map.of(), null, null);
      assertThat(ConditionEvaluator.evaluateCondition(item.get("condition"), options))
          .as(String.valueOf(item.get("name")))
          .isEqualTo(item.get("expected"));
    }
    for (Map<String, Object> item : (java.util.List<Map<String, Object>>) fixture.get("segments")) {
      Map<String, Segment> segments = JsonSupport.MAPPER.convertValue(
          item.get("segments"), new TypeReference<>() {});
      var options = new ConditionEvaluator.EvaluateOptions(
          (Map<String, Object>) item.get("context"), segments, null, null);
      boolean actual = item.containsKey("group")
          ? ConditionEvaluator.evaluateGroupSegment(item.get("group"), options)
          : ConditionEvaluator.evaluateSegment(String.valueOf(item.get("segment")), options);
      assertThat(actual).as(String.valueOf(item.get("name"))).isEqualTo(item.get("expected"));
    }
  }

  @Test
  void executesPortableRegexFixturesDirectly() throws Exception {
    Map<String, Object> regex = (Map<String, Object>) fixture().get("portableRegex");
    for (String flag : (List<String>) regex.get("flags")) {
      var condition = TestDatafiles.map("attribute", "value", "operator", "matches", "value", "x", "regexFlags", flag);
      assertThat(ConditionEvaluator.evaluateCondition(condition,
          new ConditionEvaluator.EvaluateOptions(Map.of("value", "x"), Map.of(), null, null)))
          .as("supported regex flag " + flag).isTrue();
    }
    for (Map<String, Object> item : (List<Map<String, Object>>) regex.get("accepted")) {
      var condition = TestDatafiles.map(
          "attribute", "value", "operator", "matches", "value", item.get("pattern"));
      if (item.get("flags") != null) condition.put("regexFlags", item.get("flags"));
      assertThat(ConditionEvaluator.evaluateCondition(
          condition,
          new ConditionEvaluator.EvaluateOptions(Map.of("value", item.get("value")), Map.of(), null, null)))
          .isTrue();
    }
    for (Map<String, Object> item : (List<Map<String, Object>>) regex.get("rejected")) {
      var condition = TestDatafiles.map(
          "attribute", "value", "operator", "notMatches", "value", item.get("pattern"));
      assertThat(ConditionEvaluator.evaluateCondition(
          condition,
          new ConditionEvaluator.EvaluateOptions(Map.of("value", "other"), Map.of(), null, null)))
          .as(String.valueOf(item.get("name")))
          .isFalse();
    }
  }

  @Test
  void executesTranslationFixturesDirectly() throws Exception {
    for (Map<String, Object> item :
        (java.util.List<Map<String, Object>>) fixture().get("translations")) {
      List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
      MessagevisorOptions.Builder options = MessagevisorOptions.builder()
          .context((Map<String, Object>) item.get("context"))
          .locale((String) item.get("locale"))
          .logLevel(LogLevel.DEBUG)
          .onDiagnostic(diagnostics::add);
      if (item.containsKey("datafile")) options.datafile(item.get("datafile"));
      if (item.containsKey("defaultTranslations")) {
        options.defaultTranslations(JsonSupport.MAPPER.convertValue(
            item.get("defaultTranslations"), new TypeReference<>() {}));
      }
      TranslateOptions translateOptions = TranslateOptions.builder()
          .defaultTranslation((String) item.get("defaultTranslation"))
          .build();
      Messagevisor m = Messagevisor.create(options.build());
      assertThat(m.translate(String.valueOf(item.get("message")), Map.of(), translateOptions))
          .as(String.valueOf(item.get("name")))
          .isEqualTo(item.get("expected"));
      for (String code : (List<String>) item.getOrDefault("expectedDiagnosticCodes", List.of())) {
        assertThat(diagnostics).extracting(MessagevisorDiagnostic::code).contains(code);
      }
    }
  }

  @Test
  void executesDatafileStorageAndInvalidInputContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("datafiles");
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    DatafileContent first = fixtureDatafile();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder()
        .datafile(first)
        .logLevel(LogLevel.ERROR)
        .onDiagnostic(diagnostics::add)
        .build());

    assertThat(contract.get("storageKey")).isEqualTo("locale");
    assertThat(contract.get("firstLoadedLocaleBecomesActive")).isEqualTo(true);
    assertThat(m.getLocale()).isEqualTo(first.getLocale());
    try (Messagevisor initiallyEmpty = Messagevisor.create()) {
      assertThat(initiallyEmpty.getLocale()).isNull();
      initiallyEmpty.setDatafile(first);
      assertThat(initiallyEmpty.getLocale()).isEqualTo(first.getLocale());
    }

    DatafileContent incoming = fixtureDatafile();
    incoming.setRevision("2");
    incoming.setTarget("mobile");
    incoming.setMessages(Map.of("second", new DatafileMessage()));
    incoming.setTranslations(Map.of("second", "Second"));
    m.setDatafile(incoming);
    assertThat(m.getSnapshot().datafileLocales()).containsExactly(first.getLocale());
    assertThat(m.getDatafile().getTarget()).isEqualTo(incoming.getTarget());
    if (Boolean.TRUE.equals(contract.get("mergeByDefault"))) {
      assertThat(m.getDatafile().getTranslations()).containsEntry("welcome", "Base").containsEntry("second", "Second");
    }

    DatafileContent otherLocale = fixtureDatafile();
    otherLocale.setLocale("nl");
    otherLocale.setRevision("nl-1");
    m.setDatafile(otherLocale);
    assertThat(m.getSnapshot().datafileLocales()).containsExactlyInAnyOrder(first.getLocale(), otherLocale.getLocale());
    if (Boolean.TRUE.equals(contract.get("loadingAnotherLocaleDoesNotChangeActiveLocale"))) {
      assertThat(m.getLocale()).isEqualTo("en");
    }

    DatafileContent replacement = fixtureDatafile();
    replacement.setRevision("3");
    m.setDatafile(replacement, true);
    if (Boolean.TRUE.equals(contract.get("replaceWithSecondArgument"))) {
      assertThat(m.getDatafile().getTranslations()).containsOnlyKeys("welcome");
    }

    m.setDatafile("{invalid");
    Map<String, Object> expected = (Map<String, Object>) contract.get("invalidDatafileDiagnostic");
    assertThat(diagnostics.get(diagnostics.size() - 1).code()).isEqualTo(expected.get("code"));
    assertThat(diagnostics.get(diagnostics.size() - 1).message()).isEqualTo(expected.get("message"));
  }

  @Test
  void executesModuleLifecycleContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("modules");
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    List<String> closed = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder()
        .logLevel(LogLevel.ERROR)
        .onDiagnostic(diagnostics::add)
        .build());

    m.addModule(namedModule("duplicate", closed));
    m.addModule(namedModule("duplicate", closed));
    m.addModule(new MessagevisorModule() {
      @Override public String name() { return "broken"; }
      @Override public void setup(MessagevisorModuleApi api) { throw new IllegalStateException("setup"); }
      @Override public void close() { closed.add("broken"); }
    });
    MessagevisorUnsubscribe remove = m.addModule(namedModule("dynamic", closed));
    remove.unsubscribe();
    assertThat(contract.get("removalClosesModule")).isEqualTo(true);
    assertThat(closed).contains("dynamic");
    if (Boolean.TRUE.equals(contract.get("removalIsIdempotent"))) remove.unsubscribe();
    m.addModule(namedModule("first", closed));
    m.addModule(namedModule("last", closed));
    m.close();
    assertThat(contract.get("closeOrder")).isEqualTo("reverse");

    assertThat(diagnostics).extracting(MessagevisorDiagnostic::code)
        .contains(String.valueOf(contract.get("duplicateCode")), String.valueOf(contract.get("setupFailureCode")));
    assertThat(closed).containsExactly("broken", "dynamic", "last", "first", "duplicate");
  }

  @Test
  void executesModuleCloseFailureCodeAndContinuedCleanupContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("modules");
    List<String> closed = new ArrayList<>();
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().logLevel(LogLevel.ERROR)
        .onDiagnostic(diagnostics::add).addModule(namedModule("first", closed)).build());
    IllegalStateException failure = new IllegalStateException("cleanup failed");
    m.addModule(new MessagevisorModule() {
      @Override public String name() { return "last"; }
      @Override public void close() { closed.add("last"); throw failure; }
    });
    assertThatThrownBy(m::close).isInstanceOf(MessagevisorCloseException.class);
    assertThat(closed).containsExactly("last", "first");
    assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
      assertThat(diagnostic.code()).isEqualTo(contract.get("closeFailureCode"));
      assertThat(diagnostic.originalError()).isSameAs(failure);
    });
  }

  @Test
  void executesModuleResolverRollbackContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("modules");
    DatafileContent datafile = fixtureDatafile();
    Segment enabled = new Segment();
    enabled.setConditions(Map.of("feature", "flag", "operator", "isEnabled"));
    datafile.setSegments(Map.of("enabled", enabled));
    DatafileMessage message = new DatafileMessage();
    MessageOverride override = new MessageOverride();
    override.setKey("enabled");
    override.setSegments("enabled");
    override.setTranslation("Enabled");
    message.setOverrides(List.of(override));
    datafile.setMessages(Map.of("value", message));
    datafile.setTranslations(Map.of("value", "Disabled"));

    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder()
        .datafile(datafile)
        .resolveFlag((key, context) -> false)
        .logLevel(LogLevel.FATAL)
        .build());
    MessagevisorChild child = m.spawn(Map.of());
    MessagevisorUnsubscribe remove = m.addModule(new MessagevisorModule() {
      @Override public String name() { return "enabled"; }
      @Override public void setup(MessagevisorModuleApi api) {
        api.setFlagResolver((key, context) -> true);
      }
    });
    assertThat(child.translate("value", Map.of())).isEqualTo("Enabled");

    m.addModule(new MessagevisorModule() {
      @Override public String name() { return "broken-resolver"; }
      @Override public void setup(MessagevisorModuleApi api) {
        api.setFlagResolver((key, context) -> false);
        throw new IllegalStateException("setup");
      }
    });
    if (Boolean.TRUE.equals(contract.get("failedSetupRestoresPreviousResolvers"))) {
      assertThat(m.translate("value", Map.of())).isEqualTo("Enabled");
    }
    remove.unsubscribe();
    if (Boolean.TRUE.equals(contract.get("removalRestoresPreviousResolvers"))) {
      assertThat(m.translate("value", Map.of())).isEqualTo("Disabled");
      assertThat(child.translate("value", Map.of())).isEqualTo("Disabled");
    }
  }

  @Test
  void executesStateEventOrderingAndSourceContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("events");
    List<String> observed = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().logLevel(LogLevel.FATAL).build());
    for (String source : (List<String>) contract.get("changeSources")) {
      EventName eventName = java.util.Arrays.stream(EventName.values())
          .filter(candidate -> candidate.wireValue().equals(source))
          .findFirst()
          .orElseThrow();
      m.on(eventName, ignored -> observed.add(source));
    }
    m.on(EventName.CHANGE, event -> observed.add("change:" + event.source().wireValue()));

    DatafileContent en = fixtureDatafile();
    m.setDatafile(en);
    DatafileContent nl = fixtureDatafile();
    nl.setLocale("nl");
    nl.setRevision("2");
    m.setDatafile(nl);
    m.setLocale("nl");
    m.setContext(Map.of("plan", "pro"));
    m.setCurrency("EUR");
    m.setTimeZone("UTC");

    if (Boolean.TRUE.equals(contract.get("stateEventBeforeChange"))) {
      assertThat(observed).containsExactly(
          "datafile_set", "change:datafile_set",
          "datafile_set", "change:datafile_set",
          "locale_set", "change:locale_set",
          "context_set", "change:context_set",
          "currency_set", "change:currency_set",
          "timeZone_set", "change:timeZone_set");
    }
  }

  @Test
  void executesChildOwnedDatafileEventContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("events");
    DatafileContent first = fixtureDatafile();
    Messagevisor parent = Messagevisor.create(
        MessagevisorOptions.builder().datafile(first).logLevel(LogLevel.FATAL).build());
    DatafileContent nl = fixtureDatafile();
    nl.setLocale("nl");
    nl.setRevision("nl-1");
    parent.setDatafile(nl);
    MessagevisorChild child = parent.spawn(
        Map.of("tenant", "child"), new MessagevisorSpawnOptions("nl", null, null));
    List<Map<String, Object>> trace = new ArrayList<>();
    List<MessagevisorSnapshot> capturedSnapshots = new ArrayList<>();
    child.on(EventName.DATAFILE_SET, event -> {
      trace.add(childEventTrace(event));
      capturedSnapshots.add(event.snapshot());
    });
    child.on(EventName.CHANGE, event -> {
      if (event.source() == EventName.DATAFILE_SET) trace.add(childEventTrace(event));
    });

    DatafileContent second = fixtureDatafile();
    second.setRevision("2");
    parent.setDatafile(second, true);
    assertThat(trace).isEqualTo(contract.get("childDatafileTrace"));
    assertThat(child.getSnapshot().version()).isEqualTo(1);
    assertThat(child.getSnapshot().locale()).isEqualTo("nl");
    assertThat(child.getContext()).containsEntry("tenant", "child");

    child.close();
    DatafileContent afterClose = fixtureDatafile();
    afterClose.setRevision("3");
    parent.setDatafile(afterClose, true);
    assertThat(trace).hasSize(((List<?>) contract.get("childDatafileTrace")).size());
    // This asserts captured event history, not a promise about a closed child's
    // live getSnapshot(). Datafiles remain owned by the root; no freeze is required.
    assertThat(capturedSnapshots.get(0).datafileRevisionsByLocale().get("en"))
        .isEqualTo(contract.get("capturedChildDatafileRevisionAfterClose"));
    assertThat(parent.getSnapshot().datafileRevisionsByLocale().get("en")).isEqualTo("3");
    assertThat(trace.get(0).get("datafileRevision"))
        .isEqualTo(contract.get("capturedChildDatafileRevisionAfterClose"));
  }

  @Test
  void executesApplicableNativeFallbackContract() throws Exception {
    Map<String, Object> hardening = (Map<String, Object>) fixture().get("hardening");
    Map<String, Object> fallback = (Map<String, Object>) hardening.get("fallbacks");
    Map<String, Object> zones = (Map<String, Object>) hardening.get("timeZones");
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    DatafileContent datafile = fixtureDatafile();
    datafile.getFormats().getDate().put("day", Map.of("year", "numeric"));
    try (Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(datafile)
        .timeZone((String) zones.get("preset")).logLevel(LogLevel.WARN).onDiagnostic(diagnostics::add).build())) {
      List<String> values = (List<String>) fallback.get("listInput");
      List<FormatPart> parts = m.formatListToParts(values);
      assertThat(parts.stream().filter(part -> part.type().equals("element")).map(FormatPart::value).toList())
          .isEqualTo(values);
      assertThat(parts.stream().map(FormatPart::value).collect(java.util.stream.Collectors.joining()))
          .isEqualTo(m.formatList(values));
      assertThat(diagnostics).isEmpty();
      // ICU4J is required and has native list parts and date ranges, so the fixture's
      // missing-Intl list/range separators do not apply. Date parts do use a literal
      // fallback and must retain their shape and report the canonical capability code.
      String instant = (String) zones.get("instant");
      assertThat(m.formatDateToParts(instant, "day", EvaluationOptions.builder().build()))
          .containsExactly(new FormatPart("literal", m.formatDate(instant, "day")));
      assertThat(diagnostics).extracting(MessagevisorDiagnostic::code)
          .containsExactly((String) fallback.get("diagnosticCode"));
    }
  }

  private static Map<String, Object> childEventTrace(MessagevisorEvent event) {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("type", event.type().wireValue());
    if (event.source() != null) result.put("source", event.source().wireValue());
    result.put("version", event.version());
    result.put("snapshotVersion", event.snapshot().version());
    result.put("previousSnapshotVersion", event.previousSnapshot().version());
    result.put("snapshotLocale", event.snapshot().locale());
    result.put("previousSnapshotLocale", event.previousSnapshot().locale());
    result.put("datafileLocale", event.datafile().getLocale());
    result.put("activeLocale", event.activeLocale());
    result.put("datafileRevision", event.datafile().getRevision());
    result.put("snapshotDatafileRevision", event.snapshot().datafileRevisionsByLocale().get("en"));
    result.put(
        "previousSnapshotDatafileRevision",
        event.previousSnapshot().datafileRevisionsByLocale().get("en"));
    return result;
  }

  @Test
  void executesDiagnosticEnvelopeAndFormatCodeContract() throws Exception {
    Map<String, Object> contract = (Map<String, Object>) fixture().get("diagnostics");
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor withoutDatafile = Messagevisor.create(MessagevisorOptions.builder()
        .locale("en")
        .logLevel(LogLevel.DEBUG)
        .onDiagnostic(diagnostics::add)
        .build());
    withoutDatafile.translate("missing", Map.of());

    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder()
        .datafile(fixtureDatafile())
        .logLevel(LogLevel.DEBUG)
        .onDiagnostic(diagnostics::add)
        .build());
    m.formatNumber(1, "missing");
    assertThatThrownBy(() -> m.formatNumber(
        1, Map.of("style", "currency", "currency", "INVALID")))
        .isInstanceOf(RuntimeException.class);

    assertThat(diagnostics).extracting(MessagevisorDiagnostic::code).contains(
        String.valueOf(contract.get("missingLocaleDatafileCode")),
        String.valueOf(contract.get("missingFormatCode")),
        String.valueOf(contract.get("invalidFormatCode")));
    if (Boolean.TRUE.equals(contract.get("detailsAlwaysPresent"))) {
      assertThat(diagnostics).allMatch(diagnostic -> diagnostic.details() != null);
    }
  }

  private static MessagevisorModule namedModule(String name, List<String> closed) {
    return new MessagevisorModule() {
      @Override public String name() { return name; }
      @Override public void close() { closed.add(name); }
    };
  }

  private static DatafileContent fixtureDatafile() throws Exception {
    Map<String, Object> first = ((List<Map<String, Object>>) fixture().get("translations")).get(0);
    return JsonSupport.MAPPER.convertValue(first.get("datafile"), DatafileContent.class);
  }

  @Test
  void bundledFixtureMatchesCanonicalContractWhenMonorepoIsAvailable() throws Exception {
    Path directory = Path.of("").toAbsolutePath();
    while (directory != null) {
      Path canonical = directory.resolve("messagevisor/conformance/sdk-v1.json");
      if (!Files.exists(canonical)) {
        directory = directory.getParent();
        continue;
      }
      Map<String, Object> canonicalFixture = JsonSupport.MAPPER.readValue(
          Files.readString(canonical), new TypeReference<>() {});
      assertThat(fixture()).isEqualTo(canonicalFixture);
      return;
    }
  }
}
