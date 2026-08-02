package com.messagevisor.modules.interpolation;

import static org.assertj.core.api.Assertions.assertThat;

import com.messagevisor.sdk.DatafileContent;
import com.messagevisor.sdk.DatafileMessage;
import com.messagevisor.sdk.LogLevel;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorOptions;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class InterpolationModuleTest {
  @Test
  void replacesPrimitiveValuesAndLeavesMissingValuesIntact() {
    DatafileContent datafile = new DatafileContent();
    datafile.setSchemaVersion("1");
    datafile.setMessagevisorVersion("0.0.1");
    datafile.setRevision("1");
    datafile.setTarget("web");
    datafile.setLocale("en-US");
    datafile.setMessages(Map.of("greeting", new DatafileMessage()));
    datafile.setTranslations(Map.of("greeting", "Hello {name}, {missing}"));

    Messagevisor m =
        Messagevisor.create(
            MessagevisorOptions.builder()
                .datafile(datafile)
                .addModule(InterpolationModule.create())
                .logLevel(LogLevel.FATAL)
                .build());

    assertThat(m.translate("greeting", Map.of("name", "Ada"))).isEqualTo("Hello Ada, {missing}");
  }
}
