package com.messagevisor.modules.interpolation;

import com.messagevisor.sdk.MessagevisorFormatPayload;
import com.messagevisor.sdk.MessagevisorModule;
import com.messagevisor.sdk.MessagevisorModuleApi;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InterpolationModule implements MessagevisorModule {
  private final String name;
  private final Pattern pattern;

  public InterpolationModule() {
    this("interpolation", Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}"));
  }

  public InterpolationModule(String name, Pattern pattern) {
    this.name = name == null || name.isBlank() ? "interpolation" : name;
    this.pattern = pattern == null ? Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}") : pattern;
  }

  public static InterpolationModule create() {
    return new InterpolationModule();
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Object format(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
    if (!(payload.translation() instanceof String translation) || payload.values() == null) {
      return null;
    }
    Matcher matcher = pattern.matcher(translation);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      Object value = payload.values().get(matcher.group(1));
      if (value instanceof String || value instanceof Number || value instanceof Boolean) {
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(value)));
      } else {
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
      }
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }
}
