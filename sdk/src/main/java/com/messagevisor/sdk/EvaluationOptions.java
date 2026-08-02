package com.messagevisor.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

public class EvaluationOptions {
  private final String locale;
  private final String currency;
  private final String timeZone;
  private final FormatPresets formats;
  private final Map<String, Object> moduleOptions;

  protected EvaluationOptions(Builder<?> builder) {
    this.locale = builder.locale;
    this.currency = builder.currency;
    this.timeZone = builder.timeZone;
    this.formats = builder.formats;
    this.moduleOptions = CloneUtils.deepCopyMap(builder.moduleOptions);
  }

  public static Builder<?> builder() {
    return new Builder<>();
  }

  public String locale() {
    return locale;
  }

  public String currency() {
    return currency;
  }

  public String timeZone() {
    return timeZone;
  }

  public FormatPresets formats() {
    return formats;
  }

  public Map<String, Object> moduleOptions() {
    return CloneUtils.deepCopyMap(moduleOptions);
  }

  public static class Builder<T extends Builder<T>> {
    private String locale;
    private String currency;
    private String timeZone;
    private FormatPresets formats;
    private Map<String, Object> moduleOptions = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    protected T self() {
      return (T) this;
    }

    public T locale(String locale) {
      this.locale = locale;
      return self();
    }

    public T currency(String currency) {
      this.currency = currency;
      return self();
    }

    public T timeZone(String timeZone) {
      this.timeZone = timeZone;
      return self();
    }

    public T formats(FormatPresets formats) {
      this.formats = formats;
      return self();
    }

    public T moduleOptions(Map<String, Object> moduleOptions) {
      this.moduleOptions = moduleOptions == null ? new LinkedHashMap<>() : moduleOptions;
      return self();
    }

    public EvaluationOptions build() {
      return new EvaluationOptions(this);
    }
  }
}
