package com.messagevisor.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MessagevisorOptions {
  private final Object datafile;
  private final Map<String, Map<String, String>> defaultTranslations;
  private final Map<String, FormatPresets> defaultFormats;
  private final String currency;
  private final String timeZone;
  private final Map<String, Object> context;
  private final String locale;
  private final FlagResolver resolveFlag;
  private final VariationResolver resolveVariation;
  private final MessagevisorDiagnosticHandler onDiagnostic;
  private final LogLevel logLevel;
  private final List<MessagevisorModule> modules;

  private MessagevisorOptions(Builder builder) {
    this.datafile = builder.datafile;
    this.defaultTranslations = copyDefaultTranslations(builder.defaultTranslations);
    this.defaultFormats = new LinkedHashMap<>(builder.defaultFormats);
    this.currency = builder.currency;
    this.timeZone = builder.timeZone;
    this.context = CloneUtils.deepCopyMap(builder.context);
    this.locale = builder.locale;
    this.resolveFlag = builder.resolveFlag;
    this.resolveVariation = builder.resolveVariation;
    this.onDiagnostic = builder.onDiagnostic;
    this.logLevel = builder.logLevel == null ? LogLevel.INFO : builder.logLevel;
    this.modules = List.copyOf(builder.modules);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Object datafile() {
    return datafile;
  }

  public Map<String, Map<String, String>> defaultTranslations() {
    return copyDefaultTranslations(defaultTranslations);
  }

  public Map<String, FormatPresets> defaultFormats() {
    return new LinkedHashMap<>(defaultFormats);
  }

  public String currency() {
    return currency;
  }

  public String timeZone() {
    return timeZone;
  }

  public Map<String, Object> context() {
    return CloneUtils.deepCopyMap(context);
  }

  public String locale() {
    return locale;
  }

  public FlagResolver resolveFlag() {
    return resolveFlag;
  }

  public VariationResolver resolveVariation() {
    return resolveVariation;
  }

  public MessagevisorDiagnosticHandler onDiagnostic() {
    return onDiagnostic;
  }

  public LogLevel logLevel() {
    return logLevel;
  }

  public List<MessagevisorModule> modules() {
    return modules;
  }

  private static Map<String, Map<String, String>> copyDefaultTranslations(
      Map<String, Map<String, String>> input) {
    Map<String, Map<String, String>> copy = new LinkedHashMap<>();
    if (input == null) {
      return copy;
    }
    input.forEach((locale, translations) -> copy.put(locale, new LinkedHashMap<>(translations)));
    return copy;
  }

  public static final class Builder {
    private Object datafile;
    private Map<String, Map<String, String>> defaultTranslations = new LinkedHashMap<>();
    private Map<String, FormatPresets> defaultFormats = new LinkedHashMap<>();
    private String currency;
    private String timeZone;
    private Map<String, Object> context = new LinkedHashMap<>();
    private String locale;
    private FlagResolver resolveFlag;
    private VariationResolver resolveVariation;
    private MessagevisorDiagnosticHandler onDiagnostic;
    private LogLevel logLevel = LogLevel.INFO;
    private List<MessagevisorModule> modules = new ArrayList<>();

    public Builder datafile(Object datafile) {
      this.datafile = datafile;
      return this;
    }

    public Builder defaultTranslations(Map<String, Map<String, String>> defaultTranslations) {
      this.defaultTranslations =
          defaultTranslations == null ? new LinkedHashMap<>() : defaultTranslations;
      return this;
    }

    public Builder defaultFormats(Map<String, FormatPresets> defaultFormats) {
      this.defaultFormats = defaultFormats == null ? new LinkedHashMap<>() : defaultFormats;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder timeZone(String timeZone) {
      this.timeZone = timeZone;
      return this;
    }

    public Builder context(Map<String, Object> context) {
      this.context = context == null ? new LinkedHashMap<>() : context;
      return this;
    }

    public Builder locale(String locale) {
      this.locale = locale;
      return this;
    }

    public Builder resolveFlag(FlagResolver resolveFlag) {
      this.resolveFlag = resolveFlag;
      return this;
    }

    public Builder resolveVariation(VariationResolver resolveVariation) {
      this.resolveVariation = resolveVariation;
      return this;
    }

    public Builder onDiagnostic(MessagevisorDiagnosticHandler onDiagnostic) {
      this.onDiagnostic = onDiagnostic;
      return this;
    }

    public Builder logLevel(LogLevel logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    public Builder modules(List<MessagevisorModule> modules) {
      this.modules = modules == null ? new ArrayList<>() : modules;
      return this;
    }

    public Builder addModule(MessagevisorModule module) {
      this.modules.add(module);
      return this;
    }

    public MessagevisorOptions build() {
      return new MessagevisorOptions(this);
    }
  }
}
