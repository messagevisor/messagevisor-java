package com.messagevisor.modules.missingtranslations;

import com.messagevisor.sdk.LogLevel;
import com.messagevisor.sdk.MessagevisorDiagnostic;
import com.messagevisor.sdk.MessagevisorModule;
import com.messagevisor.sdk.MessagevisorModuleApi;
import com.messagevisor.sdk.MessagevisorModuleDiagnosticOptions;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class MissingTranslationsModule implements MessagevisorModule {
  private final String name;
  private final MissingTranslationHandler handler;
  private final boolean dedupe;
  private final Set<String> seen = new LinkedHashSet<>();

  private MissingTranslationsModule(MissingTranslationsModuleOptions options) {
    MissingTranslationsModuleOptions resolvedOptions =
        options == null ? MissingTranslationsModuleOptions.builder().build() : options;
    this.name =
        resolvedOptions.name() == null || resolvedOptions.name().isBlank()
            ? "missing-translations"
            : resolvedOptions.name();
    this.handler = resolvedOptions.handler();
    this.dedupe = resolvedOptions.dedupe();
  }

  public static MissingTranslationsModule create(MissingTranslationsModuleOptions options) {
    return new MissingTranslationsModule(options);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void setup(MessagevisorModuleApi api) {
    if (handler == null) {
      throw new IllegalArgumentException("Missing translations module requires a handler.");
    }

    api.onDiagnostic(
        diagnostic -> handleDiagnostic(api, diagnostic),
        new MessagevisorModuleDiagnosticOptions(LogLevel.ERROR));
  }

  private void handleDiagnostic(MessagevisorModuleApi api, MessagevisorDiagnostic diagnostic) {
    Object messageKey = diagnostic.details().get("messageKey");
    if (!Objects.equals(diagnostic.code(), "missing_translation") || !(messageKey instanceof String)) {
      return;
    }

    Object localeValue = diagnostic.details().get("locale");
    String locale = localeValue instanceof String ? (String) localeValue : null;
    Object sourceValue = diagnostic.details().get("source");
    String source = sourceValue instanceof String ? (String) sourceValue : null;
    String revision = null;

    if (locale != null) {
      try {
        revision = api.getRevision(locale);
      } catch (RuntimeException ignored) {
        revision = null;
      }
    }

    MissingTranslationPayload payload =
        new MissingTranslationPayload(
            (String) messageKey, locale, revision, source, diagnostic);

    if (dedupe) {
      String key = dedupeKey(payload);
      if (seen.contains(key)) {
        return;
      }
      seen.add(key);
    }

    handler.handle(payload);
  }

  private static String dedupeKey(MissingTranslationPayload payload) {
    return String.join(
        "\u001f",
        nullToEmpty(payload.messageKey()),
        nullToEmpty(payload.locale()),
        nullToEmpty(payload.revision()),
        nullToEmpty(payload.source()));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
