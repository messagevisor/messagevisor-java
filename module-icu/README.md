# Messagevisor ICU Module For Java

ICU MessageFormat module for the Messagevisor Java SDK.

- Java package: `com.messagevisor.modules.icu`
- Artifact: `com.messagevisor:messagevisor-module-icu`
- Default module name: `icu`

This module formats authored translations with ICU4J classic `MessageFormat`. It is the closest Java equivalent of the JavaScript `@messagevisor/module-icu` package.

## Installation

Published releases use the `com.messagevisor` group:

```kotlin
dependencies {
    implementation("com.messagevisor:messagevisor-sdk:0.3.0")
    implementation("com.messagevisor:messagevisor-module-icu:0.3.0")
}
```

For local development inside this repository, use the `:sdk` and `:module-icu` Gradle projects.

## Usage

```java
import com.messagevisor.modules.icu.IcuModule;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;

import java.util.Map;

Messagevisor m = Messagevisor.create(
    MessagevisorOptions.builder()
        .datafile(datafileJson)
        .addModule(IcuModule.create())
        .build()
);

String text = m.translate(
    "cart.itemCount",
    Map.of("count", 3)
);
```

For a raw ICU message:

```java
String text = m.formatMessage(
    "{count, plural, one {# item} other {# items}}",
    Map.of("count", 3)
);
```

## Supported Message Syntax

The module relies on ICU4J classic `MessageFormat` and supports the usual ICU message features:

- variables
- quoting and escaped text
- number, date, and time arguments
- plural and exact plural matches
- `#` replacement in plural branches
- plural offsets
- select and selectordinal
- nested messages

Formatting failures are reported through the SDK diagnostic flow as invalid message diagnostics, then translation fallback behavior continues according to the SDK contract.

## Messagevisor Format Presets

The module integrates with Messagevisor format presets resolved from datafiles, defaults, and per-call options.

```java
String text = m.formatMessage(
    "Total: {amount, number, money}",
    Map.of("amount", 12)
);
```

Per-call currency and time zone options flow through the same SDK evaluation options used by direct format helpers.

Named Messagevisor `number`, `date`, and `time` presets are resolved before ICU MessageFormat evaluates the message, including presets nested in plural and select branches. Genuinely unsupported options report a non-fatal `unsupported_formatter` diagnostic.

## Java And JavaScript Differences

Java and JavaScript can differ in CLDR data, ICU behavior, and exact rendered formatting. The Java module treats ICU4J output as the real Java runtime behavior. For cross-runtime fixtures, use Messagevisor's `expectedByRuntime.java` convention for legitimate formatting differences.

This module intentionally uses ICU4J classic `MessageFormat`, not ICU MessageFormat 2.

## Testing

From `messagevisor-java/`:

```sh
./gradlew :module-icu:test
```
