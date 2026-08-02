# Messagevisor Java

This repository contains the Java 17 Messagevisor SDK and conformance tooling.

Treat the current `messagevisor` TypeScript monorepo as the behavioral authority. Keep the Java implementation generic and portable. Never add locale-specific output rewrites to satisfy a fixture. Use ICU4J APIs, target format overrides that preserve meaning, or `expectedByRuntime.java` for legitimate platform differences.

Run `./gradlew test`, `./gradlew build`, `make test-project-1`, and `make examples-project-1` after behavior changes.
