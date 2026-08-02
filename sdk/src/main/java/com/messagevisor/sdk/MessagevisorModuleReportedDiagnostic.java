package com.messagevisor.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A module-owned diagnostic. The SDK adds module provenance before delivering it. */
public record MessagevisorModuleReportedDiagnostic(
    LogLevel level, String code, String message, Map<String, Object> details, Object originalError) {
  public MessagevisorModuleReportedDiagnostic {
    details = Collections.unmodifiableMap(new LinkedHashMap<>(details == null ? Map.of() : details));
  }

  public static Builder builder(LogLevel level, String code, String message) {
    return new Builder(level, code, message);
  }

  public static final class Builder {
    private final LogLevel level;
    private final String code;
    private final String message;
    private final Map<String, Object> details = new LinkedHashMap<>();
    private Object originalError;

    private Builder(LogLevel level, String code, String message) {
      this.level = level;
      this.code = code;
      this.message = message;
    }

    public Builder details(Map<String, Object> details) {
      this.details.clear();
      if (details != null) {
        this.details.putAll(details);
      }
      return this;
    }

    public Builder detail(String key, Object value) {
      this.details.put(key, value);
      return this;
    }

    public Builder originalError(Object originalError) {
      this.originalError = originalError;
      return this;
    }

    public MessagevisorModuleReportedDiagnostic build() {
      return new MessagevisorModuleReportedDiagnostic(level, code, message, details, originalError);
    }
  }
}
