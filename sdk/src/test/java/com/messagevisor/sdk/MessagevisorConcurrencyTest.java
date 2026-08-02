package com.messagevisor.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MessagevisorConcurrencyTest {
  @Test
  void supportsConcurrentEvaluationSubscriptionsAndContextUpdates() throws Exception {
    Messagevisor messagevisor = Messagevisor.create(
        MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).logLevel(LogLevel.FATAL).build());
    AtomicInteger events = new AtomicInteger();
    var executor = Executors.newFixedThreadPool(12);
    List<Callable<Void>> work = new ArrayList<>();

    for (int index = 0; index < 200; index++) {
      int current = index;
      work.add(() -> {
        MessagevisorUnsubscribe unsubscribe = messagevisor.subscribe(events::incrementAndGet);
        messagevisor.setContext(Map.of("request-" + current, current));
        assertThat(messagevisor.translate("greeting", Map.of())).isEqualTo("Hello {name}");
        unsubscribe.unsubscribe();
        return null;
      });
    }

    for (var future : executor.invokeAll(work)) future.get();
    executor.shutdown();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(messagevisor.getContext()).hasSize(200);
    assertThat(events.get()).isGreaterThan(0);
  }

  @Test
  void childApiDoesNotExposeRootOwnershipOperations() {
    List<String> methodNames = List.of(MessagevisorChild.class.getMethods()).stream()
        .map(Method::getName)
        .toList();

    assertThat(methodNames)
        .doesNotContain("setDatafile", "addModule", "removeModule", "spawn");
  }
}
