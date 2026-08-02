# Messagevisor Java CLI

Conformance and utility CLI for the Messagevisor Java SDK.

- Java package: `com.messagevisor.cli`
- Main class: `com.messagevisor.cli.MessagevisorJavaCli`
- Distribution: internal Gradle application (not published as a Maven library)

The CLI is designed for SDK parity work. It shells out to `npx messagevisor` for source-project truth, then evaluates the resulting datafiles, tests, segments, and examples with the Java SDK.

For `examples`, the CLI asks the JavaScript CLI for JSON evaluation inputs and replays those inputs in Java. This keeps message-specific datafiles, raw-locale default formats, per-example formats, context, values, currency, and time zone aligned with `npx messagevisor examples`.

## Run

From `messagevisor-java/`:

```sh
./gradlew :cli:run --args='test --projectDirectoryPath=../projects/project-1 --withIcuModule --withInterpolationModule'
```

The source project must have access to the JavaScript Messagevisor CLI through `npx messagevisor`.

## Shared Options

| Option | Purpose |
| --- | --- |
| `--projectDirectoryPath=<path>` | Messagevisor project directory to inspect. |
| `--withIcuModule` | Register the Java ICU module. |
| `--withInterpolationModule` | Register the Java interpolation module. |

## `test`

Runs Messagevisor project assertions through the Java SDK.

```sh
./gradlew :cli:run --args='test --projectDirectoryPath=../projects/project-1 --withIcuModule --withInterpolationModule'
```

Useful options:

| Option | Purpose |
| --- | --- |
| `--keyPattern=<regex>` | Limit tests by message or segment key. |
| `--assertionPattern=<regex>` | Limit assertions by assertion text/key. |
| `--onlyFailures` | Print only failing assertions. |
| `--quiet` | Reduce output. |
| `--target=<target>` | Fallback target for locale tests and assertions without a target. Defaults to `java`. |

## `evaluate`

Evaluates a message, raw message string, or segment.

```sh
./gradlew :cli:run --args='evaluate --projectDirectoryPath=../projects/project-1 --locale=en-US --message=dashboard.welcome --withIcuModule'

./gradlew :cli:run --args='evaluate --projectDirectoryPath=../projects/project-1 --locale=en-US --rawMessage="Hello, {name}" --values={"name":"Ada"} --withInterpolationModule'

./gradlew :cli:run --args='evaluate --projectDirectoryPath=../projects/project-1 --locale=en-US --segment=paidUsers --context={"plan":"pro"}'
```

Useful options:

| Option | Purpose |
| --- | --- |
| `--target=<target>` | Build target to use. Defaults to `web`. |
| `--locale=<locale>` | Locale to evaluate. |
| `--message=<key>` | Message key to translate. |
| `--rawMessage=<message>` | Raw message string to format. |
| `--segment=<key>` | Segment key to evaluate. |
| `--context=<json>` | Evaluation context JSON. |
| `--values=<json>` | Translation values JSON. |
| `--json` | Print JSON output. |
| `--pretty` | Pretty-print JSON output. |

## `benchmark`

Runs repeated Java SDK evaluations and reports timing information.

```sh
./gradlew :cli:run --args='benchmark --projectDirectoryPath=../projects/project-1 --locale=en-US --message=dashboard.welcome --withIcuModule -n=1000'
```

`benchmark` accepts the same evaluation inputs as `evaluate`, plus:

| Option | Purpose |
| --- | --- |
| `-n=<count>` | Number of iterations. Defaults to `1000`. |

## `examples`

Evaluates Messagevisor project examples through the Java SDK.

```sh
./gradlew :cli:run --args='examples --projectDirectoryPath=../projects/project-1 --withIcuModule'

./gradlew :cli:run --args='examples --projectDirectoryPath=../projects/project-1 --withIcuModule --json --pretty'
```

By default, this follows the same broad behavior as `npx messagevisor examples` and includes all example matrix rows. Add `--locale=en-US` only when intentionally checking one locale.

Useful options:

| Option | Purpose |
| --- | --- |
| `--locale=<locale>` | Limit examples to a locale. |
| `--json` | Print JSON output. |
| `--pretty` | Pretty-print JSON output. |

## Project-1 Smoke Checks

From `messagevisor-java/`:

```sh
make test-project-1
make examples-project-1
```

`make examples-project-1` should report the same example count as the source JavaScript CLI for the same project.
