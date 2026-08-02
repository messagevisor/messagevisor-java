package com.messagevisor.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonSupport {
  public static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private JsonSupport() {}

  public static DatafileContent parseDatafile(Object input) {
    if (input instanceof DatafileContent datafile) {
      return datafile;
    }
    if (input instanceof String string) {
      try {
        return MAPPER.readValue(string, DatafileContent.class);
      } catch (JsonProcessingException error) {
        try {
          return MAPPER.readValue(Files.readString(Path.of(string)), DatafileContent.class);
        } catch (IOException ignored) {
          throw new IllegalArgumentException("could not parse datafile", error);
        }
      }
    }
    return MAPPER.convertValue(input, DatafileContent.class);
  }
}
