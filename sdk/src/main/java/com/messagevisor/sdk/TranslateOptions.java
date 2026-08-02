package com.messagevisor.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TranslateOptions extends EvaluationOptions {
  private final Map<String, Object> context;
  private final String defaultTranslation;

  private TranslateOptions(Builder builder) {
    super(builder);
    this.context = CloneUtils.deepCopyMap(builder.context);
    this.defaultTranslation = builder.defaultTranslation;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static TranslateOptions empty() {
    return builder().build();
  }

  public Map<String, Object> context() {
    return CloneUtils.deepCopyMap(context);
  }

  public String defaultTranslation() {
    return defaultTranslation;
  }

  public static final class Builder extends EvaluationOptions.Builder<Builder> {
    private Map<String, Object> context = new LinkedHashMap<>();
    private String defaultTranslation;

    public Builder context(Map<String, Object> context) {
      this.context = context == null ? new LinkedHashMap<>() : context;
      return this;
    }

    public Builder defaultTranslation(String defaultTranslation) {
      this.defaultTranslation = defaultTranslation;
      return this;
    }

    @Override
    public TranslateOptions build() {
      return new TranslateOptions(this);
    }
  }
}
