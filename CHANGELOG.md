# Changelog

## 0.3.0

- Align datafile validation, format merging and input ownership with the JavaScript SDK.
- Evaluate ICU branches lazily, preserve quoted literals and validate missing arguments only in selected branches.
- Correct plural precision and effective currency and time zone handling.
- Strengthen module removal, failed setup cleanup and shared concurrent shutdown.
- Preserve child event snapshots, subscriptions and resolver ownership.
- Expand executable conformance fixtures and run strict project examples in CI.
- Document corrected behaviour and native formatting boundaries throughout the SDK README.

### Upgrade notes

- Invalid datafiles are rejected without changing stored state and report `invalid_datafile`.
- Corrected formatting and argument validation can change output or errors for previously accepted inputs.
- Integration CI requires the updated project-1 template from the published Messagevisor CLI.

## 0.2.0

- Revived the Java SDK against the current Messagevisor datafile and runtime contracts.
- Added generic ICU4J number, date, time, range, relative, plural, list, and display-name formatting.
- Added ICU, interpolation, and missing-translation modules.
- Added a conformance CLI that executes message, segment, locale, and target tests.
