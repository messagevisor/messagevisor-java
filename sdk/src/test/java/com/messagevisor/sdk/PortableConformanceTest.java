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
  private static Map<String, Object> fixture() throws Exception {
    try (InputStream input = PortableConformanceTest.class.getResourceAsStream("/conformance/sdk-v1.json")) {
      return JsonSupport.MAPPER.readValue(input, new TypeReference<>() {});
    }
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

    DatafileContent incoming = fixtureDatafile();
    incoming.setRevision("2");
    incoming.setTarget("mobile");
    incoming.setMessages(Map.of("second", new DatafileMessage()));
    incoming.setTranslations(Map.of("second", "Second"));
    m.setDatafile(incoming);
    if (Boolean.TRUE.equals(contract.get("mergeByDefault"))) {
      assertThat(m.getDatafile().getTranslations()).containsEntry("welcome", "Base").containsEntry("second", "Second");
    }

    DatafileContent otherLocale = fixtureDatafile();
    otherLocale.setLocale("nl");
    otherLocale.setRevision("nl-1");
    m.setDatafile(otherLocale);
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
    if (Boolean.TRUE.equals(contract.get("removalIsIdempotent"))) remove.unsubscribe();
    m.addModule(namedModule("first", closed));
    m.addModule(namedModule("last", closed));
    m.close();

    assertThat(diagnostics).extracting(MessagevisorDiagnostic::code)
        .contains(String.valueOf(contract.get("duplicateCode")), String.valueOf(contract.get("setupFailureCode")));
    assertThat(closed).containsExactly("broken", "dynamic", "last", "first", "duplicate");
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
    child.on(EventName.DATAFILE_SET, event -> trace.add(childEventTrace(event)));
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
    assertThat(trace.get(0).get("datafileRevision"))
        .isEqualTo(contract.get("childDatafileRevisionAfterClose"));
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
    Path canonical = Path.of("../messagevisor/conformance/sdk-v1.json");
    if (Files.exists(canonical)) {
      Map<String, Object> canonicalFixture = JsonSupport.MAPPER.readValue(
          Files.readString(canonical), new TypeReference<>() {});
      assertThat(fixture()).isEqualTo(canonicalFixture);
    }
  }
}
