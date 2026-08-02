package com.messagevisor.sdk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Segment {
  private String key;
  private boolean archived;
  private Object conditions;
  private String description;
  private final Map<String, Object> additional = new LinkedHashMap<>();

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  public Object getConditions() {
    return conditions;
  }

  public void setConditions(Object conditions) {
    this.conditions = conditions;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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
