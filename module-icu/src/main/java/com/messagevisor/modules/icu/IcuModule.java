package com.messagevisor.modules.icu;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.MessagePattern;
import com.ibm.icu.text.MessagePatternUtil;
import com.ibm.icu.text.MessagePatternUtil.ArgNode;
import com.ibm.icu.text.MessagePatternUtil.MessageNode;
import com.ibm.icu.text.MessagePatternUtil.TextNode;
import com.ibm.icu.text.MessagePatternUtil.VariantNode;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import com.messagevisor.sdk.MessagevisorFormatPayload;
import com.messagevisor.sdk.MessagevisorFormatters;
import com.messagevisor.sdk.MessagevisorModule;
import com.messagevisor.sdk.MessagevisorModuleApi;
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
    if (!(payload.translation() instanceof String translation)) return null;
    // ICU owns syntax and apostrophe parsing. Only the selected message is evaluated,
    // so unused branches cannot require values or invoke formatters and diagnostics.
    MessageNode message = MessagePatternUtil.buildMessageNode(translation);
    return new Evaluator(payload, api).render(message, null);
  }

  private static final class Evaluator {
    private final MessagevisorFormatPayload payload;
    private final MessagevisorModuleApi api;
    private final ULocale locale;

    Evaluator(MessagevisorFormatPayload payload, MessagevisorModuleApi api) {
      this.payload = payload;
      this.api = api;
      this.locale = ULocale.forLanguageTag(payload.locale());
    }

    private String render(MessageNode message, Double pluralNumber) {
      StringBuilder result = new StringBuilder();
      for (var node : message.getContents()) {
        switch (node.getType()) {
          case TEXT -> result.append(((TextNode) node).getText());
          case REPLACE_NUMBER -> result.append(NumberFormat.getInstance(locale).format(pluralNumber));
          case ARG -> result.append(argument((ArgNode) node, pluralNumber));
        }
      }
      return result.toString();
    }

    private String argument(ArgNode argument, Double pluralNumber) {
      String key = argument.getName();
      if (payload.values() == null || !payload.values().containsKey(key)) {
        throw new IllegalArgumentException("Missing ICU argument: " + key);
      }
      Object value = payload.values().get(key);
      return switch (argument.getArgType()) {
        case NONE -> value == null || Boolean.FALSE.equals(value) ? "" : String.valueOf(value);
        case SELECT -> render(select(argument, String.valueOf(value), null).getMessage(), pluralNumber);
        case PLURAL, SELECTORDINAL -> {
          double number = value instanceof Number numeric ? numeric.doubleValue()
              : value == null ? 0 : Double.parseDouble(String.valueOf(value));
          double adjusted = number - argument.getComplexStyle().getOffset();
          String category = MessagevisorFormatters.formatPlural(adjusted, payload.locale(),
              argument.getArgType() == MessagePattern.ArgType.SELECTORDINAL);
          yield render(select(argument, category, number).getMessage(), adjusted);
        }
        case SIMPLE -> formatSimple(argument, value);
        default -> throw new IllegalArgumentException("Unsupported ICU argument type: " + argument.getArgType());
      };
    }

    private VariantNode select(ArgNode argument, String selector, Double exactNumber) {
      VariantNode matched = null;
      VariantNode fallback = null;
      for (VariantNode variant : argument.getComplexStyle().getVariants()) {
        if (variant.isSelectorNumeric()) {
          if (exactNumber != null && variant.getSelectorValue() == exactNumber) return variant;
        } else if (variant.getSelector().equals(selector)) {
          matched = variant;
        } else if (variant.getSelector().equals("other")) {
          fallback = variant;
        }
      }
      if (matched != null) return matched;
      if (fallback != null) return fallback;
      throw new IllegalArgumentException("Missing ICU other branch: " + argument.getName());
    }

    private String formatSimple(ArgNode argument, Object value) {
      String kind = argument.getTypeName();
      String style = argument.getSimpleStyle();
      if (style != null) style = style.trim();
      Map<String, Object> preset = preset(kind, style);
      if (preset != null) return formatPreset(kind, value, preset);

      // Synthetic arguments contain values, never reprocessed message syntax.
      String pattern = "{value," + kind + (style == null ? "" : "," + style) + "}";
      MessageFormat format = new MessageFormat(pattern, locale);
      for (java.text.Format formatter : format.getFormats()) {
        if (formatter instanceof NumberFormat numberFormat) {
          numberFormat.setRoundingMode(java.math.RoundingMode.HALF_UP.ordinal());
        }
      }
      if ("date".equals(kind) || "time".equals(kind)) {
        TimeZone zone = payload.timeZone() == null ? TimeZone.getDefault() : TimeZone.getTimeZone(payload.timeZone());
        if (TimeZone.UNKNOWN_ZONE_ID.equals(zone.getID())) {
          throw new IllegalArgumentException("Unknown time zone: " + payload.timeZone());
        }
        for (java.text.Format formatter : format.getFormats()) {
          if (formatter instanceof DateFormat dateFormat) dateFormat.setTimeZone(zone);
        }
        if (value instanceof java.time.Instant instant) value = java.util.Date.from(instant);
        else if (value instanceof String string) value = java.util.Date.from(java.time.Instant.parse(string));
      }
      return format.format(Map.of("value", value == null ? 0 : value));
    }

    private Map<String, Object> preset(String kind, String name) {
      if (name == null || name.isBlank() || payload.formats() == null) return null;
      return switch (kind) {
        case "number" -> payload.formats().getNumber().get(name);
        case "date" -> payload.formats().getDate().get(name);
        case "time" -> payload.formats().getTime().get(name);
        default -> null;
      };
    }

    private String formatPreset(String kind, Object value, Map<String, Object> preset) {
      for (String message : MessagevisorFormatters.formatterLimitations(kind, preset)) {
        api.reportDiagnostic(
            com.messagevisor.sdk.MessagevisorModuleReportedDiagnostic.builder(
                    com.messagevisor.sdk.LogLevel.WARN, "unsupported_formatter", message)
                .detail("locale", payload.locale())
                .detail("messageKey", payload.messageKey())
                .detail("source", payload.source())
                .build());
      }
      return switch (kind) {
        case "number" -> MessagevisorFormatters.formatNumber(value, payload.locale(), preset, payload.currency());
        case "date" -> MessagevisorFormatters.formatDate(value, payload.locale(), preset, payload.timeZone());
        case "time" -> MessagevisorFormatters.formatTime(value, payload.locale(), preset, payload.timeZone());
        default -> String.valueOf(value);
      };
    }
  }
}
