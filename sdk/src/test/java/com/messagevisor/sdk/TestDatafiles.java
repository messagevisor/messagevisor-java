package com.messagevisor.sdk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestDatafiles {
  private TestDatafiles() {}

  static DatafileContent enUs() {
    DatafileContent datafile = new DatafileContent();
    datafile.setSchemaVersion("1");
    datafile.setMessagevisorVersion("0.0.1");
    datafile.setRevision("en-1");
    datafile.setTarget("web");
    datafile.setLocale("en-US");
    datafile.setDirection("ltr");

    FormatPresets formats = new FormatPresets();
    formats.setNumber(
        mapOfMaps(
            "money",
            map("style", "currency", "currency", "USD", "currencyDisplay", "code"),
            "runtimeMoney",
            map("style", "currency", "currencyDisplay", "code"),
            "fixed",
            map("minimumFractionDigits", 2, "maximumFractionDigits", 2),
            "percent",
            map("style", "percent", "maximumFractionDigits", 1),
            "compactShort",
            map("notation", "compact", "compactDisplay", "short"),
            "compactLong",
            map("notation", "compact", "compactDisplay", "long"),
            "scientific",
            map("notation", "scientific"),
            "engineering",
            map("notation", "engineering"),
            "unitDistance",
            map("style", "unit", "unit", "kilometer", "unitDisplay", "short"),
            "signAlways",
            map("signDisplay", "always"),
            "signNever",
            map("signDisplay", "never"),
            "significant3",
            map("minimumSignificantDigits", 3, "maximumSignificantDigits", 3),
            "arabicDigits",
            map("numberingSystem", "arab")));
    formats.setDate(
        mapOfMaps(
            "short",
            map("dateStyle", "short", "timeZone", "UTC"),
            "numeric",
            map("year", "numeric", "month", "2-digit", "day", "2-digit", "timeZone", "UTC"),
            "weekday",
            map("weekday", "long", "year", "numeric", "month", "long", "day", "numeric", "timeZone", "UTC"),
            "buddhist",
            map("calendar", "buddhist", "year", "numeric", "month", "long", "day", "numeric", "era", "short", "timeZone", "UTC"),
            "arabicNumeric",
            map("numberingSystem", "arab", "year", "numeric", "month", "2-digit", "day", "2-digit", "timeZone", "UTC")));
    formats.setTime(
        mapOfMaps(
            "short",
            map("timeStyle", "short", "timeZone", "UTC"),
            "seconds",
            map("hour", "numeric", "minute", "2-digit", "second", "2-digit", "timeZone", "UTC"),
            "fractional",
            map("hour", "numeric", "minute", "2-digit", "second", "2-digit", "fractionalSecondDigits", 3, "timeZone", "UTC"),
            "period",
            map("hour", "numeric", "dayPeriod", "long", "timeZone", "UTC"),
            "zoneLong",
            map("hour", "numeric", "minute", "2-digit", "timeZoneName", "long", "timeZone", "UTC")));
    formats.setDateTimeRange(
        mapOfMaps(
            "event",
            map("year", "numeric", "month", "short", "day", "numeric", "hour", "numeric", "minute", "2-digit", "timeZone", "UTC")));
    formats.setRelative(mapOfMaps("auto", map("numeric", "auto", "style", "long")));
    datafile.setFormats(formats);

    Segment web = new Segment();
    web.setConditions(map("attribute", "platform", "operator", "equals", "value", "web"));
    datafile.setSegments(Map.of("platform-web", web));

    MessageOverride webOverride = new MessageOverride();
    webOverride.setKey("web");
    webOverride.setSegments("platform-web");
    webOverride.setTranslation("Hello web {name}");

    MessageOverride flagOverride = new MessageOverride();
    flagOverride.setKey("flag");
    flagOverride.setConditions(map("feature", "new-checkout", "operator", "isEnabled"));
    flagOverride.setTranslation("Feature enabled");

    MessageOverride experimentOverride = new MessageOverride();
    experimentOverride.setKey("experiment");
    experimentOverride.setConditions(
        map("experiment", "checkout-copy", "operator", "hasVariation", "value", "b"));
    experimentOverride.setTranslation("Experiment B");

    DatafileMessage greeting = new DatafileMessage();
    greeting.setOverrides(List.of(webOverride));
    DatafileMessage feature = new DatafileMessage();
    feature.setOverrides(List.of(flagOverride));
    DatafileMessage experiment = new DatafileMessage();
    experiment.setOverrides(List.of(experimentOverride));
    DatafileMessage deprecated = new DatafileMessage();
    deprecated.setDeprecated(true);
    deprecated.setDeprecationWarning("Use greeting instead.");

    datafile.setMessages(
        mapMessages(
            "greeting", greeting,
            "feature", feature,
            "experiment", experiment,
            "empty", new DatafileMessage(),
            "deprecated", deprecated,
            "total", new DatafileMessage()));
    datafile.setTranslations(
        mapStrings(
            "greeting", "Hello {name}",
            "feature", "Feature disabled",
            "experiment", "Experiment default",
            "empty", "",
            "deprecated", "Old",
            "total", "Total: {amount, number, money}"));
    return datafile;
  }

  static DatafileContent nlNl() {
    DatafileContent datafile = enUs();
    datafile.setRevision("nl-1");
    datafile.setLocale("nl-NL");

    FormatPresets formats = datafile.getFormats();
    formats.setNumber(
        mapOfMaps(
            "fixed",
            map("minimumFractionDigits", 1, "maximumFractionDigits", 1),
            "runtimeMoney",
            map("style", "currency", "currencyDisplay", "code")));
    formats.setDate(
        mapOfMaps(
            "numeric",
            map("year", "numeric", "month", "2-digit", "day", "2-digit", "timeZone", "UTC")));
    formats.setTime(
        mapOfMaps(
            "short",
            map("hour", "2-digit", "minute", "2-digit", "hour12", false, "timeZone", "UTC")));
    formats.setRelative(mapOfMaps("auto", map("numeric", "auto", "style", "long")));

    DatafileMessage greeting = datafile.getMessages().get("greeting");
    greeting.setMeta(map("locale", "nl-NL"));
    greeting.setDeprecated(true);
    greeting.setDeprecationWarning("Gebruik greeting.new.");

    MessageOverride webOverride = new MessageOverride();
    webOverride.setKey("web");
    webOverride.setSegments("platform-web");
    webOverride.setTranslation("Hallo web {name}");
    greeting.setOverrides(List.of(webOverride));

    datafile.setTranslations(
        mapStrings(
            "greeting", "Hallo {name}",
            "feature", "Feature disabled",
            "experiment", "Experiment default",
            "empty", "",
            "deprecated", "Oud",
            "total", "Totaal: {amount, number, runtimeMoney}"));
    return datafile;
  }

  static Map<String, Object> map(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put(String.valueOf(entries[i]), entries[i + 1]);
    }
    return result;
  }

  static Map<String, String> mapStrings(String... entries) {
    Map<String, String> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put(entries[i], entries[i + 1]);
    }
    return result;
  }

  static Map<String, DatafileMessage> mapMessages(Object... entries) {
    Map<String, DatafileMessage> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put(String.valueOf(entries[i]), (DatafileMessage) entries[i + 1]);
    }
    return result;
  }

  static Map<String, Map<String, Object>> mapOfMaps(Object... entries) {
    Map<String, Map<String, Object>> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      @SuppressWarnings("unchecked")
      Map<String, Object> value = (Map<String, Object>) entries[i + 1];
      result.put(String.valueOf(entries[i]), value);
    }
    return result;
  }
}
