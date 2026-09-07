package com.messagevisor.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonSupport {
  public static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private JsonSupport() {}

  public static DatafileContent parseDatafile(Object input) {
    if (input instanceof DatafileContent datafile) {
      ObjectNode node = MAPPER.valueToTree(datafile);
      if (datafile.getDirection() == null) node.remove("direction");
      validateDatafile(node);
      // Typed inputs have the same ownership boundary as JSON: SDK writes must not
      // mutate a caller's object or a different root that received the same input.
      return MAPPER.convertValue(node, DatafileContent.class);
    }
    JsonNode node;
    if (input instanceof String string) {
      try {
        node = MAPPER.readTree(string);
      } catch (JsonProcessingException error) {
        try {
          node = MAPPER.readTree(Files.readString(Path.of(string)));
        } catch (IOException ignored) {
          throw new IllegalArgumentException("could not parse datafile", error);
        }
      }
    } else {
      node = MAPPER.valueToTree(input);
    }
    validateDatafile(node);
    return MAPPER.convertValue(node, DatafileContent.class);
  }

  private static void validateDatafile(JsonNode node) {
    if (node == null || !node.isObject()
        || !node.path("schemaVersion").isTextual()
        || !"1".equals(node.path("schemaVersion").textValue())
        || !node.path("locale").isTextual() || node.path("locale").textValue().isEmpty()) {
      throw new IllegalArgumentException("could not parse datafile");
    }
    for (String field : new String[] {"messagevisorVersion", "revision", "target"}) {
      if (!node.path(field).isTextual()) throw new IllegalArgumentException("could not parse datafile");
    }
    for (String field : new String[] {"segments", "messages", "translations"}) {
      if (!node.path(field).isObject()) throw new IllegalArgumentException("could not parse datafile");
    }
    if (node.has("formats") && !node.get("formats").isObject()) {
      throw new IllegalArgumentException("could not parse datafile");
    }
    if (node.has("direction") && !"ltr".equals(node.get("direction").textValue())
        && !"rtl".equals(node.get("direction").textValue())) {
      throw new IllegalArgumentException("could not parse datafile");
    }
  }
}
