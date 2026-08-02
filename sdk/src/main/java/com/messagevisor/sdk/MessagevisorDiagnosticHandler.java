package com.messagevisor.sdk;

@FunctionalInterface
public interface MessagevisorDiagnosticHandler {
  void handle(MessagevisorDiagnostic diagnostic);
}
