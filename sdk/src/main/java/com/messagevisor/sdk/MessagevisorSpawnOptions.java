package com.messagevisor.sdk;

public record MessagevisorSpawnOptions(String locale, String currency, String timeZone) {
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String locale;
    private String currency;
    private String timeZone;

    public Builder locale(String locale) { this.locale = locale; return this; }
    public Builder currency(String currency) { this.currency = currency; return this; }
    public Builder timeZone(String timeZone) { this.timeZone = timeZone; return this; }
    public MessagevisorSpawnOptions build() { return new MessagevisorSpawnOptions(locale, currency, timeZone); }
  }
}
