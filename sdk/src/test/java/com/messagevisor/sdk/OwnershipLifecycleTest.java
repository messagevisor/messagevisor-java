package com.messagevisor.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class OwnershipLifecycleTest {
  @Test
  void sharedTypedInputNeverMutatesTheCallerOrOtherRootsDuringMergeOrReplacement() {
    DatafileContent shared = TestDatafiles.enUs();
    shared.setFormats(new FormatPresets());
    shared.setTranslations(Map.of("shared", "Shared"));
    Messagevisor a = Messagevisor.create(MessagevisorOptions.builder().datafile(TestDatafiles.enUs()).build());
    Messagevisor b = Messagevisor.create(MessagevisorOptions.builder().datafile(shared).build());
    MessagevisorSnapshot before = b.getSnapshot();
    AtomicInteger changes = new AtomicInteger();
    b.subscribe(changes::incrementAndGet);
    a.setDatafile(shared);
    assertThat(a.getDatafile().getFormats().getNumber()).containsKey("money");
    assertThat(b.getDatafile().getFormats().getNumber()).isEmpty();
    assertThat(shared.getFormats().getNumber()).isEmpty();
    assertThat(shared.getTranslations()).containsExactlyEntriesOf(Map.of("shared", "Shared"));
    assertThat(b.getDatafile().getTranslations()).isEqualTo(shared.getTranslations());
    assertThat(b.getSnapshot()).isEqualTo(before);
    assertThat(changes.get()).isZero();
    assertThat(a.getDatafile()).isNotSameAs(shared).isNotSameAs(b.getDatafile());

    a.setDatafile(shared, true);
    shared.setRevision("caller update");
    shared.getFormats().getNumber().put("caller", Map.of("style", "percent"));
    assertThat(a.getRevision()).isEqualTo("en-1");
    assertThat(b.getRevision()).isEqualTo("en-1");
    assertThat(a.getDatafile().getFormats().getNumber()).isEmpty();
    assertThat(b.getDatafile().getFormats().getNumber()).isEmpty();
  }

  @Test
  void typedInputNestedMessagesAndConditionsAreCopiedAsWell() {
    DatafileContent shared = TestDatafiles.enUs();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(shared).build());
    String original = m.getDatafile().getMessages().get("greeting").getOverrides().get(0).getTranslation();
    shared.getMessages().get("greeting").getOverrides().get(0).setTranslation("caller changed");
    shared.getSegments().get("platform-web").setConditions("*");
    assertThat(m.getDatafile().getMessages().get("greeting").getOverrides().get(0).getTranslation()).isEqualTo(original);
    assertThat(m.getDatafile().getSegments().get("platform-web").getConditions()).isInstanceOf(Map.class);
  }

  @Test
  void concurrentAsyncCloseCallersShareCompletionAndRunCleanupOnce() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger closes = new AtomicInteger();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().addModule(new MessagevisorModule() {
      @Override public void close() throws Exception {
        closes.incrementAndGet();
        entered.countDown();
        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test cleanup timed out");
      }
    }).build());
    CompletableFuture<Void> first = m.closeAsync();
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      CompletableFuture<Void> second = m.closeAsync();
      assertThat(second).isSameAs(first).isNotDone();
      List<CompletableFuture<CompletableFuture<Void>>> callers = new ArrayList<>();
      for (int i = 0; i < 20; i++) callers.add(CompletableFuture.supplyAsync(m::closeAsync));
      for (var caller : callers) assertThat(caller.get(5, TimeUnit.SECONDS)).isSameAs(first);
    } finally {
      release.countDown();
    }
    first.get(5, TimeUnit.SECONDS);
    assertThat(m.closeAsync()).isSameAs(first).isCompleted();
    m.close();
    assertThat(closes.get()).isEqualTo(1);
  }

  @Test
  void allCloseCallersObserveTheSameAggregateFailureAndRemainingModulesAreClosed() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    List<String> trace = new java.util.concurrent.CopyOnWriteArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().logLevel(LogLevel.FATAL)
        .addModule(new MessagevisorModule() {
          @Override public void close() { trace.add("first"); throw new IllegalStateException("first error"); }
        }).addModule(new MessagevisorModule() {
          @Override public void close() throws Exception {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test cleanup timed out");
            trace.add("second");
            throw new IllegalStateException("second error");
          }
        }).build());
    CompletableFuture<Void> first = m.closeAsync();
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(m.closeAsync()).isSameAs(first).isNotDone();
    } finally {
      release.countDown();
    }
    MessagevisorCloseException error = (MessagevisorCloseException)
        first.handle((value, failure) -> failure).get(5, TimeUnit.SECONDS);
    assertThat(error.errors()).extracting(Throwable::getMessage).containsExactly("second error", "first error");
    assertThat(trace).containsExactly("second", "first");
    assertThatThrownBy(first::join).isInstanceOf(CompletionException.class).hasCause(error);
    assertThatThrownBy(m::close).isSameAs(error);
    assertThat(m.closeAsync()).isSameAs(first);
  }

  @Test
  void synchronousClosePublishesItsCompletionToAsyncCallers() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().addModule(new MessagevisorModule() {
      @Override public void close() throws Exception {
        entered.countDown();
        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test cleanup timed out");
      }
    }).build());
    CompletableFuture<Void> synchronousCaller = CompletableFuture.runAsync(m::close);
    CompletableFuture<Void> asyncCaller;
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      asyncCaller = m.closeAsync();
      assertThat(asyncCaller).isNotDone();
    } finally {
      release.countDown();
    }
    synchronousCaller.get(5, TimeUnit.SECONDS);
    asyncCaller.get(5, TimeUnit.SECONDS);
  }

  @Test
  void reentrantSynchronousCloseDoesNotDeadlock() throws Exception {
    Messagevisor m = Messagevisor.create();
    m.addModule(new MessagevisorModule() {
      @Override public void close() { m.close(); }
    });
    m.closeAsync().get(5, TimeUnit.SECONDS);
  }

  @Test
  void failedSetupIsCleanedBeforeAnObserverClosesTheSdk() throws Exception {
    for (boolean asyncObserver : new boolean[] {false, true}) {
      for (boolean cleanupFails : new boolean[] {false, true}) {
        List<String> trace = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<Messagevisor> owner = new AtomicReference<>();
        IllegalStateException setupError = new IllegalStateException("setup");
        Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().logLevel(LogLevel.ERROR)
            .onDiagnostic(diagnostic -> {
              if (!diagnostic.code().equals("module_setup_error")) return;
              assertThat(diagnostic.originalError()).isSameAs(setupError);
              trace.add("diagnostic");
              if (asyncObserver) {
                // A bounded wait also proves notification occurs outside the
                // registration lock needed by asynchronous root shutdown.
                try {
                  owner.get().closeAsync().get(5, TimeUnit.SECONDS);
                } catch (Exception error) {
                  throw new AssertionError(error);
                }
              } else owner.get().close();
              trace.add("observer close completed");
            }).build());
        owner.set(m);
        m.addModule(new MessagevisorModule() {
          @Override public void close() { trace.add("existing close"); }
        });
        MessagevisorUnsubscribe remove = m.addModule(new MessagevisorModule() {
          @Override public String name() { return "failed"; }
          @Override public void setup(MessagevisorModuleApi api) {
            trace.add("setup");
            api.onDiagnostic(diagnostic -> trace.add("stale subscription"), null);
            throw setupError;
          }
          @Override public void close() {
            trace.add("partial close");
            if (cleanupFails) throw new IllegalStateException("partial close failed");
          }
        });
        remove.unsubscribe();
        m.closeAsync().get(5, TimeUnit.SECONDS);
        assertThat(trace).containsExactly("setup", "partial close", "diagnostic", "existing close", "observer close completed");
      }
    }
  }

  @Test
  void cleanupCallbacksObserveThePublishedSharedCompletionWithoutRecursing() throws Exception {
    for (boolean asyncOwner : new boolean[] {false, true}) {
      AtomicReference<CompletableFuture<Void>> observed = new AtomicReference<>();
      AtomicInteger closes = new AtomicInteger();
      Messagevisor m = Messagevisor.create();
      m.addModule(new MessagevisorModule() {
        @Override public void close() {
          closes.incrementAndGet();
          observed.set(m.closeAsync());
          assertThat(observed.get()).isNotDone();
          m.close();
          assertThat(m.closeAsync()).isSameAs(observed.get()).isNotDone();
        }
      });
      CompletableFuture<Void> outer = asyncOwner ? m.closeAsync() : CompletableFuture.runAsync(m::close);
      outer.get(5, TimeUnit.SECONDS);
      assertThat(m.closeAsync()).isSameAs(observed.get()).isCompleted();
      if (asyncOwner) assertThat(outer).isSameAs(observed.get());
      assertThat(closes.get()).isEqualTo(1);
    }
  }

  @Test
  void removingNamedAndAnonymousModulesClearsEveryChildApiAndSubscription() {
    for (String name : new String[] {"observer", null}) {
      Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().datafile(TestDatafiles.enUs())
          .logLevel(LogLevel.FATAL).build());
      List<MessagevisorModuleApi> apis = new ArrayList<>();
      AtomicInteger delivered = new AtomicInteger();
      AtomicInteger closes = new AtomicInteger();
      MessagevisorUnsubscribe remove = m.addModule(new MessagevisorModule() {
        @Override public String name() { return name; }
        @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
          if (!apis.contains(api)) {
            apis.add(api);
            api.onDiagnostic(diagnostic -> delivered.incrementAndGet(), null);
            api.setFlagResolver((key, context) -> true);
          }
          return null;
        }
        @Override public void close() { closes.incrementAndGet(); }
      });
      var child = m.spawn(Map.of());
      var second = m.spawn(Map.of());
      child.formatMessage("hello", Map.of());
      second.formatMessage("hello", Map.of());
      child.translate("missing");
      assertThat(delivered.get()).isEqualTo(1);
      if (name != null) m.removeModule(name); else remove.unsubscribe();
      remove.unsubscribe();
      int before = delivered.get();
      for (var api : apis) api.onDiagnostic(diagnostic -> delivered.incrementAndGet(), null);
      child.translate("missing");
      second.translate("missing");
      assertThat(delivered.get()).isEqualTo(before);
      assertThat(closes.get()).isEqualTo(1);
      assertChildModuleStateEmpty(child);
      assertChildModuleStateEmpty(second);
      child.close();
      second.close();
      m.close();
      assertThat(closes.get()).isEqualTo(1);
    }
  }

  @Test
  void closingParentInvalidatesChildApisIncludingAnonymousModules() {
    for (String name : new String[] {"observer", null}) {
      Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en-US").logLevel(LogLevel.FATAL).build());
      AtomicReference<MessagevisorModuleApi> retained = new AtomicReference<>();
      m.addModule(new MessagevisorModule() {
        @Override public String name() { return name; }
        @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
          retained.set(api);
          api.onDiagnostic(diagnostic -> {}, null);
          api.setFlagResolver((key, context) -> true);
          return null;
        }
      });
      var child = m.spawn(Map.of());
      child.formatMessage("hello", Map.of());
      m.close();
      retained.get().onDiagnostic(diagnostic -> {}, null);
      retained.get().setFlagResolver((key, context) -> true);
      assertChildModuleStateEmpty(child);
      child.close();
    }
  }

  @Test
  void oldChildApisCannotInterfereWithAReplacementModuleOfTheSameName() {
    List<MessagevisorDiagnostic> diagnostics = new ArrayList<>();
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en-US")
        .logLevel(LogLevel.ERROR).onDiagnostic(diagnostics::add).build());
    AtomicReference<MessagevisorModuleApi> oldApi = new AtomicReference<>();
    AtomicInteger deliveries = new AtomicInteger();
    m.addModule(new MessagevisorModule() {
      @Override public String name() { return "observer"; }
      @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
        oldApi.set(api);
        api.onDiagnostic(diagnostic -> deliveries.addAndGet(100), null);
        return null;
      }
    });
    var child = m.spawn(Map.of());
    child.formatMessage("hello", Map.of());
    m.removeModule("observer");
    AtomicReference<MessagevisorModuleApi> newApi = new AtomicReference<>();
    m.addModule(new MessagevisorModule() {
      @Override public String name() { return "observer"; }
      @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
        if (newApi.getAndSet(api) == null) api.onDiagnostic(diagnostic -> deliveries.incrementAndGet(), null);
        return null;
      }
    });
    child.formatMessage("hello", Map.of());
    oldApi.get().onDiagnostic(diagnostic -> deliveries.addAndGet(100), null);
    oldApi.get().reportDiagnostic(MessagevisorModuleReportedDiagnostic.builder(LogLevel.ERROR, "stale", "stale").build());
    assertThat(diagnostics).isEmpty();
    child.translate("missing");
    assertThat(deliveries.get()).isEqualTo(1);
    assertThat(oldApi.get()).isNotSameAs(newApi.get());
    child.close();
    m.close();
  }

  @Test
  void removalFailureStillDisposesChildRegistrationsAndDoesNotCloseTwice() {
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en-US").logLevel(LogLevel.FATAL).build());
    AtomicInteger closes = new AtomicInteger();
    MessagevisorUnsubscribe remove = m.addModule(new MessagevisorModule() {
      @Override public String name() { return "broken"; }
      @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
        api.onDiagnostic(diagnostic -> {}, null);
        api.setVariationResolver((key, context) -> "a");
        return null;
      }
      @Override public void close() { closes.incrementAndGet(); throw new IllegalStateException("cleanup"); }
    });
    var child = m.spawn(Map.of());
    child.formatMessage("hello", Map.of());
    assertThatThrownBy(() -> m.removeModule("broken")).isInstanceOf(MessagevisorCloseException.class);
    assertChildModuleStateEmpty(child);
    remove.unsubscribe();
    m.close();
    child.close();
    assertThat(closes.get()).isEqualTo(1);
  }

  @Test
  void closingAChildReleasesItsRegistrationWithoutClosingSharedModules() throws Exception {
    Messagevisor m = Messagevisor.create(MessagevisorOptions.builder().locale("en-US").build());
    AtomicInteger closes = new AtomicInteger();
    AtomicReference<MessagevisorModuleApi> retained = new AtomicReference<>();
    m.addModule(new MessagevisorModule() {
      @Override public Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
        retained.set(api);
        api.onDiagnostic(diagnostic -> {}, null);
        return null;
      }
      @Override public void close() { closes.incrementAndGet(); }
    });
    var child = m.spawn(Map.of());
    child.formatMessage("hello", Map.of());
    child.close();
    retained.get().onDiagnostic(diagnostic -> {}, null);
    assertChildModuleStateEmpty(child);
    assertThat(field(m, "children")).isEqualTo(List.of());
    assertThat(closes.get()).isZero();
    m.close();
    assertThat(closes.get()).isEqualTo(1);
  }

  private static void assertChildModuleStateEmpty(MessagevisorChild child) {
    try {
      Object delegate = field(child, "delegate");
      for (String name : List.of("moduleApis", "moduleApiActivity", "anonymousModuleApiKeys")) {
        assertThat((Map<?, ?>) field(delegate, name)).as(name).isEmpty();
      }
      for (String name : List.of("moduleDiagnosticSubscriptions", "moduleFlagResolvers", "moduleVariationResolvers")) {
        assertThat((List<?>) field(delegate, name)).as(name).isEmpty();
      }
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
  }

  private static Object field(Object object, String name) throws ReflectiveOperationException {
    var field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }
}
