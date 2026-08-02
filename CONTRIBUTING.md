# Contributing

Thanks for helping improve Messagevisor Java. Please open an issue before a large API or portability change so its relationship with the JavaScript source of truth can be agreed first. By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md). Report vulnerabilities through the process in [SECURITY.md](SECURITY.md), not a public issue.

Use Java 17 compatible APIs and keep formatter behavior generic. Locale names, translated labels, named-zone branches, fixture-specific output rewrites, and silent test skipping do not belong in the SDK.

Before opening a change, run:

```sh
./gradlew test
./gradlew build
make test-project-1
make examples-project-1
```

Use `expectedByRuntime.java` for legitimate ICU or CLDR differences. A Java target format override is appropriate only when it preserves authored semantics and expresses an explicit platform formatter choice.
