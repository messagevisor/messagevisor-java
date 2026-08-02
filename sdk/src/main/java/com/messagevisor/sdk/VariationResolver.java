package com.messagevisor.sdk;

import java.util.Map;

@FunctionalInterface
public interface VariationResolver {
  String resolve(String experimentKey, Map<String, Object> context);
}
