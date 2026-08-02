package com.messagevisor.sdk;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConditionEvaluator {
  private static final Object MISSING = new Object();
  private static final Pattern PORTABLE_ISO_DATE_TIME =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?(?:Z|[+-]\\d{2}:\\d{2})$");
  private static final Pattern COUNTED_POSSESSIVE_PREFIX =
      Pattern.compile("^\\{\\d+(?:,\\d*)?\\}\\+");
  private ConditionEvaluator() {}

  public static boolean evaluateCondition(Object condition, EvaluateOptions options) {
    if (condition == null || Objects.equals(condition, "*")) {
      return true;
    }
    if (condition instanceof List<?> list) {
      return list.stream().allMatch(item -> evaluateCondition(item, options));
    }
    if (condition instanceof String string) {
      Object parsed = parseStructuredString(string);
      return parsed != string && evaluateCondition(parsed, options);
    }
    if (!(condition instanceof Map<?, ?> raw)) {
      return false;
    }
    Map<String, Object> map = ObjectMaps.stringMap(raw);
    if (map.containsKey("and")) {
      return asList(map.get("and")).stream().allMatch(item -> evaluateCondition(item, options));
    }
    if (map.containsKey("or")) {
      return asList(map.get("or")).stream().anyMatch(item -> evaluateCondition(item, options));
    }
    if (map.containsKey("not")) {
      return !asList(map.get("not")).stream().allMatch(item -> evaluateCondition(item, options));
    }
    if (map.containsKey("feature")) {
      boolean enabled =
          options.resolveFlag() != null
              && options.resolveFlag().resolve(String.valueOf(map.get("feature")), options.context());
      return switch (String.valueOf(map.get("operator"))) {
        case "isEnabled" -> enabled;
        case "isDisabled" -> !enabled;
        default -> false;
      };
    }
    if (map.containsKey("experiment")) {
      String variation =
          options.resolveVariation() == null
              ? null
              : options
                  .resolveVariation()
                  .resolve(String.valueOf(map.get("experiment")), options.context());
      return Objects.equals("hasVariation", map.get("operator"))
          && Objects.equals(variation, map.get("value"));
    }

    Object value = contextValue(options.context(), String.valueOf(map.get("attribute")));
    Object expected = map.get("value");
    return switch (String.valueOf(map.get("operator"))) {
      case "equals" -> Objects.equals(value, expected);
      case "notEquals" -> !Objects.equals(value, expected);
      case "exists" -> value != MISSING;
      case "notExists" -> value == MISSING;
      case "greaterThan" -> compareNumbers(value, expected, comparison -> comparison > 0);
      case "greaterThanOrEquals" -> compareNumbers(value, expected, comparison -> comparison >= 0);
      case "lessThan" -> compareNumbers(value, expected, comparison -> comparison < 0);
      case "lessThanOrEquals" -> compareNumbers(value, expected, comparison -> comparison <= 0);
      case "contains" -> value instanceof String actual && expected instanceof String wanted && actual.contains(wanted);
      case "notContains" -> value instanceof String actual && expected instanceof String wanted && !actual.contains(wanted);
      case "startsWith" -> value instanceof String actual && expected instanceof String wanted && actual.startsWith(wanted);
      case "endsWith" -> value instanceof String actual && expected instanceof String wanted && actual.endsWith(wanted);
      case "matches" -> matches(value, expected, map.get("regexFlags"), false);
      case "notMatches" -> matches(value, expected, map.get("regexFlags"), true);
      case "before" -> compareDate(value, expected, comparison -> comparison < 0);
      case "after" -> compareDate(value, expected, comparison -> comparison > 0);
      case "includes" -> value instanceof List<?> list && list.contains(expected);
      case "notIncludes" -> value instanceof List<?> list && !list.contains(expected);
      case "in" -> expected instanceof List<?> list && list.contains(value);
      case "notIn" -> expected instanceof List<?> list && !list.contains(value);
      default -> false;
    };
  }

  public static boolean evaluateGroupSegment(Object groupSegment, EvaluateOptions options) {
    if (groupSegment == null || Objects.equals(groupSegment, "*")) {
      return true;
    }
    if (groupSegment instanceof List<?> list) {
      return list.stream().allMatch(item -> evaluateGroupSegment(item, options));
    }
    if (groupSegment instanceof String string) {
      Object parsed = parseStructuredString(string);
      if (parsed != string) {
        return evaluateGroupSegment(parsed, options);
      }
      return evaluateSegment(string, options);
    }
    if (!(groupSegment instanceof Map<?, ?> raw)) {
      return false;
    }
    Map<String, Object> map = ObjectMaps.stringMap(raw);
    if (map.containsKey("and")) {
      return asList(map.get("and")).stream().allMatch(item -> evaluateGroupSegment(item, options));
    }
    if (map.containsKey("or")) {
      return asList(map.get("or")).stream().anyMatch(item -> evaluateGroupSegment(item, options));
    }
    if (map.containsKey("not")) {
      return !asList(map.get("not")).stream().allMatch(item -> evaluateGroupSegment(item, options));
    }
    return false;
  }

  public static boolean evaluateSegment(String segmentKey, EvaluateOptions options) {
    Segment segment = options.segments().get(segmentKey);
    return segment != null
        && !segment.isArchived()
        && evaluateCondition(segment.getConditions(), options);
  }

  private static Object parseStructuredString(String value) {
    if (!(value.startsWith("{") || value.startsWith("["))) {
      return value;
    }
    try {
      return JsonSupport.MAPPER.readValue(value, Object.class);
    } catch (Exception ignored) {
      return value;
    }
  }

  private static List<?> asList(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private static Object contextValue(Map<String, Object> context, String attribute) {
    Object current = context;
    for (String part : attribute.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      if (!map.containsKey(part)) return MISSING;
      current = map.get(part);
    }
    return current;
  }

  private static boolean compareNumbers(
      Object value, Object expected, java.util.function.IntPredicate predicate) {
    if (!(value instanceof Number actual) || !(expected instanceof Number wanted)) return false;
    return predicate.test(Double.compare(actual.doubleValue(), wanted.doubleValue()));
  }

  private static boolean compareDate(
      Object value, Object expected, java.util.function.IntPredicate predicate) {
    try {
      return predicate.test(parseInstant(value).compareTo(parseInstant(expected)));
    } catch (Exception error) {
      return false;
    }
  }

  private static Instant parseInstant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof Date date) return date.toInstant();
    String text = String.valueOf(value);
    if (!PORTABLE_ISO_DATE_TIME.matcher(text).matches()) throw new IllegalArgumentException("Invalid portable date");
    return OffsetDateTime.parse(text).toInstant();
  }

  private static boolean matches(Object value, Object expected, Object flagsValue, boolean negate) {
    if (!(value instanceof String actual) || !(expected instanceof String expression)) return false;
    String flags = flagsValue == null ? "" : String.valueOf(flagsValue);
    if (!flags.matches("[imsu]*") || flags.chars().distinct().count() != flags.length()) return false;
    if (!isPortableRegexSyntax(expression)) return false;
    int patternFlags = 0;
    if (flags.contains("i")) patternFlags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    if (flags.contains("m")) patternFlags |= Pattern.MULTILINE;
    if (flags.contains("s")) patternFlags |= Pattern.DOTALL;
    if (flags.contains("u")) patternFlags |= Pattern.UNICODE_CHARACTER_CLASS;
    try {
      boolean matched = Pattern.compile(expression, patternFlags).matcher(actual).find();
      return negate ? !matched : matched;
    } catch (PatternSyntaxException error) {
      return false;
    }
  }

  private static boolean isPortableRegexSyntax(String expression) {
    boolean inCharacterClass = false;
    for (int index = 0; index < expression.length(); index++) {
      char character = expression.charAt(index);
      if (character == '\\') {
        if (index + 1 < expression.length()) {
          char escaped = expression.charAt(index + 1);
          if (!inCharacterClass
              && ((escaped >= '1' && escaped <= '9')
                  || ((escaped == 'k' || escaped == 'g')
                      && index + 2 < expression.length()
                      && (expression.charAt(index + 2) == '<'
                          || expression.charAt(index + 2) == '\'')))) {
            return false;
          }
          index++;
        }
        continue;
      }
      if (character == '[' && !inCharacterClass) {
        inCharacterClass = true;
        continue;
      }
      if (character == ']' && inCharacterClass) {
        inCharacterClass = false;
        continue;
      }
      if (inCharacterClass) continue;
      if (character == '(' && index + 1 < expression.length() && expression.charAt(index + 1) == '?') {
        return false;
      }
      if ((character == '?' || character == '*' || character == '+')
          && index + 1 < expression.length()
          && expression.charAt(index + 1) == '+') {
        return false;
      }
      if (character == '{'
          && COUNTED_POSSESSIVE_PREFIX.matcher(expression.substring(index)).find()) {
        return false;
      }
    }
    return true;
  }

  public record EvaluateOptions(
      Map<String, Object> context,
      Map<String, Segment> segments,
      FlagResolver resolveFlag,
      VariationResolver resolveVariation) {
    public EvaluateOptions {
      context = context == null ? Map.of() : context;
      segments = segments == null ? Map.of() : segments;
    }
  }
}
