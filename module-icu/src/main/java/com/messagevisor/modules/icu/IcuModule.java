package com.messagevisor.modules.icu;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import com.messagevisor.sdk.MessagevisorFormatPayload;
import com.messagevisor.sdk.MessagevisorFormatters;
import com.messagevisor.sdk.MessagevisorModule;
import com.messagevisor.sdk.MessagevisorModuleApi;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IcuModule implements MessagevisorModule {
  private final String name;

  public IcuModule() {
    this("icu");
  }

  public IcuModule(String name) {
    this.name = name == null || name.isBlank() ? "icu" : name;
  }

  public static IcuModule create() {
    return new IcuModule();
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Object format(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
    if (!(payload.translation() instanceof String translation)) {
      return null;
    }

    PreparedMessage prepared = new PresetPreprocessor(payload, api).prepare(translation);
    Map<String, Object> values = new LinkedHashMap<>();
    if (payload.values() != null) {
      values.putAll(payload.values());
    }
    values.putAll(prepared.syntheticValues);

    MessageFormat messageFormat = new MessageFormat(prepared.pattern, ULocale.forLanguageTag(payload.locale()));
    return messageFormat.format(values);
  }

  private static final class PresetPreprocessor {
    private final MessagevisorFormatPayload payload;
    private final MessagevisorModuleApi api;
    private int syntheticIndex;

    PresetPreprocessor(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
      this.payload = payload;
      this.api = api;
    }

    PreparedMessage prepare(String pattern) {
      Map<String, Object> syntheticValues = new LinkedHashMap<>();
      String transformed = transform(pattern, syntheticValues);
      return new PreparedMessage(transformed, syntheticValues);
    }

    private String transform(String pattern, Map<String, Object> syntheticValues) {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < pattern.length(); ) {
        char ch = pattern.charAt(i);
        if (ch == '\'') {
          int next = i + 1;
          out.append(ch);
          if (next < pattern.length()) {
            out.append(pattern.charAt(next));
            i += 2;
          } else {
            i++;
          }
          continue;
        }
        if (ch != '{') {
          out.append(ch);
          i++;
          continue;
        }
        int end = matchingBrace(pattern, i);
        if (end < 0) {
          out.append(pattern.substring(i));
          break;
        }
        out.append(transformPlaceholder(pattern.substring(i + 1, end), syntheticValues));
        i = end + 1;
      }
      return out.toString();
    }

    private String transformPlaceholder(String content, Map<String, Object> syntheticValues) {
      String[] parts = splitTopLevel(content, 3);
      if (parts.length < 3) {
        return "{" + content + "}";
      }

      String argumentName = parts[0].trim();
      String kind = parts[1].trim();
      String style = parts[2].trim();

      Map<String, Object> preset = preset(kind, style);
      if (preset != null) {
        Object value = payload.values() == null ? null : payload.values().get(argumentName);
        if (value == null) {
          return "{" + content + "}";
        }
        String syntheticName = "__messagevisor_icu_" + syntheticIndex++;
        syntheticValues.put(syntheticName, formatPreset(kind, value, preset));
        return "{" + syntheticName + "}";
      }

      if ("plural".equals(kind) || "selectordinal".equals(kind) || "select".equals(kind)) {
        return "{" + argumentName + ", " + kind + ", " + transformChoiceBody(style, syntheticValues) + "}";
      }

      return "{" + content + "}";
    }

    private String transformChoiceBody(String body, Map<String, Object> syntheticValues) {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < body.length(); ) {
        char ch = body.charAt(i);
        if (ch != '{') {
          out.append(ch);
          i++;
          continue;
        }
        int end = matchingBrace(body, i);
        if (end < 0) {
          out.append(body.substring(i));
          break;
        }
        out.append('{').append(transform(body.substring(i + 1, end), syntheticValues)).append('}');
        i = end + 1;
      }
      return out.toString();
    }

    private Map<String, Object> preset(String kind, String name) {
      if (name == null || name.isBlank() || payload.formats() == null) {
        return null;
      }
      return switch (kind) {
        case "number" -> payload.formats().getNumber().get(name);
        case "date" -> payload.formats().getDate().get(name);
        case "time" -> payload.formats().getTime().get(name);
        default -> null;
      };
    }

    private String formatPreset(String kind, Object value, Map<String, Object> preset) {
      reportFormatterDiagnostics(kind, preset);
      return switch (kind) {
        case "number" -> MessagevisorFormatters.formatNumber(value, payload.locale(), preset, payload.currency());
        case "date" -> MessagevisorFormatters.formatDate(value, payload.locale(), preset, payload.timeZone());
        case "time" -> MessagevisorFormatters.formatTime(value, payload.locale(), preset, payload.timeZone());
        default -> String.valueOf(value);
      };
    }

    private void reportFormatterDiagnostics(String kind, Map<String, Object> preset) {
      for (String message : MessagevisorFormatters.formatterLimitations(kind, preset)) {
        api.reportDiagnostic(
            com.messagevisor.sdk.MessagevisorModuleReportedDiagnostic.builder(
                    com.messagevisor.sdk.LogLevel.WARN, "unsupported_formatter", message)
                .detail("locale", payload.locale())
                .detail("messageKey", payload.messageKey())
                .detail("source", payload.source())
                .build());
      }
    }
  }

  private record PreparedMessage(String pattern, Map<String, Object> syntheticValues) {}

  private static int matchingBrace(String value, int start) {
    int depth = 0;
    for (int i = start; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '\'') {
        i++;
        continue;
      }
      if (ch == '{') {
        depth++;
      } else if (ch == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static String[] splitTopLevel(String value, int limit) {
    java.util.List<String> parts = new java.util.ArrayList<>();
    int depth = 0;
    int last = 0;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '\'') {
        i++;
        continue;
      }
      if (ch == '{') {
        depth++;
      } else if (ch == '}') {
        depth--;
      } else if (ch == ',' && depth == 0 && parts.size() < limit - 1) {
        parts.add(value.substring(last, i).trim());
        last = i + 1;
      }
    }
    parts.add(value.substring(last).trim());
    return parts.toArray(String[]::new);
  }
}
