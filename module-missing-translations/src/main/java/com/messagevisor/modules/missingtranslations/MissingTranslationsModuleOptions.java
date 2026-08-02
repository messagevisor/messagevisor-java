package com.messagevisor.modules.missingtranslations;

public final class MissingTranslationsModuleOptions {
  private final String name;
  private final MissingTranslationHandler handler;
  private final boolean dedupe;

  private MissingTranslationsModuleOptions(Builder builder) {
    this.name = builder.name;
    this.handler = builder.handler;
    this.dedupe = builder.dedupe;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String name() {
    return name;
  }

  public MissingTranslationHandler handler() {
    return handler;
  }

  public boolean dedupe() {
    return dedupe;
  }

  public static final class Builder {
    private String name;
    private MissingTranslationHandler handler;
    private boolean dedupe;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder handler(MissingTranslationHandler handler) {
      this.handler = handler;
      return this;
    }

    public Builder dedupe(boolean dedupe) {
      this.dedupe = dedupe;
      return this;
    }

    public MissingTranslationsModuleOptions build() {
      return new MissingTranslationsModuleOptions(this);
    }
  }
}
