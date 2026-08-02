package com.messagevisor.modules.missingtranslations;

import static org.assertj.core.api.Assertions.assertThat;

import com.messagevisor.sdk.DatafileContent;
import com.messagevisor.sdk.DatafileMessage;
import com.messagevisor.sdk.LogLevel;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorModuleReportedDiagnostic;
import com.messagevisor.sdk.MessagevisorOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MissingTranslationsModuleTest {
  @Test
  void requiresHandlerDuringSetup() {
    DatafileContent datafile = datafile("en-US", "rev-1");

    List<String> diagnostics = new ArrayList<>();
    Messagevisor.create(
        MessagevisorOptions.builder()
            .datafile(datafile)
            .addModule(
                MissingTranslationsModule.create(
                    MissingTranslationsModuleOptions.builder().build()))
            .onDiagnostic(diagnostic -> diagnostics.add(diagnostic.code()))
            .logLevel(LogLevel.ERROR)
            .build());
    assertThat(diagnostics).containsExactly("module_setup_error");
  }

  @Test
  void usesDefaultAndCustomNames() {
    MissingTranslationsModule defaultModule =
        MissingTranslationsModule.create(
            MissingTranslationsModuleOptions.builder().handler(payload -> {}).build());
    MissingTranslationsModule customModule =
        MissingTranslationsModule.create(
            MissingTranslationsModuleOptions.builder()
                .name("missing-observer")
                .handler(payload -> {})
                .build());

    assertThat(defaultModule.name()).isEqualTo("missing-translations");
    assertThat(customModule.name()).isEqualTo("missing-observer");
  }

  @Test
  void callsHandlerForMissingMessagesWithRevisionAndDiagnostic() {
    List<MissingTranslationPayload> payloads = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile("en-US", "rev-1"))
                .addModule(module(payloads, false))
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.getRawTranslation("missing.title")).isEqualTo("missing.title");

    assertThat(payloads).hasSize(1);
    MissingTranslationPayload payload = payloads.get(0);
    assertThat(payload.messageKey()).isEqualTo("missing.title");
    assertThat(payload.locale()).isEqualTo("en-US");
    assertThat(payload.revision()).isEqualTo("rev-1");
    assertThat(payload.source()).isEqualTo("translation");
    assertThat(payload.diagnostic().code()).isEqualTo("missing_translation");
  }

  @Test
  void ignoresNonMissingDiagnostics() {
    List<MissingTranslationPayload> payloads = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile("en-US", "rev-1"))
                .addModule(module(payloads, false))
                .logLevel(LogLevel.FATAL)
                .build());

    m.addModule(
        new com.messagevisor.sdk.MessagevisorModule() {
          @Override
          public String name() {
            return "diagnostic-source";
          }

          @Override
          public void setup(com.messagevisor.sdk.MessagevisorModuleApi api) {
            api.reportDiagnostic(
                MessagevisorModuleReportedDiagnostic.builder(
                        LogLevel.ERROR, "custom_error", "Custom error")
                    .detail("messageKey", "ignored")
                    .build());
          }
        });

    assertThat(payloads).isEmpty();
  }

  @Test
  void callsHandlerEveryTimeByDefault() {
    List<MissingTranslationPayload> payloads = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile("en-US", "rev-1"))
                .addModule(module(payloads, false))
                .logLevel(LogLevel.FATAL)
                .build());

    m.getRawTranslation("missing.title");
    m.getRawTranslation("missing.title");

    assertThat(payloads).hasSize(2);
  }

  @Test
  void dedupesWhenEnabled() {
    List<MissingTranslationPayload> payloads = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile("en-US", "rev-1"))
                .addModule(module(payloads, true))
                .logLevel(LogLevel.FATAL)
                .build());

    m.getRawTranslation("missing.title");
    m.getRawTranslation("missing.title");
    m.getRawTranslation("missing.other");

    assertThat(payloads).extracting(MissingTranslationPayload::messageKey)
        .containsExactly("missing.title", "missing.other");
  }

  @Test
  void omitsRevisionWhenLookupFails() {
    List<MissingTranslationPayload> payloads = new ArrayList<>();
    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile("en-US", "rev-1"))
                .addModule(module(payloads, false))
                .logLevel(LogLevel.FATAL)
                .build());

    m.addModule(
        new com.messagevisor.sdk.MessagevisorModule() {
          @Override
          public String name() {
            return "missing-reporter";
          }

          @Override
          public void setup(com.messagevisor.sdk.MessagevisorModuleApi api) {
            api.reportDiagnostic(
                MessagevisorModuleReportedDiagnostic.builder(
                        LogLevel.ERROR, "missing_translation", "Missing")
                    .detail("locale", "fr-FR")
                    .detail("messageKey", "missing.remote")
                    .detail("source", "translation")
                    .build());
          }
        });

    assertThat(payloads).hasSize(1);
    assertThat(payloads.get(0).locale()).isEqualTo("fr-FR");
    assertThat(payloads.get(0).revision()).isNull();
  }

  private static MissingTranslationsModule module(
      List<MissingTranslationPayload> payloads, boolean dedupe) {
    return MissingTranslationsModule.create(
        MissingTranslationsModuleOptions.builder()
            .handler(payloads::add)
            .dedupe(dedupe)
            .build());
  }

  private static DatafileContent datafile(String locale, String revision) {
    DatafileContent datafile = new DatafileContent();
    datafile.setSchemaVersion("1");
    datafile.setMessagevisorVersion("0.0.1");
    datafile.setRevision(revision);
    datafile.setTarget("web");
    datafile.setLocale(locale);
    datafile.setMessages(Map.of("known", new DatafileMessage()));
    datafile.setTranslations(Map.of("known", "Known"));
    return datafile;
  }
}
