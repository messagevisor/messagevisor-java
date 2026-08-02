package com.messagevisor.sdk;

@FunctionalInterface
public interface MessagevisorEventCallback {
  void handle(MessagevisorEvent event);
}
