package com.messagevisor.sdk;

public record MessagevisorModuleDiagnosticOptions(LogLevel logLevel) {
  public MessagevisorModuleDiagnosticOptions {
    if (logLevel == null) {
      logLevel = LogLevel.INFO;
    }
  }
}
