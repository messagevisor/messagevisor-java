package com.messagevisor.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

final class ObjectMaps {
  private ObjectMaps() {}

  static Map<String, Object> stringMap(Map<?, ?> raw) {
    Map<String, Object> result = new LinkedHashMap<>();
    raw.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  static String stringOption(Map<String, Object> options, String key) {
    Object value = options == null ? null : options.get(key);
    return value == null ? null : String.valueOf(value);
  }

  static int intOption(Map<String, Object> options, String key, int fallback) {
    Object value = options == null ? null : options.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value != null) {
      try {
        return Integer.parseInt(String.valueOf(value));
      } catch (NumberFormatException ignored) {
      }
    }
    return fallback;
  }

  static boolean boolOption(Map<String, Object> options, String key, boolean fallback) {
    Object value = options == null ? null : options.get(key);
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
  }
}
