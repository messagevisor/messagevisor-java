# Messagevisor Interpolation Module For Java

Simple `{name}` interpolation module for the Messagevisor Java SDK.

- Java package: `com.messagevisor.modules.interpolation`
- Artifact: `com.messagevisor:messagevisor-module-interpolation`
- Default module name: `interpolation`

Use this module when your translations only need straightforward placeholder replacement and do not need ICU plurals, selects, date formatting, or number formatting inside message strings.

## Installation

Published releases use the `com.messagevisor` group:

```kotlin
dependencies {
    implementation("com.messagevisor:messagevisor-sdk:0.2.0")
    implementation("com.messagevisor:messagevisor-module-interpolation:0.2.0")
}
```

For local development inside this repository, use the `:sdk` and `:module-interpolation` Gradle projects.

## Usage

```java
import com.messagevisor.modules.interpolation.InterpolationModule;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;

import java.util.Map;

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .addModule(InterpolationModule.create())
        .build()
);

String text = m.formatMessage(
    "Hello, {name}. You have {count} messages.",
    Map.of("name", "Ada", "count", 3)
);
```

## Behavior

- Replaces primitive placeholder values: strings, numbers, and booleans.
- Leaves missing values unchanged.
- Leaves complex values unchanged.
- Runs as a Messagevisor formatting module, so it can be combined with SDK translation lookup and output transforms.

For ICU plurals, selects, nested formatting, and locale-aware number/date formatting inside messages, use [`module-icu`](../module-icu/README.md).

## Custom Pattern

The module exposes constructors for custom names and placeholder patterns when an application needs a different placeholder convention. Prefer the default `InterpolationModule.create()` unless you have a clear compatibility need.

## Testing

From `messagevisor-java/`:

```sh
./gradlew :module-interpolation:test
```
