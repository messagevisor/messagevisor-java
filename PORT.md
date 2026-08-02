# Messagevisor Java Port Notes

This port starts from the current JavaScript SDK in `packages/sdk`, not from the older Go port alone.

## Decisions

- Java baseline: 17
- Build: Gradle multi-module
- Package root: `com.messagevisor`
- Core package: `com.messagevisor.sdk`
- Modules: `com.messagevisor.modules.interpolation`, `com.messagevisor.modules.icu`, and `com.messagevisor.modules.missingtranslations`
- CLI package: `com.messagevisor.cli`
- JSON: Jackson
- CLI parsing: picocli
- Locale/message formatting: Java plus ICU4J classic

## Parity Targets

The Java SDK should match portable runtime behavior:

- datafile parsing and same-locale merge
- active locale and deferred datafile loading
- shallow context merge with replace option
- translation fallback order
- empty-string translations as explicit values
- override, segment, condition, feature, and experiment evaluation
- diagnostics, events, snapshots, modules, and lifecycle cleanup
- direct formatter helpers for numbers, currency, dates, times, ranges, relative time, and plural categories
- missing translation observability through the missing translations module

## Diagnostics Contract

`MessagevisorDiagnostic` carries the portable envelope: `level`, `code`, `message`, and an always-present `details()` map. Java-specific module provenance remains available through `module()` / `moduleName()`, and failures through `originalError()`.

Put translation context such as `locale`, `messageKey`, `overrideKey`, `deprecationWarning`, and `source` only in `details()`. Do not add top-level accessors for them.

Modules report `MessagevisorModuleReportedDiagnostic`, which deliberately exposes only module-owned `level`, `code`, `message`, optional `details()`, and optional `originalError()`. The SDK adds module provenance.

Use `setLogLevel(LogLevel)` to change the diagnostic threshold at runtime. All state setters continue emitting their existing lightweight events. When module cleanup fails, emit `module_close_error`, finish closing remaining modules, and throw `MessagevisorCloseException` with the aggregated failures.

## Differences From The Go Port

The Go port is useful for CLI flow and conformance-runner shape, but some details are stale:

- use `defaultTranslations`, not `defaultMessages`
- do not implement `fallbackOnEmptyString`
- missing variations are `null`
- `setContext` and `setDatafile` merge by default
- Java should lean on ICU4J instead of copying Go formatter limitations

## Formatting Policy

The goal is native Java behavior with transparent diagnostics, not fake byte-for-byte JavaScript output.

When Java/ICU4J differs from JavaScript for valid locale reasons, add `expectedByRuntime.java` to shared fixtures. Do not add locale-specific hacks in SDK code just to satisfy current examples.

## CLI Policy

The Java CLI must not reimplement source project parsing. It should continue shelling out to:

```sh
npx messagevisor list --tests --applyMatrix --json
npx messagevisor list --segments --json
npx messagevisor build --json --target=<target> --locale=<locale>
npx messagevisor examples --json
```

This keeps the Java port focused on runtime behavior.
