package com.messagevisor.sdk;

public enum EventName {
  CHANGE("change"),
  ERROR("error"),
  DATAFILE_SET("datafile_set"),
  LOCALE_SET("locale_set"),
  CONTEXT_SET("context_set"),
  CURRENCY_SET("currency_set"),
  TIME_ZONE_SET("timeZone_set");

  private final String wireValue;

  EventName(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
