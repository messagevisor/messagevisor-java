package com.messagevisor.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record MessagevisorDiagnostic(
    LogLevel level,
    String code,
    String message,
    Map<String, Object> details,
    String module,
    String moduleName,
    Object originalError) {
  public MessagevisorDiagnostic {
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
    private String module;
    private String moduleName;
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

    public Builder module(String module) {
      this.module = module;
      return this;
    }

    public Builder moduleName(String moduleName) {
      this.moduleName = moduleName;
      return this;
    }

    public Builder originalError(Object originalError) {
      this.originalError = originalError;
      return this;
    }

    public MessagevisorDiagnostic build() {
      return new MessagevisorDiagnostic(
          level, code, message, details, module, moduleName, originalError);
    }
  }
}
