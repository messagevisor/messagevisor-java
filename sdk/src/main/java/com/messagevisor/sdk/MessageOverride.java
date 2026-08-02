package com.messagevisor.sdk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessageOverride {
  private String key;
  private Object conditions;
  private Object segments;
  private String translation;
  private final Map<String, Object> additional = new LinkedHashMap<>();

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Object getConditions() {
    return conditions;
  }

  public void setConditions(Object conditions) {
    this.conditions = conditions;
  }

  public Object getSegments() {
    return segments;
  }

  public void setSegments(Object segments) {
    this.segments = segments;
  }

  public String getTranslation() {
    return translation;
  }

  public void setTranslation(String translation) {
    this.translation = translation;
  }

  @JsonAnyGetter
  public Map<String, Object> additional() {
    return additional;
  }

  @JsonAnySetter
  public void setAdditional(String key, Object value) {
    additional.put(key, value);
  }
}
