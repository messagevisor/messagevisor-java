package com.messagevisor.sdk;

import java.util.Map;

public record MessagevisorEvent(
    EventName type,
    EventName source,
    int version,
    MessagevisorSnapshot snapshot,
    MessagevisorSnapshot previousSnapshot,
    String locale,
    String activeLocale,
    String previousLocale,
    DatafileContent datafile,
    Map<String, Object> context,
    Map<String, Object> previousContext,
    String currency,
    String previousCurrency,
    String timeZone,
    String previousTimeZone,
    Boolean replaced,
    MessagevisorDiagnostic diagnostic) {}
