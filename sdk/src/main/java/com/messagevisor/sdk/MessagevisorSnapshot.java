package com.messagevisor.sdk;

import java.util.List;
import java.util.Map;

public record MessagevisorSnapshot(
    int version,
    String locale,
    String direction,
    Map<String, Object> context,
    String currency,
    String timeZone,
    List<String> datafileLocales,
    Map<String, String> datafileRevisionsByLocale) {}
