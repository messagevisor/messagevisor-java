# Messagevisor Java SDK

Core runtime SDK for Messagevisor Java.

- Java package: `com.messagevisor.sdk`
- Artifact: `com.messagevisor:messagevisor-sdk`
- Java baseline: 17+

The SDK consumes generated Messagevisor datafiles at runtime. It handles locale and context state, translation lookup, overrides, segment and condition evaluation, diagnostics, event subscriptions, modules, default fallbacks, and direct formatting helpers.

## Installation

Published releases use the `com.messagevisor` group:

```kotlin
dependencies {
    implementation("com.messagevisor:messagevisor-sdk:0.1.0")
}
```

For local development inside this repository, use `implementation(project(":sdk"))`.

## Create An Instance

```java
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .locale("en-US")
        .currency("USD")
        .timeZone("America/New_York")
        .build()
);
```

You can also create the SDK without an initial datafile:

```java
Messagevisor m = Messagevisor.create();

m.setDatafile(datafileJson);
```

If `locale`, `defaultTranslations`, or `defaultFormats` are provided without a datafile, fallback translations and direct format helpers can still work before project datafiles arrive.

## Datafiles

```java
m.setDatafile(datafileJson);
m.setDatafile(datafileJson, true);
```

`setDatafile(datafile)` stores the first datafile for a locale normally. If a datafile for the same locale already exists, it merges the incoming content by default. Pass `true` as the second argument to replace the same-locale datafile completely.

Supported inputs include parsed datafile objects and JSON strings.

## Context

```java
m.setContext(Map.of("platform", "web"));
m.setContext(Map.of("plan", "pro"));
m.setContext(Map.of("platform", "mobile"), true);
```

`setContext(context)` shallow-merges top-level keys by default. `setContext(context, true)` replaces the entire context. `getContext()` returns a shallow copy.

## Translations

```java
String text = m.translate("dashboard.welcome");
String alias = m.t("dashboard.welcome");

String withValues = m.translate(
    "dashboard.welcome",
    Map.of("name", "Ada")
);

String withDefault = m.translate(
    "unknown.key",
    Map.of(),
    TranslateOptions.builder()
        .locale("en-US")
        .defaultTranslation("Fallback text")
        .build()
);
```

Lookup order follows the TypeScript SDK contract:

1. datafile translation or matching override
2. `defaultTranslations`
3. missing-translation diagnostic
4. per-call `defaultTranslation`
5. message key

An empty string is a valid resolved translation and is returned as-is.

Per-call `locale` options are supported and do not mutate the active instance locale:

```java
String dutch = m.translate(
    "dashboard.welcome",
    Map.of("name", "Ada"),
    TranslateOptions.builder()
        .locale("nl-NL")
        .build()
);
```

## Formatting

Direct formatter helpers use datafile/default format presets plus per-call overrides:

```java
String number = m.formatNumber(1234.5, "compact");
String money = m.formatNumber(12, "money");

String euro = m.formatNumber(
    12,
    "money",
    EvaluationOptions.builder()
        .currency("EUR")
        .build()
);

String date = m.formatDate(
    instant,
    "shortDate",
    EvaluationOptions.builder()
        .locale("nl-NL")
        .timeZone("Europe/Amsterdam")
        .build()
);
```

Supported helpers include:

- numbers: `formatNumber`, `formatNumberToParts`
- dates and times: `formatDate`, `formatDateToParts`, `formatTime`, `formatTimeToParts`, `formatDateTimeRange`
- relative and plural: `formatRelativeTime`, `formatRelativeTimeToParts`, `formatPlural`
- lists and display names: `formatList`, `formatListToParts`, `formatDisplayName`

Per-call `locale`, `currency`, and `timeZone` options override the active locale, presets, and instance defaults. Use `formatNumber` with a currency preset or inline `style: currency` options for currency amounts.

`*ToParts` helpers return stable Java `FormatPart` records. Exact part shapes can differ from JavaScript `Intl.*.formatToParts`.

The implementation maps Messagevisor presets to generic ICU4J APIs. It does not contain locale-specific output fixes. Java/ICU-native CLDR and pattern differences belong in `expectedByRuntime.java`; genuinely unsupported options produce a non-fatal `unsupported_formatter` diagnostic.

## Conditions And Resolvers

The SDK evaluates Messagevisor attribute conditions, segments, feature conditions, and experiment variation conditions.

Feature and variation resolvers can be supplied at construction time:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .resolveFlag((featureKey, context) -> featureService.isEnabled(featureKey, context))
        .resolveVariation((experimentKey, context) -> experimentService.getVariation(experimentKey, context))
        .build()
);
```

Variation resolvers return a `String` or `null`. `null` means no variation matched.

## Modules

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .addModule(IcuModule.create())
        .build()
);

m.addModule(InterpolationModule.create());
m.removeModule("interpolation");
```

Modules can provide setup hooks, message formatting, output transforms, diagnostic subscriptions, close hooks, and resolver registration.

## Events And Diagnostics

The SDK emits lifecycle events such as context, locale, datafile, error, and change events. Diagnostics are delivered through `onDiagnostic`; error-level diagnostics also emit `EventName.ERROR`.

```java
m.on(EventName.ERROR, event -> {
    System.out.println(event.diagnostic());
});

m.subscribe(() -> {
    System.out.println(m.getSnapshot().version());
});
```

Diagnostics include missing locale, missing translation, invalid message, unsupported formatter, duplicate module, and other runtime issues. The Java port keeps the same portable diagnostic style as the TypeScript SDK.

## Testing

From `messagevisor-java/`:

```sh
./gradlew :sdk:test
```
