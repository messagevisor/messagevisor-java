package com.messagevisor.sdk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class DatafileContent {
  private String schemaVersion;
  private String messagevisorVersion;
  private String revision;
  private String target;
  private String locale;
  private String direction;
  private FormatPresets formats = new FormatPresets();
  private Map<String, Segment> segments = new LinkedHashMap<>();
  private Map<String, DatafileMessage> messages = new LinkedHashMap<>();
  private Map<String, String> translations = new LinkedHashMap<>();
  private final Map<String, Object> additional = new LinkedHashMap<>();

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getMessagevisorVersion() {
    return messagevisorVersion;
  }

  public void setMessagevisorVersion(String messagevisorVersion) {
    this.messagevisorVersion = messagevisorVersion;
  }

  public String getRevision() {
    return revision;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public FormatPresets getFormats() {
    return formats;
  }

  public void setFormats(FormatPresets formats) {
    this.formats = formats == null ? new FormatPresets() : formats;
  }

  public Map<String, Segment> getSegments() {
    return segments;
  }

  public void setSegments(Map<String, Segment> segments) {
    this.segments = segments == null ? new LinkedHashMap<>() : segments;
  }

  public Map<String, DatafileMessage> getMessages() {
    return messages;
  }

  public void setMessages(Map<String, DatafileMessage> messages) {
    this.messages = messages == null ? new LinkedHashMap<>() : messages;
  }

  public Map<String, String> getTranslations() {
    return translations;
  }

  public void setTranslations(Map<String, String> translations) {
    this.translations = translations == null ? new LinkedHashMap<>() : translations;
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
