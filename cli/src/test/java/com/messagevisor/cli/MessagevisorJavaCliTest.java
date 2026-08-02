package com.messagevisor.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.messagevisor.modules.icu.IcuModule;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class MessagevisorJavaCliTest {
  @Test
  void exposesExpectedSubcommands() {
    CommandLine commandLine = new CommandLine(new MessagevisorJavaCli());

    assertThat(commandLine.getSubcommands().keySet())
        .contains("test", "evaluate", "benchmark", "examples");
  }

  @Test
  void derivesCliVersionFromTheBuildVersion() {
    String previous = System.getProperty("messagevisor.version");
    try {
      System.setProperty("messagevisor.version", "1.2.3-test");
      assertThat(new MessagevisorJavaCli.VersionProvider().getVersion()).containsExactly("1.2.3-test");
    } finally {
      if (previous == null) System.clearProperty("messagevisor.version");
      else System.setProperty("messagevisor.version", previous);
    }
  }

  @Test
  void processRunnerDrainsLargeStderrWithoutHanging() throws Exception {
    MessagevisorJavaCli.ProcessResult result =
        MessagevisorJavaCli.runProcess(
            Path.of("."),
            List.of(
                "sh",
                "-c",
                "for i in $(seq 1 20000); do echo noisy-stderr-line >&2; done; echo ok"));

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).contains("ok");
    assertThat(result.stderr()).contains("noisy-stderr-line");
  }

  @Test
  void examplesUseIncludedEvaluationDatafileAndFormats() {
    Map<String, Object> messageExample = map(
        "locale", "en-US",
        "message", "only.included",
        "evaluationInput", map(
            "datafile", map(
                "schemaVersion", "1",
                "messagevisorVersion", "0.0.1",
                "revision", "1",
                "target", "message-only",
                "locale", "en-US",
                "messages", map("only.included", map()),
                "translations", map("only.included", "From included datafile"))));

    MessagevisorJavaCli.reevaluateExample(messageExample, List.of());

    assertThat(messageExample.get("evaluatedTranslation")).isEqualTo("From included datafile");

    Map<String, Object> rawExample = map(
        "locale", "en-US",
        "rawMessage", "{v, number, oneDecimal}",
        "evaluationInput", map(
            "values", map("v", 1.24),
            "defaultFormats", map(
                "en-US", map(
                    "number", map(
                        "oneDecimal", map("maximumFractionDigits", 0)))),
            "formats", map(
                "number", map(
                    "oneDecimal", map("maximumFractionDigits", 1)))));

    MessagevisorJavaCli.reevaluateExample(rawExample, List.of(IcuModule.create()));

    assertThat(rawExample.get("evaluatedTranslation")).isEqualTo("1.2");
  }

  private static Map<String, Object> map(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put(String.valueOf(entries[i]), entries[i + 1]);
    }
    return result;
  }
}
