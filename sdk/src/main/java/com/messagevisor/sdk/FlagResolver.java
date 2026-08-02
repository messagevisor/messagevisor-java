package com.messagevisor.sdk;

import java.util.Map;

@FunctionalInterface
public interface FlagResolver {
  boolean resolve(String featureKey, Map<String, Object> context);
}
