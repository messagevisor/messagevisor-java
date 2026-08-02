package com.messagevisor.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CloneUtils {
  private CloneUtils() {}

  @SuppressWarnings("unchecked")
  static Object deepCopyValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, child) -> copy.put(String.valueOf(key), deepCopyValue(child)));
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      for (Object child : list) {
        copy.add(deepCopyValue(child));
      }
      return copy;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> deepCopyMap(Map<String, Object> map) {
    Map<String, Object> copy = new LinkedHashMap<>();
    if (map == null) {
      return copy;
    }
    map.forEach((key, value) -> copy.put(key, deepCopyValue(value)));
    return copy;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Map<String, Object>> deepCopyMapOfMaps(Map<String, Map<String, Object>> map) {
    Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
    if (map == null) {
      return copy;
    }
    map.forEach((key, value) -> copy.put(key, deepCopyMap(value)));
    return copy;
  }
}
