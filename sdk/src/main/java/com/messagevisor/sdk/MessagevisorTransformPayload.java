package com.messagevisor.sdk;

import java.util.Map;

public record MessagevisorTransformPayload(
    Object translation,
    String locale,
    String source,
    String messageKey,
    Map<String, Object> meta) {}
