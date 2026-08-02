# Messagevisor Java SDK <!-- omit in toc -->

Messagevisor's Java SDK is the Java 17 runtime package for evaluating translations from Messagevisor datafiles. It mirrors the portable behavior of the JavaScript SDK while using idiomatic Java APIs and Java/ICU-native formatting where that is the right fit.

The repository also includes Java modules and a conformance CLI for checking the SDK against real Messagevisor projects.

## Table of contents <!-- omit in toc -->

- [Installation](#installation)
- [Initialization](#initialization)
- [Datafile fetching](#datafile-fetching)
- [Recommended modules](#recommended-modules)
- [Translations](#translations)
  - [Translating with values](#translating-with-values)
  - [`t` alias](#t-alias)
  - [`formatMessage`](#formatmessage)
  - [`getRawTranslation`](#getrawtranslation)
- [Context](#context)
  - [Setting initial context](#setting-initial-context)
  - [Setting after initialization](#setting-after-initialization)
  - [Replacing the whole context](#replacing-the-whole-context)
  - [Per-translation context](#per-translation-context)
- [Datafile operations](#datafile-operations)
  - [Initial datafile](#initial-datafile)
  - [Setting datafile afterwards](#setting-datafile-afterwards)
  - [Updating datafile](#updating-datafile)
  - [Replacing datafile](#replacing-datafile)
  - [Split datafiles](#split-datafiles)
- [Changing locale](#changing-locale)
  - [Per-call locale](#per-call-locale)
- [Currency](#currency)
  - [Currency at initialization](#currency-at-initialization)
  - [Setting currency afterwards](#setting-currency-afterwards)
- [Time zone](#time-zone)
  - [Time zone at initialization](#time-zone-at-initialization)
  - [Setting time zone afterwards](#setting-time-zone-afterwards)
- [Formatting values](#formatting-values)
  - [Per-call format overrides](#per-call-format-overrides)
  - [Number formatting](#number-formatting)
  - [Date formatting](#date-formatting)
  - [Time formatting](#time-formatting)
  - [Relative time formatting](#relative-time-formatting)
  - [List formatting](#list-formatting)
  - [Display name formatting](#display-name-formatting)
  - [Plural rules](#plural-rules)
- [Default translations](#default-translations)
  - [Instance-level translations](#instance-level-translations)
  - [Per-call fallback](#per-call-fallback)
- [Default formats](#default-formats)
- [Resolvers](#resolvers)
  - [Feature flags](#feature-flags)
  - [Experiment variations](#experiment-variations)
- [Request-scoped child instances](#request-scoped-child-instances)
- [Diagnostics](#diagnostics)
  - [Log level](#log-level)
  - [Diagnostic events](#diagnostic-events)
  - [Diagnostic codes](#diagnostic-codes)
- [Events](#events)
  - [Event types](#event-types)
- [Modules](#modules)
  - [Registering modules in SDK](#registering-modules-in-sdk)
  - [Registering at project level](#registering-at-project-level)
  - [Creating a custom module](#creating-a-custom-module)
- [Missing translations module](#missing-translations-module)
- [Closing the SDK](#closing-the-sdk)
- [Translation lookup](#translation-lookup)
  - [Resolving the source string](#resolving-the-source-string)
  - [Formatting the result](#formatting-the-result)
- [CLI and project conformance](#cli-and-project-conformance)
- [Build and test](#build-and-test)
  - [Releasing](#releasing)
- [Repository layout](#repository-layout)
- [Formatting notes](#formatting-notes)
- [Related](#related)
- [License](#license)

<!-- MESSAGEVISOR_DOCS_BEGIN -->

## Installation

Published artifacts use the `com.messagevisor` group:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/messagevisor/messagevisor-java")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

GitHub Packages requires authentication, including for public packages. Use a GitHub personal access token with `read:packages` through the `gpr.user` and `gpr.key` Gradle properties, or the corresponding environment variables.

```kotlin
dependencies {
    implementation("com.messagevisor:messagevisor-sdk:0.2.0")
    implementation("com.messagevisor:messagevisor-module-icu:0.2.0")
}
```

Repository builds use the version in `gradle.properties`; `-Pversion=<version>` overrides it for a release. Maintainers can publish signed artifacts to a Maven-compatible repository by setting `MAVEN_REPOSITORY_URL`, repository credentials, and the in-memory `SIGNING_KEY` and `SIGNING_PASSWORD`, then running `./gradlew publish`. Use `publishToMavenLocal` for a local release smoke test. Publishing a GitHub release derives every artifact and CLI version from the release tag and publishes to GitHub Packages using the workflow's `GITHUB_TOKEN`.

## Initialization

The SDK can be initialized with or without a datafile:

```java
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .build()
);

System.out.println(m.translate("auth.signin"));
```

## Datafile fetching

The SDK itself has no opinion on how you load datafiles. You can either:

- fetch the JSON datafile in your application runtime, or
- bundle/read it along with your application

```java
String datafileJson = Files.readString(Path.of("datafiles/messagevisor-web-en.json"));

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .build()
);
```

To make the most out of Messagevisor, it is recommended that datafiles are loaded in the runtime, ideally through a CDN or another deployment pipeline suitable for your application.

## Recommended modules

When dealing with translations, it is very likely that you will want to use additional modules to extend runtime behavior.

The Java repository includes:

- `module-icu`: ICU MessageFormat support backed by ICU4J classic.
- `module-interpolation`: simple `{name}` placeholder interpolation.
- `module-missing-translations`: missing translation diagnostic observer.

Register modules on the SDK instance:

```java
import com.messagevisor.modules.icu.IcuModule;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .addModule(IcuModule.create())
        .build()
);
```

There is also a simpler interpolation module that can be used to interpolate primitive values into messages without the full ICU MessageFormat syntax.

## Translations

Now that we have the SDK instance, we can start translating messages.

```java
m.translate("auth.signin");
// → "Sign in"
```

This returns the translated message for the key `auth.signin`.

### Translating with values

If we are using either the ICU or interpolation modules, we can pass values to the `translate` method:

```java
m.translate("dashboard.welcome", Map.of("name", "Ada"));
// → "Welcome back, Ada"
```

This assumes the original message translation is `Welcome back, {name}`.

When we pass the values map as `Map.of("name", "Ada")`, a registered formatting module replaces placeholders. Without a module, `translate` returns the raw source string from the datafile with placeholders unchanged.

### `t` alias

`t` is an alias for `translate` with the same behavior:

```java
m.t("dashboard.welcome", Map.of("name", "Ada"), TranslateOptions.empty());
```

### `formatMessage`

Use `formatMessage` to format an arbitrary string that is not looked up from the datafile, using the same module pipeline as `translate`:

```java
m.formatMessage("Hello {name}", Map.of("name", "Ada"));
```

### `getRawTranslation`

Use `getRawTranslation` when you need the resolved source string before modules format it:

```java
String raw = m.getRawTranslation("dashboard.total");
m.formatMessage(raw, Map.of("amount", 1200));
```

## Context

Contexts let you translate messages based on the current user, request, or any other runtime information.

Override conditions and segment rules are evaluated against the merged context described below. See [Translation lookup](#translation-lookup) for the full order of operations.

### Setting initial context

If you already have some contextual info available at SDK initialization time, you can set it as follows:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .context(Map.of(
            "platform", "web",
            "browser", "chrome"
        ))
        .build()
);
```

### Setting after initialization

`setContext` shallow-merges new top-level keys into the existing instance context:

```java
m.setContext(Map.of(
    "userId", "user-123",
    "plan", "pro"
));
```

If the instance already had `{ platform: "web" }`, the context is now `{ platform: "web", userId: "user-123", plan: "pro" }`.

### Replacing the whole context

Pass `true` as the second argument when you intentionally want to replace the whole instance context:

```java
m.setContext(
    Map.of(
        "userId", "user-123",
        "plan", "pro"
    ),
    true
);
```

The merge is top-level only. Nested objects are replaced at their key, not deep-merged.

### Per-translation context

If you do not want to set context at SDK instance level, pass it in `TranslateOptions`:

```java
m.translate(
    "dashboard.welcome",
    Map.of("name", "Ada"),
    TranslateOptions.builder()
        .context(Map.of("plan", "enterprise"))
        .build()
);
```

## Datafile operations

Messagevisor SDK is designed to handle multiple datafiles at once, usually belonging to different locales or targets.

### Initial datafile

If you already have the datafile available at initialization time, set it directly:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .build()
);
```

The generated datafile already contains locale info, so you do not need to pass it manually.

### Setting datafile afterwards

You can also set the datafile later:

```java
m.setDatafile(datafileJson);
```

When no locale is active yet, the first successful `setDatafile` call also sets the active locale from the datafile's own `locale` field.

### Updating datafile

You can keep calling `setDatafile` to update the datafile for a given locale.

If the SDK already has a datafile for the incoming locale, `setDatafile` merges the incoming datafile with the existing one by default. Segments, messages, and translations are merged by key, while top-level fields such as `revision`, `target`, and `formats` come from the incoming datafile.

### Replacing datafile

Pass `true` as the second argument when you intentionally want to replace the existing datafile for that locale:

```java
m.setDatafile(freshDatafileJson, true);
```

### Split datafiles

It is possible to split translations into multiple datafiles, even when they belong to the same locale.

This is common when you have multiple targets in your Messagevisor project, where each target produces its own datafiles based on filtering rules.

For example, you may have two targets for your web app called `homepage` and `checkout`, and you do not want to load translations for the `checkout` page until the user has navigated to it.

In those cases, call `setDatafile` for each datafile. Same-locale datafiles merge by default:

```java
m.setDatafile(datafileForAnotherTarget);
```

The datafile content already has locale information, so no need to pass anything else manually.

## Changing locale

Once you have the desired datafiles set, switch between loaded locales at runtime with `setLocale`:

```java
m.setLocale("nl-NL");
```

The SDK switches the active locale to one that already has a datafile loaded. `setLocale` throws if that locale has no datafile yet.

**NOTE**: `setDatafile` for a new locale does not change the active locale automatically. The first datafile loaded becomes active, and later `setDatafile` calls for other locales only register those datafiles until you call `setLocale`.

You can verify the active locale with:

```java
System.out.println(m.getLocale());
// → "en-US"
```

### Per-call locale

For server-side applications, or any place where one SDK instance has multiple locale datafiles loaded, pass `locale` in call options to evaluate one request against a different locale without changing instance state:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(enDatafileJson)
        .build()
);

m.setDatafile(nlDatafileJson);

m.translate(
    "checkout.title",
    Map.of(),
    TranslateOptions.builder()
        .locale("nl-NL")
        .build()
);

m.getRawTranslation(
    "checkout.title",
    TranslateOptions.builder()
        .locale("nl-NL")
        .build()
);

m.formatNumber(
    1200,
    "decimal",
    EvaluationOptions.builder()
        .locale("nl-NL")
        .build()
);
```

Per-call `locale` applies only to that call. It does not emit `LOCALE_SET`, does not emit `CHANGE`, and does not change `m.getLocale()` or `m.getSnapshot().locale()`.

Per-call locale is evaluation state, not context. If a segment or condition needs a locale value as an attribute, pass it explicitly in `context`.

## Currency

It can be useful to define formats without mentioning the currency code directly, leaving the SDK to fill it in at runtime.

### Currency at initialization

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .currency("EUR")
        .build()
);
```

### Setting currency afterwards

```java
m.setCurrency("EUR");
```

## Time zone

Similar to currency, time zone info can also be filled in at runtime.

### Time zone at initialization

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .timeZone("Europe/Amsterdam")
        .build()
);
```

### Setting time zone afterwards

```java
m.setTimeZone("Europe/Amsterdam");
```

## Formatting values

Next to evaluating translations, the SDK also allows you to format numbers, dates, times, relative times, lists, display names, and plural categories.

To format values inside translations, use the ICU or interpolation modules.

### Per-call format overrides

Direct format helpers accept `EvaluationOptions` for per-call overrides. Use `currency` for currency number formats and `timeZone` for date, time, and date-time-range formats when a single call needs to differ from the SDK instance or authored preset.

### Number formatting

Use `formatNumber` to format numbers with the current locale. Pass a format preset name from the active locale's `formats.number`, or pass inline options:

```java
m.formatNumber(1200, "money");
// → "$1,200.00"

m.formatNumber(
    1200,
    Map.of(
        "notation", "compact",
        "compactDisplay", "short"
    )
);
// → "1.2K"

m.formatNumber(
    12,
    "money",
    EvaluationOptions.builder()
        .currency("EUR")
        .build()
);
```

Use `formatNumberToParts` when you need segmented output. Java returns stable `FormatPart` records, but exact part shapes can differ from JavaScript `Intl.NumberFormat.formatToParts`.

### Date formatting

Use `formatDate` for calendar dates. Values can be ISO strings, date-like values supported by the SDK formatter, or Java date/time objects supported by the implementation:

```java
m.formatDate("2026-05-12T08:30:00Z", "short");

m.formatDate(
    "2026-05-12T08:30:00Z",
    Map.of(
        "year", "numeric",
        "month", "long",
        "day", "numeric"
    )
);

m.formatDate(
    "2026-05-12T08:30:00Z",
    "weekday",
    EvaluationOptions.builder()
        .timeZone("Europe/Amsterdam")
        .build()
);
```

Time zone resolution follows the same rules as [Time zone](#time-zone): per-call `timeZone`, then the preset's `timeZone`, then the instance time zone.

Use `formatDateToParts` for segmented output:

```java
m.formatDateToParts(
    "2026-05-12T08:30:00Z",
    "weekday",
    EvaluationOptions.builder()
        .timeZone("Europe/Amsterdam")
        .build()
);
```

For a start/end range, use `formatDateTimeRange`:

```java
m.formatDateTimeRange(
    startsAt,
    endsAt,
    "event",
    EvaluationOptions.builder()
        .timeZone("America/Los_Angeles")
        .build()
);
```

### Time formatting

Use `formatTime` for clock times. It reads presets from `formats.time`:

```java
m.formatTime(startsAt, "short");

m.formatTime(
    startsAt,
    "event",
    EvaluationOptions.builder()
        .timeZone("America/New_York")
        .build()
);
```

You can pass inline options instead of a preset name. Use `formatTimeToParts` when you need segmented output.

### Relative time formatting

Use `formatRelativeTime` with a numeric offset and a unit such as `"second"`, `"minute"`, `"hour"`, `"day"`, `"week"`, `"month"`, or `"year"`.

```java
m.formatRelativeTime(-1, "day", "short");
// → "yesterday"

m.formatRelativeTime(
    3,
    "hour",
    "short",
    EvaluationOptions.builder()
        .locale("nl-NL")
        .build()
);
```

Use `formatRelativeTimeToParts` when you need segmented output.

### List formatting

Use `formatList` to join strings with locale-aware conjunctions and separators:

```java
m.formatList(
    List.of("HTML", "CSS", "Java"),
    Map.of("type", "conjunction")
);

m.formatList(
    List.of("A", "B", "C"),
    Map.of("type", "disjunction")
);
```

Options are mapped to ICU4J list formatting behavior. Use `formatListToParts` for simplified element parts.

### Display name formatting

Use `formatDisplayName` to resolve localized names for regions, languages, currencies, and other codes:

```java
m.formatDisplayName("NL", Map.of("type", "region"));
// → "Netherlands"

m.formatDisplayName("USD", Map.of("type", "currency"));
// → "US Dollar"
```

Pass `Map.of("fallback", "none")` to get `null` instead of the raw code when no display name can be resolved.

### Plural rules

Use `formatPlural` to select a plural category for a number:

```java
m.formatPlural(1);
// → "one"

m.formatPlural(2, true);
// ordinal category
```

You can also pass `EvaluationOptions` when the plural category should use a different locale:

```java
m.formatPlural(
    0,
    EvaluationOptions.builder()
        .locale("ar")
        .build()
);
// → "zero"
```

## Default translations

There can be cases where you want default translations that are not present in the datafile for a given locale.

The SDK option is `defaultTranslations`, a map of locale keys to message-key dictionaries. When lookup in the active datafile is missing, the SDK falls back to these defaults before per-call `defaultTranslation` and the message-key fallback.

Use `getDefaultTranslations(locale)` to read the defaults registered for a locale.

### Instance-level translations

Set them at initialization:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .locale("nl-NL")
        .defaultTranslations(Map.of(
            "nl-NL",
            Map.of("dashboard.welcome", "Hallo {name}")
        ))
        .build()
);
```

### Per-call fallback

`defaultTranslation` is separate from `defaultTranslations` and is only available per call:

```java
m.translate(
    "dashboard.welcome",
    Map.of("name", "Ada"),
    TranslateOptions.builder()
        .defaultTranslation("Hallo {name}")
        .build()
);
```

## Default formats

If a datafile does not provide a desired format, set default formats at initialization:

```java
FormatPresets formats = new FormatPresets();
formats.setNumber(Map.of(
    "money",
    Map.of("style", "currency", "currency", "EUR")
));

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .locale("nl-NL")
        .defaultFormats(Map.of("nl-NL", formats))
        .build()
);
```

## Resolvers

Overrides can reference feature flags and experiments. The SDK does not call an external service itself. You provide resolvers that answer those conditions at runtime.

Evaluation context is the instance context merged with any per-call `context` in `TranslateOptions`.

### Feature flags

Register a flag resolver at initialization:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .resolveFlag((featureKey, context) -> true)
        .build()
);
```

Or later:

```java
m.setFlagResolver((featureKey, context) -> true);
```

Overrides use `feature` with `isEnabled` or `isDisabled`. If no resolver is set, feature conditions do not match.

### Experiment variations

Register a variation resolver the same way for A/B tests:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .resolveVariation((experimentKey, context) -> "control")
        .build()
);
```

Or later:

```java
m.setVariationResolver((experimentKey, context) -> "control");
```

Overrides use `experiment` with `hasVariation` and an expected variation string. Return `null` when the user is not in the experiment, or omit a resolver entirely. `hasVariation` will not match in either case.

Featurevisor integration is intentionally not included in this Java SDK yet. Use resolvers directly for now.

Resolver registrations made by modules are scoped to that module. Removing a module, or a failed module setup, restores the previous resolver automatically.

## Request-scoped child instances

Use `spawn` in servers, jobs, or other concurrent code where one long-lived parent owns datafiles and modules while each request needs isolated context or locale settings:

```java
MessagevisorChild requestMessagevisor = m.spawn(
    Map.of("userId", "user-123", "plan", "pro"),
    MessagevisorSpawnOptions.builder()
        .locale("nl-NL")
        .currency("EUR")
        .timeZone("Europe/Amsterdam")
        .build()
);

String title = requestMessagevisor.translate("checkout.title", Map.of());
requestMessagevisor.close();
```

Children share the parent's datafiles and current module list, so later parent updates are visible immediately. Context, locale, currency, time zone, snapshots, event versions, and lifecycle are isolated. A child's `DATAFILE_SET` listeners and generic `CHANGE` listeners observe parent datafile updates through child-owned events containing the child's active locale, context, snapshots, and monotonically increasing version. Listener unsubscription is local and idempotent. Closing the child removes its single parent bridge without affecting the parent. The `MessagevisorChild` type intentionally does not expose `setDatafile`, module management, or further spawning, leaving shared resources under one root owner.

Condition operators are type-strict. Regular expressions use the portable `i`, `m`, `s`, and `u` flags, with `u` mapped to Java's Unicode character-class mode. Repeated flags, lookarounds, named or non-capturing groups, inline flags, backreferences, and possessive quantifiers are not portable and do not match. Character classes and escaped literal backslashes remain valid and are not mistaken for unsupported syntax.

The SDK is safe to use concurrently from server request threads. Child instances are still recommended for request-specific state so one request cannot overwrite another request's locale or context.

## Diagnostics

The SDK reports structured diagnostics for missing translations, invalid datafiles, deprecated messages, unsupported formatters, module errors, and more.

Pass `onDiagnostic` at initialization to handle them yourself. Otherwise the SDK logs to `System.err` with a `[Messagevisor]` prefix.

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .logLevel(LogLevel.WARN)
        .onDiagnostic(diagnostic -> {
            System.out.println(diagnostic.code() + " " + diagnostic.message());
        })
        .build()
);
```

### Log level

`logLevel` filters which diagnostics reach `onDiagnostic` or the fallback logger. Levels are ordered from most to least severe:

- `FATAL`
- `ERROR`
- `WARN`
- `INFO`
- `DEBUG`

With `LogLevel.WARN`, you receive `FATAL`, `ERROR`, and `WARN` diagnostics but not `INFO` or `DEBUG`.

Use `LogLevel.FATAL` in tests when you want a quiet instance.

You can change the threshold later without recreating the instance:

```java
m.setLogLevel(LogLevel.DEBUG);
```

Modules can subscribe to diagnostics separately with their own threshold via the modules API.

### Diagnostic events

Each diagnostic is a `MessagevisorDiagnostic` object.

Important fields include:

| Field                      | Description                                                                            |
| -------------------------- | -------------------------------------------------------------------------------------- |
| `level()`                  | `FATAL`, `ERROR`, `WARN`, `INFO`, or `DEBUG`                                           |
| `code()`                   | Stable code such as `missing_translation`, `deprecated_message`, or `invalid_datafile` |
| `message()`                | Human-readable description                                                             |
| `details()`                | Always-present map with diagnostic-specific context                                    |
| `module()`, `moduleName()` | Present when reported by or about a module                                             |
| `originalError()`          | Underlying error for parse or format failures                                          |

Translation diagnostics store `locale`, `messageKey`, `overrideKey`, `deprecationWarning`, and `source` in `details()`.

### Diagnostic codes

Common built-in codes include:

- `sdk_initialized`: instance constructed
- `missing_translation`: key missing from datafile and `defaultTranslations`
- `missing_datafile` / `missing_locale`: locale or datafile not available
- `missing_format`: a requested named format preset is absent
- `invalid_format`: formatter options are invalid for the runtime
- `invalid_datafile`: JSON parse or shape failure
- `invalid_message`: module formatting threw
- `deprecated_message`: message has deprecated metadata
- `message_override_matched`: debug-level override match
- `unsupported_formatter`: runtime-native formatter limitation or unsupported option
- `duplicate_module`: two modules share the same name
- `module_setup_error`: module setup failed and registration was rolled back
- `module_close_error`: a module threw while the SDK was closing

Diagnostics with `level() == LogLevel.ERROR` also emit an `ERROR` event.

## Events

Subscribe to SDK state changes with `on` or the convenience `subscribe` helper, which is equivalent to listening for `EventName.CHANGE`.

```java
MessagevisorUnsubscribe unsubscribe = m.subscribe(() -> {
    MessagevisorSnapshot snapshot = m.getSnapshot();
    System.out.println(snapshot.locale());
});

m.setLocale("nl-NL");
unsubscribe.unsubscribe();
```

### Event types

| Event                     | When it fires                          |
| ------------------------- | -------------------------------------- |
| `EventName.DATAFILE_SET`  | `setDatafile` updates a locale         |
| `EventName.LOCALE_SET`    | `setLocale` changes the active locale  |
| `EventName.CONTEXT_SET`   | `setContext` updates instance context  |
| `EventName.CURRENCY_SET`  | `setCurrency` runs                     |
| `EventName.TIME_ZONE_SET` | `setTimeZone` runs                     |
| `EventName.CHANGE`        | After any of the above                 |
| `EventName.ERROR`         | An error-level diagnostic was reported |

Each callback receives a `MessagevisorEvent` with:

- `type()`: the subscribed event type
- `source()`: the originating state event for generic `CHANGE` events
- `snapshot()` and `previousSnapshot()`
- state-specific details such as `locale()`, `activeLocale()`, and `replaced()`

For `DATAFILE_SET`, `locale()` is the stored locale that changed, while `activeLocale()` remains the locale currently selected for evaluation. `replaced()` distinguishes explicit replacement from the default same-locale merge. Context events use `replaced()` in the same way.

## Modules

Modules allow you to transform translations at evaluation time.

### Registering modules in SDK

They can be registered at initialization time:

```java
Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .addModule(IcuModule.create())
        .build()
);
```

You can also register them afterwards with `addModule`. It returns an idempotent removal function:

```java
MessagevisorUnsubscribe removeIcu = m.addModule(IcuModule.create());
removeIcu.unsubscribe();
```

And remove them by name with `removeModule`:

```java
m.removeModule("icu");
```

Resolver changes installed during module setup belong to that module. Setup failure or removal restores the previous resolver, and spawned instances observe later parent resolver changes dynamically.

### Registering at project level

It is recommended that the same modules you use with the SDK in your application are also registered in your Messagevisor project configuration.

This helps the CLI behave the same way and avoids unexpected differences in behavior.

### Creating a custom module

Here's a brief example of a custom module:

```java
import com.messagevisor.sdk.MessagevisorFormatPayload;
import com.messagevisor.sdk.MessagevisorModule;
import com.messagevisor.sdk.MessagevisorModuleApi;

public final class UppercaseModule implements MessagevisorModule {
  @Override
  public String name() {
    return "uppercase";
  }

  @Override
  public Object format(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
    Object translation = payload.translation();
    return translation instanceof String text ? text.toUpperCase() : translation;
  }
}
```

Register it:

```java
m.addModule(new UppercaseModule());
```

## Missing translations module

The missing translations module listens for missing translation diagnostics:

```java
import com.messagevisor.modules.missingtranslations.MissingTranslationsModule;
import com.messagevisor.modules.missingtranslations.MissingTranslationsModuleOptions;

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .addModule(MissingTranslationsModule.create(
            MissingTranslationsModuleOptions.builder()
                .handler(payload -> {
                    System.out.println(
                        "Missing translation: "
                            + payload.messageKey()
                            + " locale="
                            + payload.locale()
                            + " revision="
                            + payload.revision()
                    );
                })
                .dedupe(true)
                .build()
        ))
        .build()
);
```

## Closing the SDK

Call `close()` when tearing down the instance, for example in tests, SSR, or request-scoped runtimes. It runs each module's optional `close` hook and clears subscriptions and listeners. Using an instance after closing it is outside the supported contract. If a module throws during cleanup, the SDK emits a `module_close_error` diagnostic, still closes the remaining modules, then throws an aggregate close exception.

```java
m.close();
```

`closeAsync()` is also available when a `CompletableFuture` is more convenient:

```java
m.closeAsync().join();
```

## Translation lookup

`translate` and `getRawTranslation` resolve a source string first, then run registered modules on that string. Understanding lookup helps when debugging overrides, fallbacks, and missing-translation diagnostics.

### Resolving the source string

For a message key, the SDK uses the per-call `locale` when provided, otherwise the active locale. It then builds an evaluation context by merging instance context with any per-call `context` from `TranslateOptions`.

Lookup proceeds in this order:

1. **Overrides**: for the message, each override is tried in datafile order. The first override whose conditions and segments match wins.
    - **Conditions**: direct rules on context attributes, plus optional `feature` / `experiment` rules when resolvers are configured.
    - **Segments**: reusable segment references from the datafile. Omitted or `"*"` segment groups always match.
    - On match, the SDK returns that override's `translation` and emits a `message_override_matched` diagnostic at debug level.
2. **Base translation**: if no override matches, the SDK uses `translations[messageKey]` from the datafile.
3. **`defaultTranslations`**: if the result is still missing, the SDK checks `defaultTranslations` for the evaluation locale.
4. **Missing diagnostic**: if the key is still unresolved after steps 1 to 3, the SDK emits `missing_translation` when the locale datafile exists, or `missing_datafile` when it does not. This happens even when per-call `defaultTranslation` in the next step supplies the final string.
5. **Per-call `defaultTranslation`**: if provided, the `defaultTranslation` option is used.
6. **Message key**: if nothing else matched, the message key string is returned.

Empty strings are explicit translations. If an override, base translation, `defaultTranslations` entry, or per-call `defaultTranslation` resolves to `""`, the SDK returns `""` and does not continue fallback lookup.

Condition operators are type-strict. Numbers are never coerced from strings, string operators require strings, and negative array operators still require arrays. Regular expressions use the portable `i`, `m`, `s`, and `u` flags. Repeated flags, lookarounds, named or non-capturing groups, inline flags, backreferences, and possessive quantifiers are not portable and do not match. Invalid or non-portable expressions return false for both `matches` and `notMatches`. `before` and `after` accept complete ISO 8601 date-times with an explicit `Z` or numeric offset, not date-only strings.

When a resolved message has deprecated metadata in the datafile, the SDK emits a `deprecated_message` warning before formatting.

```java
m.setContext(Map.of("plan", "pro"));
m.translate("pricing.cta");

m.getRawTranslation(
    "missing.key",
    TranslateOptions.builder()
        .defaultTranslation("Fallback copy")
        .build()
);
```

### Formatting the result

After the source string is resolved, `translate` runs modules in registration order:

1. **`format`** hooks: interpolation, ICU, and other formatters using `values`, locale formats, and optional `moduleOptions`.
2. **`transform`** hooks: post-processing on the formatted output.

`getRawTranslation` stops after source resolution and does not run modules. Use it when you need the matched override or base string before formatting.

## CLI and project conformance

The Java CLI reuses the JavaScript Messagevisor CLI for source-project truth. It shells out to `npx messagevisor` for built datafiles, tests, segments, and examples, then evaluates them with the Java SDK.

The SDK test suite also executes the canonical language-neutral `conformance/sdk-v1.json` contract. That contract covers conditions and segments, combined override matching, empty and fallback translations, deprecation diagnostics, locale-keyed datafile merge/replacement, module lifecycle, diagnostic envelopes, and event ordering with change sources. The bundled fixture is checked against the monorepo copy when both repositories are available.

```sh
./gradlew :cli:run --args='test --projectDirectoryPath=../projects/project-1 --withIcuModule --withInterpolationModule'

./gradlew :cli:run --args='evaluate --projectDirectoryPath=../projects/project-1 --locale=en-US --message=dashboard.welcome --withIcuModule'

./gradlew :cli:run --args='benchmark --projectDirectoryPath=../projects/project-1 --locale=en-US --message=dashboard.welcome --withIcuModule -n=1000'

./gradlew :cli:run --args='examples --projectDirectoryPath=../projects/project-1 --withIcuModule'
```

Convenience targets are available:

```sh
make test-project-1
make examples-project-1
```

<!-- MESSAGEVISOR_DOCS_END -->

## Build and test

Requirements:

- Java 17+
- The included Gradle wrapper
- Node.js 24.16.0 from `.nvmrc` and `npx messagevisor` when using the CLI against source Messagevisor projects

Formatter caching is an internal runtime concern, not application API. Java may optimize formatter reuse independently while preserving the same observable translation and lifecycle contract. Spawned children share parent runtime state where required, but callers do not provide or inspect formatter caches.

Run:

```sh
./gradlew test
./gradlew build
```

Verify the main JARs, source and Javadoc JARs, generated POMs, dependency boundaries, and licence metadata:

```sh
make verify-artifacts
```

### Releasing

1. Update `gradle.properties`, the installation versions in this README, and `CHANGELOG.md` to the same release version.
2. Merge the release commit into `main`.
3. Create and publish a GitHub release using a semantic version tag with a `v` prefix, such as `v0.2.0`.
4. GitHub Actions validates the tag, builds and tests every artifact, checks the publication boundaries, and publishes the signed artifacts to the configured Maven repository.
5. Verify the published POMs, JARs, source JARs, Javadoc JARs, signatures, and checksums from a clean consumer project.

## Repository layout

| Directory                     | Java package                                   | Artifact                                                    | Purpose                                                                                                                           |
| ----------------------------- | ---------------------------------------------- | ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `sdk`                         | `com.messagevisor.sdk`                         | `com.messagevisor:messagevisor-sdk`                         | Core runtime SDK for loading datafiles, translating messages, evaluating conditions, formatting values, diagnostics, and modules. |
| `module-interpolation`        | `com.messagevisor.modules.interpolation`       | `com.messagevisor:messagevisor-module-interpolation`        | Simple `{name}` placeholder interpolation module.                                                                                 |
| `module-icu`                  | `com.messagevisor.modules.icu`                 | `com.messagevisor:messagevisor-module-icu`                  | ICU MessageFormat module backed by ICU4J classic.                                                                                 |
| `module-missing-translations` | `com.messagevisor.modules.missingtranslations` | `com.messagevisor:messagevisor-module-missing-translations` | Missing translation diagnostic observer module.                                                                                   |
| `cli`                         | `com.messagevisor.cli`                         | Internal application                                        | Java conformance and utility CLI for tests, evaluation, benchmarks, and examples.                                                 |

## Formatting notes

The Java SDK uses Java and ICU4J formatting as the real Java application behavior. Java and JavaScript may not always share identical CLDR data, option support, or rendered strings.

Where Messagevisor project fixtures need cross-runtime expectations, prefer `expectedByRuntime.java` for legitimate Java-vs-JavaScript differences rather than hardcoding one-off output hacks into the SDK.

Do not add Java branches for specific locales, named time zones, unit labels, or compact suffixes. Use ICU4J locale APIs such as number, measure, date/time, relative, list, and display-name formatting, then document unavoidable differences in fixtures.

The ICU module uses ICU4J classic `MessageFormat`. ICU MessageFormat 2 is intentionally not used for this first Java port because it is still treated as a newer technical-preview style API in the ICU ecosystem.

## Related

- [Messagevisor JavaScript SDK](https://messagevisor.com/docs/sdks/javascript/)
- [Formats](https://messagevisor.com/docs/formats/)
- [Evaluation](https://messagevisor.com/docs/evaluation/)
- [Modules](https://messagevisor.com/docs/modules/)
- [Custom modules](https://messagevisor.com/docs/modules/custom/)
- [Building datafiles](https://messagevisor.com/docs/building-datafiles/)

## License

MIT © [Fahad Heylaal](https://fahad19.com)
