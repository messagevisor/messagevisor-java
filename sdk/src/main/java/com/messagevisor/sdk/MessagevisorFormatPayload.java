package com.messagevisor.sdk;

import java.util.Map;

public record MessagevisorFormatPayload(
    Object translation,
    Map<String, Object> values,
    String locale,
    String source,
    String messageKey,
    Map<String, Object> meta,
    FormatPresets formats,
    Map<String, Object> moduleOptions,
    String currency,
    String timeZone) {}
