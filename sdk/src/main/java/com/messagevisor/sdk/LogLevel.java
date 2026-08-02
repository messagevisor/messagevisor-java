package com.messagevisor.sdk;

public enum LogLevel {
  FATAL,
  ERROR,
  WARN,
  INFO,
  DEBUG;

  public static LogLevel fromString(String value) {
    if (value == null || value.isBlank()) {
      return INFO;
    }
    return LogLevel.valueOf(value.trim().toUpperCase());
  }

  public boolean allows(LogLevel level) {
    return this.ordinal() >= level.ordinal();
  }

  public String wireValue() {
    return name().toLowerCase();
  }
}
