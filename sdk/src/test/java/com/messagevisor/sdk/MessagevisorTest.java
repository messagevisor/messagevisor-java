package com.messagevisor.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MessagevisorTest {
  @Test
  void canBeCreatedWithoutDatafileAndLoadedLater() {
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().logLevel(LogLevel.FATAL).build());

    assertThat(m.getLocale()).isNull();
    assertThatThrownBy(m::getDatafile).hasMessage("Datafile not found: no locale is set");

    m.setDatafile(TestDatafiles.enUs());

    assertThat(m.getLocale()).isEqualTo("en-US");
    assertThat(m.getRawTranslation("greeting")).isEqualTo("Hello {name}");
  }

  @Test
  void reportsMissingMessageAndUsesDefaultTranslation() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .logLevel(LogLevel.ERROR)
                .onDiagnostic(diagnostics::add)
                .build());

    assertThat(m.getRawTranslation("missing")).isEqualTo("missing");
    assertThat(m.translate("missing", Map.of(), TranslateOptions.builder().defaultTranslation("Fallback").build()))
        .isEqualTo("Fallback");
    assertThat(diagnostics)
        .anyMatch(
            diagnostic ->
                diagnostic.code().equals("missing_translation")
                    && diagnostic.details().equals(Map.of("locale", "en-US", "messageKey", "missing", "source", "translation")));
  }

  @Test
  void distinguishesMissingDatafilesAndNamedFormats() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor withoutDatafile = Messagevisor.create(MessagevisorOptions.builder()
        .locale("en-US")
        .logLevel(LogLevel.ERROR)
        .onDiagnostic(diagnostics::add)
        .build());
    assertThat(withoutDatafile.translate("missing", Map.of())).isEqualTo("missing");
    assertThat(diagnostics).anyMatch(diagnostic -> diagnostic.code().equals("missing_datafile"));

    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder()
        .datafile(TestDatafiles.enUs())
        .logLevel(LogLevel.ERROR)
        .onDiagnostic(diagnostics::add)
        .build());
    m.formatNumber(1, "missing");
    assertThat(diagnostics).anyMatch(diagnostic -> diagnostic.code().equals("missing_format"));
    assertThatThrownBy(() ->
        m.formatNumber(1, TestDatafiles.map("style", "currency", "currency", "INVALID")))
        .isInstanceOf(RuntimeException.class);
    assertThat(diagnostics).anyMatch(diagnostic -> diagnostic.code().equals("invalid_format"));
  }

  @Test
  void normalizesDiagnosticDetailsAndChangesLogLevelAtRuntime() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .logLevel(LogLevel.FATAL)
                .onDiagnostic(diagnostics::add)
                .build());

    m.getRawTranslation("missing.before");
    assertThat(diagnostics).isEmpty();

    m.setLogLevel(LogLevel.ERROR);
    m.getRawTranslation("missing.after");

    assertThat(diagnostics)
        .containsExactly(
            MessagevisorDiagnostic.builder(LogLevel.ERROR, "missing_translation", "Missing translation")
                .detail("locale", "en-US")
                .detail("messageKey", "missing.after")
                .detail("source", "translation")
                .build());
  }

  @Test
  void treatsEmptyStringAsExplicitTranslation() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .defaultTranslations(Map.of("en-US", Map.of("empty", "Default")))
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.getRawTranslation("empty")).isEqualTo("");
  }

  @Test
  void mergesAndReplacesContext() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .context(TestDatafiles.map("platform", "web"))
                .logLevel(LogLevel.FATAL)
                .build());

    m.setContext(TestDatafiles.map("plan", "pro"));
    assertThat(m.getContext()).containsEntry("platform", "web").containsEntry("plan", "pro");

    m.setContext(TestDatafiles.map("plan", "free"), true);
    assertThat(m.getContext()).containsOnly(Map.entry("plan", "free"));
  }

  @Test
  void mergesAndReplacesSameLocaleDatafiles() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).logLevel(LogLevel.FATAL).build());
    DatafileContent partial = new DatafileContent();
    partial.setSchemaVersion("1");
    partial.setMessagevisorVersion("0.0.1");
    partial.setRevision("en-2");
    partial.setTarget("web");
    partial.setLocale("en-US");
    partial.setSegments(Map.of());
    partial.setMessages(Map.of("new", new DatafileMessage()));
    partial.setTranslations(Map.of("new", "New"));

    m.setDatafile(partial);
    assertThat(m.getRawTranslation("greeting")).isEqualTo("Hello {name}");
    assertThat(m.getRawTranslation("new")).isEqualTo("New");

    DatafileContent replacement = new DatafileContent();
    replacement.setSchemaVersion("1");
    replacement.setMessagevisorVersion("0.0.1");
    replacement.setRevision("en-3");
    replacement.setTarget("web");
    replacement.setLocale("en-US");
    replacement.setSegments(Map.of());
    replacement.setMessages(Map.of("new", new DatafileMessage()));
    replacement.setTranslations(Map.of("new", "New"));

    m.setDatafile(replacement, true);
    assertThat(m.getRawTranslation("greeting")).isEqualTo("greeting");
  }

  @Test
  void evaluatesOverridesThroughContextFlagsAndVariations() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .context(TestDatafiles.map("platform", "web"))
                .resolveFlag((key, context) -> key.equals("new-checkout"))
                .resolveVariation((key, context) -> key.equals("checkout-copy") ? "b" : null)
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.getRawTranslation("greeting")).isEqualTo("Hello web {name}");
    assertThat(m.getRawTranslation("feature")).isEqualTo("Feature enabled");
    assertThat(m.getRawTranslation("experiment")).isEqualTo("Experiment B");
  }

  @Test
  void usesPerCallLocaleWithoutMutatingActiveLocale() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    List<MessagevisorEvent> localeEvents = new ArrayList<>();
    List<String> moduleLocales = new ArrayList<>();
    MessagevisorModule captureModule =
        new MessagevisorModule() {
          @Override
          public String name() {
            return "capture";
          }

          @Override
          public Object format(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
            moduleLocales.add("format:" + payload.locale() + ":" + payload.meta().get("locale"));
            return null;
          }

          @Override
          public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
            moduleLocales.add("transform:" + payload.locale());
            return null;
          }
        };
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .logLevel(LogLevel.DEBUG)
                .onDiagnostic(diagnostics::add)
                .addModule(captureModule)
                .build());
    m.setDatafile(TestDatafiles.nlNl());
    m.on(EventName.LOCALE_SET, localeEvents::add);

    TranslateOptions nlOptions =
        TranslateOptions.builder()
            .locale("nl-NL")
            .context(TestDatafiles.map("platform", "web"))
            .build();

    assertThat(m.translate("greeting", TestDatafiles.map("name", "Ada"), nlOptions))
        .isEqualTo("Hallo web {name}");
    assertThat(m.getRawTranslation("greeting", TranslateOptions.builder().locale("nl-NL").build()))
        .isEqualTo("Hallo {name}");
    assertThat(m.getRawTranslation("missing.key", TranslateOptions.builder().locale("nl-NL").build()))
        .isEqualTo("missing.key");
    assertThat(m.getLocale()).isEqualTo("en-US");
    assertThat(localeEvents).isEmpty();
    assertThat(moduleLocales)
        .contains("format:nl-NL:nl-NL", "transform:nl-NL");
    assertThat(diagnostics)
        .anyMatch(
            diagnostic ->
                diagnostic.code().equals("message_override_matched")
                    && "nl-NL".equals(diagnostic.details().get("locale")))
        .anyMatch(
            diagnostic ->
                diagnostic.code().equals("deprecated_message")
                    && "nl-NL".equals(diagnostic.details().get("locale")))
        .anyMatch(
            diagnostic ->
                diagnostic.code().equals("missing_translation")
                    && "nl-NL".equals(diagnostic.details().get("locale")));
  }

  @Test
  void usesPerCallLocaleForDefaultsAndDirectFormatters() {
    FormatPresets enDefaults = new FormatPresets();
    enDefaults.setNumber(TestDatafiles.mapOfMaps("short", TestDatafiles.map("minimumFractionDigits", 1, "maximumFractionDigits", 1)));
    FormatPresets nlDefaults = new FormatPresets();
    nlDefaults.setNumber(TestDatafiles.mapOfMaps("short", TestDatafiles.map("minimumFractionDigits", 1, "maximumFractionDigits", 1)));
    nlDefaults.setDate(TestDatafiles.mapOfMaps("short", TestDatafiles.map("year", "numeric", "month", "long", "day", "numeric", "timeZone", "UTC")));
    nlDefaults.setTime(TestDatafiles.mapOfMaps("short", TestDatafiles.map("hour", "2-digit", "minute", "2-digit", "hour12", false, "timeZone", "UTC")));
    nlDefaults.setRelative(TestDatafiles.mapOfMaps("auto", TestDatafiles.map("numeric", "auto", "style", "long")));

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .locale("en-US")
                .defaultTranslations(Map.of("nl-NL", Map.of("greeting", "Hallo uit defaults")))
                .defaultFormats(Map.of("en-US", enDefaults, "nl-NL", nlDefaults))
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.translate("greeting", Map.of(), TranslateOptions.builder().locale("nl-NL").build()))
        .isEqualTo("Hallo uit defaults");
    assertThat(m.formatNumber(1200, "short")).isEqualTo("1,200.0");
    assertThat(m.formatNumber(1200, "short", EvaluationOptions.builder().locale("nl-NL").build()))
        .isEqualTo("1.200,0");
    assertThat(m.formatDate("2026-05-12T08:30:00Z", "short", EvaluationOptions.builder().locale("nl-NL").build()))
        .contains("2026");
    assertThat(m.formatTime("2026-05-12T08:30:00Z", "short", EvaluationOptions.builder().locale("nl-NL").timeZone("UTC").build()))
        .contains("08:30");
    assertThat(m.formatRelativeTime(-1, "day", "auto", EvaluationOptions.builder().locale("nl-NL").build()))
        .isEqualTo("gisteren");
    assertThat(m.formatPlural(0, EvaluationOptions.builder().locale("ar").build())).isEqualTo("zero");
    assertThat(m.formatList(List.of("A", "B"), TestDatafiles.map("locale", "nl-NL"))).contains(" en ");
    assertThat(m.formatDisplayName("NL", TestDatafiles.map("locale", "nl-NL", "type", "region")))
        .isEqualTo("Nederland");
    assertThat(m.getLocale()).isEqualTo("en-US");
  }

  @Test
  void exposesEventsAndSnapshots() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).logLevel(LogLevel.FATAL).build());
    List<MessagevisorEvent> events = new ArrayList<>();
    m.on(EventName.CONTEXT_SET, events::add);

    m.setContext(TestDatafiles.map("platform", "web"));

    assertThat(events).hasSize(1);
    assertThat(events.get(0).previousSnapshot().context()).isEmpty();
    assertThat(events.get(0).snapshot().context()).containsEntry("platform", "web");
    assertThat(events.get(0).replaced()).isFalse();
  }

  @Test
  void exposesChangeEventSourcesAndDatafileStorageDetails() {
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder()
        .datafile(TestDatafiles.enUs()).logLevel(LogLevel.FATAL).build());
    List<MessagevisorEvent> datafileEvents = new ArrayList<>();
    List<MessagevisorEvent> changeEvents = new ArrayList<>();
    m.on(EventName.DATAFILE_SET, datafileEvents::add);
    m.on(EventName.CHANGE, changeEvents::add);
    m.setDatafile(TestDatafiles.nlNl(), true);

    assertThat(datafileEvents).singleElement().satisfies(event -> {
      assertThat(event.locale()).isEqualTo("nl-NL");
      assertThat(event.activeLocale()).isEqualTo("en-US");
      assertThat(event.replaced()).isTrue();
      assertThat(event.source()).isNull();
    });
    assertThat(changeEvents.get(changeEvents.size() - 1).source()).isEqualTo(EventName.DATAFILE_SET);
  }

  @Test
  void emitsLifecycleEventsForEveryObservableStateChange() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).logLevel(LogLevel.FATAL).build());
    List<EventName> events = new ArrayList<>();
    for (EventName eventName :
        List.of(
            EventName.DATAFILE_SET,
            EventName.LOCALE_SET,
            EventName.CONTEXT_SET,
            EventName.CURRENCY_SET,
            EventName.TIME_ZONE_SET)) {
      m.on(eventName, event -> events.add(event.type()));
    }

    m.setContext(TestDatafiles.map("plan", "pro"));
    m.setCurrency("EUR");
    m.setTimeZone("UTC");
    m.setDatafile(TestDatafiles.nlNl());
    m.setLocale("nl-NL");

    assertThat(events)
        .containsExactly(
            EventName.CONTEXT_SET,
            EventName.CURRENCY_SET,
            EventName.TIME_ZONE_SET,
            EventName.DATAFILE_SET,
            EventName.LOCALE_SET);
  }

  @Test
  void formatsRuntimeCurrencyAndTimeZone() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .currency("EUR")
                .timeZone("UTC")
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.formatNumber(12, "runtimeMoney")).contains("EUR");
    assertThat(m.formatNumber(12, "runtimeMoney", EvaluationOptions.builder().currency("JPY").build())).contains("JPY");
    assertThat(m.formatPlural(1)).isEqualTo("one");
  }

  @Test
  void supportsExpandedFormatterHelpersAndParts() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .timeZone("UTC")
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.formatNumber(1200, "compactShort")).containsIgnoringCase("K");
    assertThat(m.formatNumber(12500, "compactShort")).isEqualTo("13K");
    assertThat(m.formatNumber(1840000, "compactShort")).isEqualTo("1.8M");
    assertThat(m.formatNumber(1200, "compactLong")).contains("thousand");
    assertThat(m.formatNumber(1200, "scientific")).contains("E");
    assertThat(m.formatNumber(1200, "engineering")).contains("E");
    assertThat(m.formatNumber(5, "unitDistance")).contains("km");
    assertThat(m.formatNumber(5, "signAlways")).startsWith("+");
    assertThat(m.formatNumber(-5, "signNever")).doesNotStartWith("-");
    assertThat(m.formatNumber(12345, "significant3")).contains("12");
    assertThat(m.formatNumber(2025, "arabicDigits")).isNotEqualTo("2,025");
    assertThat(m.formatNumberToParts(12, "fixed")).anyMatch(part -> part.type().equals("integer"));

    String instant = "2026-05-12T08:30:45.678Z";
    assertThat(m.formatDate(instant, "weekday")).contains("2026");
    assertThat(m.formatDate(instant, "buddhist")).contains("2569");
    assertThat(m.formatDate(instant, "arabicNumeric")).isNotEqualTo("05/12/2026");
    assertThat(m.formatTime(instant, "seconds")).contains("30");
    assertThat(m.formatTime(instant, "fractional")).contains("678");
    assertThat(m.formatTime(instant, "zoneLong")).containsIgnoringCase("Coordinated");
    assertThat(
            m.formatTime(
                "2026-11-26T15:05:00Z",
                TestDatafiles.map("hour", "numeric", "minute", "2-digit", "timeZone", "UTC", "timeZoneName", "shortGeneric")))
        .contains("3:05")
        .contains("PM")
        .contains("GMT");
    assertThat(m.formatDateToParts(instant, "numeric", EvaluationOptions.builder().build())).isNotEmpty();
    assertThat(m.formatTimeToParts(instant, "seconds", EvaluationOptions.builder().build())).isNotEmpty();
    assertThat(m.formatDateTimeRange("2026-05-12T08:00:00Z", "2026-05-12T09:30:00Z", "event"))
        .contains("2026");

    assertThat(m.formatRelativeTime(-1, "day", "auto")).isEqualTo("yesterday");
    assertThat(m.formatRelativeTimeToParts(3, "day", "auto")).isNotEmpty();
    assertThat(m.formatList(List.of("A", "B", "C"))).contains("A").contains("B").contains("C");
    assertThat(m.formatList(List.of("A", "B"), TestDatafiles.map("type", "disjunction"))).contains("or");
    assertThat(m.formatListToParts(List.of("A", "B"))).anyMatch(part -> part.type().equals("element"));
    assertThat(m.formatDisplayName("NL", TestDatafiles.map("type", "region"))).contains("Netherlands");
    assertThat(m.formatDisplayName("USD", TestDatafiles.map("type", "currency"))).containsIgnoringCase("dollar");
  }

  @Test
  void keepsPortableFormatterContractStableAcrossDirectHelpers() {
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .timeZone("UTC")
                .logLevel(LogLevel.FATAL)
                .build());
    String instant = "2026-05-12T08:30:45.678Z";

    assertThat(m.formatNumber(1234567.891)).isEqualTo("1,234,567.891");
    assertThat(m.formatNumber(12, "fixed")).isEqualTo("12.00");
    assertThat(m.formatNumber(12345.678, "significant3")).isEqualTo("12,300");
    assertThat(m.formatNumber(5, "signAlways")).isEqualTo("+5");
    assertThat(m.formatNumber(12500, "compactShort")).isEqualTo("13K");
    assertThat(m.formatNumber(12500, "compactLong")).contains("13");
    assertThat(m.formatNumber(42, TestDatafiles.map("style", "unit", "unit", "kilometer", "unitDisplay", "long")))
        .isEqualTo("42 kilometers");
    assertThat(m.formatNumber(99.5, TestDatafiles.map("style", "currency", "currency", "GBP"))).isEqualTo("£99.50");
    assertThat(m.formatNumber(1999.99, TestDatafiles.map("style", "currency", "currency", "JPY"))).isEqualTo("¥2,000");
    assertThat(m.formatNumber(0.0575, "percent")).isEqualTo("5.8%");
    assertThat(m.formatDate(instant, "numeric")).isEqualTo("05/12/2026");
    assertThat(m.formatDate(instant, TestDatafiles.map("year", "numeric", "month", "long", "day", "numeric", "timeZone", "UTC")))
        .isEqualTo("May 12, 2026");
    assertThat(m.formatTime(instant, TestDatafiles.map("hour", "numeric", "minute", "2-digit", "timeZone", "UTC")))
        .isEqualTo("8:30\u202FAM");
    assertThat(
            m.formatTime(
                instant,
                TestDatafiles.map("hour", "numeric", "minute", "2-digit", "timeZone", "UTC", "timeZoneName", "short")))
        .isEqualTo("8:30\u202FAM UTC");
    assertThat(
            m.formatTime(
                instant,
                TestDatafiles.map(
                    "hour", "numeric",
                    "minute", "2-digit",
                    "timeZone", "America/New_York",
                    "timeZoneName", "shortGeneric")))
        .isEqualTo("4:30\u202FAM ET");
    assertThat(m.formatList(List.of("A", "B", "C"), TestDatafiles.map("type", "conjunction"))).isEqualTo("A, B, and C");
    assertThat(m.formatDisplayName("NL", TestDatafiles.map("type", "region"))).isEqualTo("Netherlands");
    assertThat(m.formatRelativeTime(-2, "day", "auto")).isEqualTo("2 days ago");
  }

  @Test
  void supportsDefaultFormatsBeforeDatafileAndInlineFormatterOptions() {
    FormatPresets defaults = new FormatPresets();
    defaults.setNumber(TestDatafiles.mapOfMaps("money", TestDatafiles.map("style", "currency", "currencyDisplay", "code")));
    defaults.setDate(TestDatafiles.mapOfMaps("short", TestDatafiles.map("year", "numeric", "month", "2-digit", "day", "2-digit")));

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .locale("en-US")
                .currency("GBP")
                .timeZone("UTC")
                .defaultFormats(Map.of("en-US", defaults))
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.formatNumber(12, "money")).contains("GBP");
    assertThat(m.formatNumber(12, TestDatafiles.map("style", "currency", "currency", "EUR", "currencyDisplay", "code"))).contains("EUR");
    assertThat(m.formatNumber(12, "money", EvaluationOptions.builder().currency("JPY").build())).contains("JPY");
    assertThat(m.formatDate("2026-05-12T00:00:00Z", "short")).contains("2026");
  }

  @Test
  void doesNotReportDiagnosticsForSupportedFormatterOptions() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .logLevel(LogLevel.WARN)
                .onDiagnostic(diagnostics::add)
                .build());

    m.formatNumber(12.03, TestDatafiles.map("roundingPriority", "morePrecision"));
    m.formatNumber(12, TestDatafiles.map("maximumFractionDigits", 2));

    assertThat(diagnostics)
        .noneMatch(
            diagnostic ->
                diagnostic.code().equals("unsupported_formatter")
                    && (diagnostic.message().contains("roundingPriority")
                        || diagnostic.message().contains("maximumFractionDigits")));
  }

  @Test
  void formatsNonLatinLocaleThroughIcuWithoutSdkStringHacks() {
    DatafileContent datafile = TestDatafiles.enUs();
    datafile.setLocale("bn-BD");
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder().datafile(datafile).timeZone("UTC").logLevel(LogLevel.FATAL).build());

    assertThat(m.formatNumber(1234567.891)).isEqualTo("১২,৩৪,৫৬৭.৮৯১");
    assertThat(m.formatNumber(42, TestDatafiles.map("style", "unit", "unit", "kilometer", "unitDisplay", "long")))
        .isEqualTo("৪২ কিলোমিটার");
    assertThat(m.formatTime("2026-05-12T08:30:45.678Z", TestDatafiles.map("hour", "numeric", "minute", "2-digit", "timeZone", "UTC", "timeZoneName", "short")))
        .contains("৮:৩০");
  }

  @Test
  void formatterSourceAvoidsLocaleSpecificVisibleTextHacks() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/messagevisor/sdk/MessagevisorFormatters.java"));

    assertThat(source).doesNotContain("en_US_POSIX");
    assertThat(source).doesNotContain("Europe/Amsterdam");
    assertThat(source).doesNotContain("America/New_York");
    assertThat(source).doesNotContain("Asia/Tokyo");
    assertThat(source).doesNotContain("+ \" \" + unit");
    assertThat(source).doesNotContain("\" km\"");
    assertThat(source).doesNotContain("replace(\"K\"");
    assertThat(source).doesNotContain("replace(\"thousand\"");
  }

  @Test
  void cleansAnonymousModuleDiagnosticSubscriptionsOnRemoval() {
    AtomicInteger handled = new AtomicInteger();
    MessagevisorModule anonymous =
        new MessagevisorModule() {
          @Override
          public void setup(MessagevisorModuleApi api) {
            api.onDiagnostic(
                diagnostic -> handled.incrementAndGet(),
                new MessagevisorModuleDiagnosticOptions(LogLevel.ERROR));
          }
        };

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .logLevel(LogLevel.FATAL)
                .build());

    m.addModule(anonymous);
    m.getRawTranslation("missing.before");
    assertThat(handled.get()).isEqualTo(1);

    m.removeModule(null);
    m.getRawTranslation("missing.after");
    assertThat(handled.get()).isEqualTo(1);
  }

  @Test
  void restoresResolverPrecedenceAfterModuleRemovalAndSetupFailure() {
    DatafileContent datafile = TestDatafiles.enUs();
    Segment enabled = new Segment();
    enabled.setConditions(TestDatafiles.map("feature", "flag", "operator", "isEnabled"));
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
    m.addModule(new MessagevisorModule() {
      @Override public String name() { return "enabled"; }
      @Override public void setup(MessagevisorModuleApi api) { api.setFlagResolver((key, context) -> true); }
    });
    assertThat(child.translate("value", Map.of())).isEqualTo("Enabled");

    m.addModule(new MessagevisorModule() {
      @Override public String name() { return "broken"; }
      @Override public void setup(MessagevisorModuleApi api) {
        api.setFlagResolver((key, context) -> false);
        throw new IllegalStateException("setup");
      }
    });
    assertThat(m.translate("value", Map.of())).isEqualTo("Enabled");
    m.removeModule("enabled");
    m.removeModule("enabled");
    assertThat(m.translate("value", Map.of())).isEqualTo("Disabled");
    assertThat(child.translate("value", Map.of())).isEqualTo("Disabled");
  }

  @Test
  void addModuleReturnsAnIdempotentRemovalFunction() {
    AtomicInteger closes = new AtomicInteger();
    Messagevisor m = Messagevisor.create(
        MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).build());
    MessagevisorUnsubscribe remove = m.addModule(new MessagevisorModule() {
      @Override public String name() { return "removable"; }
      @Override public void close() { closes.incrementAndGet(); }
    });

    remove.unsubscribe();
    remove.unsubscribe();

    assertThat(closes.get()).isEqualTo(1);
  }

  @Test
  void childInstancesShareDatafilesAndModulesButKeepRequestStateIsolated() {
    AtomicInteger transforms = new AtomicInteger();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder()
        .datafile(TestDatafiles.enUs())
        .context(TestDatafiles.map("request", "parent"))
        .addModule(new MessagevisorModule() {
          @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
            transforms.incrementAndGet();
            return payload.translation();
          }
        })
        .logLevel(LogLevel.FATAL)
        .build());
    MessagevisorChild child = m.spawn(
        TestDatafiles.map("request", "child"),
        MessagevisorSpawnOptions.builder().currency("EUR").build());

    assertThat(child.getContext()).containsEntry("request", "child");
    assertThat(m.getContext()).containsEntry("request", "parent");
    assertThat(child.getCurrency()).isEqualTo("EUR");
    assertThat(child.translate("greeting", Map.of())).isEqualTo("Hello {name}");
    assertThat(transforms.get()).isEqualTo(1);
    child.close();
    assertThat(m.translate("greeting", Map.of())).isEqualTo("Hello {name}");
    assertThat(transforms.get()).isEqualTo(2);
  }

  @Test
  void childObservesParentDatafileEventsUntilClosed() {
    Messagevisor parent = Messagevisor.create(
        MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).logLevel(LogLevel.FATAL).build());
    MessagevisorChild child = parent.spawn(Map.of());
    List<String> datafileRevisions = new ArrayList<>();
    List<EventName> changeSources = new ArrayList<>();

    child.on(EventName.DATAFILE_SET, event -> {
      datafileRevisions.add(event.datafile().getRevision());
      assertThat(event.snapshot().context()).containsEntry("plan", "pro");
      assertThat(event.snapshot().version()).isEqualTo(2);
      assertThat(event.previousSnapshot().version()).isEqualTo(1);
    });
    child.on(EventName.CHANGE, event -> changeSources.add(event.source()));
    child.setContext(Map.of("plan", "pro"));

    DatafileContent second = TestDatafiles.enUs();
    second.setRevision("2");
    parent.setDatafile(second, true);
    assertThat(datafileRevisions).containsExactly("2");
    assertThat(changeSources).containsExactly(EventName.CONTEXT_SET, EventName.DATAFILE_SET);

    child.close();
    DatafileContent third = TestDatafiles.enUs();
    third.setRevision("3");
    parent.setDatafile(third, true);
    assertThat(datafileRevisions).containsExactly("2");
    assertThat(changeSources).containsExactly(EventName.CONTEXT_SET, EventName.DATAFILE_SET);
  }

  @Test
  void closeRunsModulesInReverseOrderAndAggregatesErrors() {
    List<String> closed = new ArrayList<>();
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    MessagevisorModule first =
        new MessagevisorModule() {
          @Override
          public String name() {
            return "first";
          }

          @Override
          public void close() {
            closed.add("first");
          }
        };
    MessagevisorModule second =
        new MessagevisorModule() {
          @Override
          public String name() {
            return "second";
          }

          @Override
          public void close() {
            closed.add("second");
            throw new IllegalStateException("boom");
          }
        };

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .addModule(first)
                .addModule(second)
                .logLevel(LogLevel.ERROR)
                .onDiagnostic(diagnostics::add)
                .build());

    assertThatThrownBy(m::close).isInstanceOf(MessagevisorCloseException.class);
    assertThat(closed).containsExactly("second", "first");
    assertThat(diagnostics)
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.level()).isEqualTo(LogLevel.ERROR);
              assertThat(diagnostic.code()).isEqualTo("module_close_error");
              assertThat(diagnostic.message()).isEqualTo("Module close failed");
              assertThat(diagnostic.moduleName()).isEqualTo("second");
              assertThat(diagnostic.details()).isEmpty();
              assertThat(diagnostic.originalError()).isInstanceOf(IllegalStateException.class);
            });
  }

  @Test
  void ignoresStateMutationsAndModuleChangesAfterClose() {
    List<String> calls = new ArrayList<>();
    MessagevisorModule module =
        new MessagevisorModule() {
          @Override
          public String name() {
            return "closer";
          }

          @Override
          public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
            calls.add("transform");
            return null;
          }

          @Override
          public void close() {
            calls.add("close");
          }
        };
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(TestDatafiles.enUs())
                .addModule(module)
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.translate("greeting", Map.of())).isEqualTo("Hello {name}");
    m.close();
    m.addModule(
        new MessagevisorModule() {
          @Override
          public String name() {
            return "late";
          }

          @Override
          public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
            calls.add("late-transform");
            return null;
          }
        });
    m.removeModule("closer");
    MessagevisorUnsubscribe unsubscribe = m.subscribe(() -> calls.add("change"));
    unsubscribe.unsubscribe();
    m.setContext(TestDatafiles.map("platform", "mobile"));

    assertThat(m.translate("greeting", Map.of())).isEqualTo("Hello {name}");
    assertThat(calls).containsExactly("transform", "close");
  }
}
