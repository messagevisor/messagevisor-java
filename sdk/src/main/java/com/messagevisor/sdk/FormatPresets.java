package com.messagevisor.sdk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class FormatPresets {
  private Map<String, Map<String, Object>> number = new LinkedHashMap<>();
  private Map<String, Map<String, Object>> date = new LinkedHashMap<>();
  private Map<String, Map<String, Object>> time = new LinkedHashMap<>();
  private Map<String, Map<String, Object>> relative = new LinkedHashMap<>();
  private Map<String, Map<String, Object>> dateTimeRange = new LinkedHashMap<>();
  private final Map<String, Object> additional = new LinkedHashMap<>();

  public Map<String, Map<String, Object>> getNumber() {
    return number;
  }

  public void setNumber(Map<String, Map<String, Object>> number) {
    this.number = number == null ? new LinkedHashMap<>() : number;
  }

  public Map<String, Map<String, Object>> getDate() {
    return date;
  }

  public void setDate(Map<String, Map<String, Object>> date) {
    this.date = date == null ? new LinkedHashMap<>() : date;
  }

  public Map<String, Map<String, Object>> getTime() {
    return time;
  }

  public void setTime(Map<String, Map<String, Object>> time) {
    this.time = time == null ? new LinkedHashMap<>() : time;
  }

  public Map<String, Map<String, Object>> getRelative() {
    return relative;
  }

  public void setRelative(Map<String, Map<String, Object>> relative) {
    this.relative = relative == null ? new LinkedHashMap<>() : relative;
  }

  public Map<String, Map<String, Object>> getDateTimeRange() {
    return dateTimeRange;
  }

  public void setDateTimeRange(Map<String, Map<String, Object>> dateTimeRange) {
    this.dateTimeRange = dateTimeRange == null ? new LinkedHashMap<>() : dateTimeRange;
  }

  @JsonAnyGetter
  public Map<String, Object> additional() {
    return additional;
  }

  @JsonAnySetter
  public void setAdditional(String key, Object value) {
    additional.put(key, value);
  }

  public FormatPresets copy() {
    FormatPresets copy = new FormatPresets();
    copy.setNumber(CloneUtils.deepCopyMapOfMaps(number));
    copy.setDate(CloneUtils.deepCopyMapOfMaps(date));
    copy.setTime(CloneUtils.deepCopyMapOfMaps(time));
    copy.setRelative(CloneUtils.deepCopyMapOfMaps(relative));
    copy.setDateTimeRange(CloneUtils.deepCopyMapOfMaps(dateTimeRange));
    copy.additional.putAll(CloneUtils.deepCopyMap(additional));
    return copy;
  }
}
