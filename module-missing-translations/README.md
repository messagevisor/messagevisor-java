# Messagevisor Missing Translations Module For Java

Missing translation observer module for the Messagevisor Java SDK.

- Java package: `com.messagevisor.modules.missingtranslations`
- Artifact: `com.messagevisor:messagevisor-module-missing-translations`
- Default module name: `missing-translations`

This module subscribes to SDK `missing_translation` diagnostics and forwards developer-friendly payloads to a handler. It does not change translation fallback behavior.

## Installation

Published releases use the `com.messagevisor` group:

```kotlin
dependencies {
    implementation("com.messagevisor:messagevisor-sdk:0.3.0")
    implementation("com.messagevisor:messagevisor-module-missing-translations:0.3.0")
}
```

For local development inside this repository, use the `:sdk` and `:module-missing-translations` Gradle projects.

## Usage

```java
import com.messagevisor.modules.missingtranslations.MissingTranslationsModule;
import com.messagevisor.modules.missingtranslations.MissingTranslationsModuleOptions;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .addModule(MissingTranslationsModule.create(
            MissingTranslationsModuleOptions.builder()
                .handler(payload -> {
                    System.out.println("Missing translation: " + payload.messageKey());
                    System.out.println("Locale: " + payload.locale());
                    System.out.println("Revision: " + payload.revision());
                })
                .build()
        ))
        .build()
);
```

## Options

| Option | Default | Purpose |
| --- | --- | --- |
| `handler` | required | Called whenever the module handles a missing translation diagnostic. |
| `name` | `missing-translations` | Module name used by the SDK module registry. |
| `dedupe` | `false` | When `true`, suppresses repeated payloads for the same message key, locale, revision, and source. |

The handler is required. Module setup throws `Missing translations module requires a handler.` when it is not provided.

## Payload

The handler receives a `MissingTranslationPayload`:

```java
payload.messageKey();
payload.locale();
payload.revision();
payload.source();
payload.diagnostic();
```

Fields can be `null` when the SDK diagnostic does not carry that value. `revision` is resolved through the SDK module API using the active locale. If revision lookup fails, translation flow continues and `revision` is `null`.

## Dedupe

By default, the handler is called every time a missing translation diagnostic is emitted:

```java
MissingTranslationsModuleOptions.builder()
    .handler(payload -> report(payload))
    .build();
```

Enable dedupe when you only want the first report per `messageKey + locale + revision + source`:

```java
MissingTranslationsModuleOptions.builder()
    .handler(payload -> report(payload))
    .dedupe(true)
    .build();
```

## Testing

From `messagevisor-java/`:

```sh
./gradlew :module-missing-translations:test
```
