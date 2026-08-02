package com.messagevisor.sdk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class DatafileMessage {
  private boolean deprecated;
  private String deprecationWarning;
  private Map<String, Object> meta = new LinkedHashMap<>();
  private List<MessageOverride> overrides = new ArrayList<>();
  private final Map<String, Object> additional = new LinkedHashMap<>();

  public boolean isDeprecated() {
    return deprecated;
  }

  public void setDeprecated(boolean deprecated) {
    this.deprecated = deprecated;
  }

  public String getDeprecationWarning() {
    return deprecationWarning;
  }

  public void setDeprecationWarning(String deprecationWarning) {
    this.deprecationWarning = deprecationWarning;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta == null ? new LinkedHashMap<>() : meta;
  }

  public List<MessageOverride> getOverrides() {
    return overrides;
  }

  public void setOverrides(List<MessageOverride> overrides) {
    this.overrides = overrides == null ? new ArrayList<>() : overrides;
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
