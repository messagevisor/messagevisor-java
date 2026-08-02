package com.messagevisor.sdk;

public interface MessagevisorModule extends AutoCloseable {
  default String name() {
    return null;
  }

  default void setup(MessagevisorModuleApi api) {}

  default Object format(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
    return null;
  }

  default Object transform(MessagevisorTransformPayload payload, MessagevisorModuleApi api) {
    return null;
  }

  @Override
  default void close() throws Exception {}
}
