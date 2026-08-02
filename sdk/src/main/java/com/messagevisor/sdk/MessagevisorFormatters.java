package com.messagevisor.sdk;

import com.ibm.icu.number.FormattedNumber;
import com.ibm.icu.number.FractionPrecision;
import com.ibm.icu.number.IntegerWidth;
import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.Notation;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.number.Precision;
import com.ibm.icu.number.Scale;
import com.ibm.icu.text.ConstrainedFieldPosition;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DateIntervalFormat;
import com.ibm.icu.text.DateTimePatternGenerator;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.RelativeDateTimeFormatter;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.DateInterval;
import com.ibm.icu.util.MeasureUnit;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Format;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Portable formatter adapters backed by ICU4J locale data. */
public final class MessagevisorFormatters {
  private MessagevisorFormatters() {}

  public static String formatNumber(
      Object value, String locale, Map<String, Object> options, String runtimeCurrency) {
    BigDecimal number = toBigDecimal(value);
    if (number == null) {
      return String.valueOf(value);
    }
    try {
      return numberFormatter(number, locale, options, runtimeCurrency).format(number).toString();
    } catch (AssertionError error) {
      if ("currency".equals(options.get("style")) && "name".equals(options.get("currencyDisplay"))) {
        return formatCurrencyName(number, locale, options, runtimeCurrency);
      }
      throw error;
    }
  }

  public static List<FormatPart> formatNumberToParts(
      Object value, String locale, Map<String, Object> options, String runtimeCurrency) {
    BigDecimal number = toBigDecimal(value);
    if (number == null) {
      return singlePart("literal", String.valueOf(value));
    }
    FormattedNumber formatted = numberFormatter(number, locale, options, runtimeCurrency).format(number);
    return numberParts(formatted);
  }

  public static String formatDate(
      Object value, String locale, Map<String, Object> options, String runtimeTimeZone) {
    Date date = toDate(value);
    if (date == null) {
      return String.valueOf(value);
    }
    return dateFormatter(locale, options, runtimeTimeZone, false).format(date);
  }

  public static List<FormatPart> formatDateToParts(
      Object value, String locale, Map<String, Object> options, String runtimeTimeZone) {
    return singlePart("literal", formatDate(value, locale, options, runtimeTimeZone));
  }

  public static String formatTime(
      Object value, String locale, Map<String, Object> options, String runtimeTimeZone) {
    Date date = toDate(value);
    if (date == null) {
      return String.valueOf(value);
    }
    return dateFormatter(locale, options, runtimeTimeZone, true).format(date);
  }

  public static List<FormatPart> formatTimeToParts(
      Object value, String locale, Map<String, Object> options, String runtimeTimeZone) {
    return singlePart("literal", formatTime(value, locale, options, runtimeTimeZone));
  }

  public static String formatDateTimeRange(
      Object start,
      Object end,
      String locale,
      Map<String, Object> options,
      String runtimeTimeZone) {
    Date startDate = toDate(start);
    Date endDate = toDate(end);
    if (startDate == null || endDate == null) {
      return String.valueOf(start) + " - " + end;
    }
    Map<String, Object> resolved = safeOptions(options);
    DateIntervalFormat formatter =
        DateIntervalFormat.getInstance(dateTimeSkeleton(resolved), dateLocale(locale, resolved));
    formatter.setTimeZone(TimeZone.getTimeZone(resolveTimeZone(resolved, runtimeTimeZone)));
    return formatter.format(new DateInterval(startDate.getTime(), endDate.getTime()));
  }

  public static String formatRelativeTime(
      double value, String unit, String locale, Map<String, Object> options) {
    Map<String, Object> resolved = safeOptions(options);
    RelativeDateTimeFormatter.Style style =
        switch (String.valueOf(resolved.getOrDefault("style", "long"))) {
          case "short" -> RelativeDateTimeFormatter.Style.SHORT;
          case "narrow" -> RelativeDateTimeFormatter.Style.NARROW;
          default -> RelativeDateTimeFormatter.Style.LONG;
        };
    RelativeDateTimeFormatter formatter =
        RelativeDateTimeFormatter.getInstance(
            ULocale.forLanguageTag(locale), null, style, DisplayContext.CAPITALIZATION_NONE);
    String normalizedUnit = unit.replaceAll("s$", "");
    RelativeDateTimeFormatter.RelativeUnit relativeUnit = relativeUnit(normalizedUnit);
    if ("auto".equals(resolved.getOrDefault("numeric", "always"))) {
      RelativeDateTimeFormatter.AbsoluteUnit absoluteUnit = absoluteUnit(normalizedUnit);
      if (absoluteUnit != null) {
        if (value == -1) return formatter.format(RelativeDateTimeFormatter.Direction.LAST, absoluteUnit);
        if (value == 0) return formatter.format(RelativeDateTimeFormatter.Direction.THIS, absoluteUnit);
        if (value == 1) return formatter.format(RelativeDateTimeFormatter.Direction.NEXT, absoluteUnit);
      }
    }
    return formatter.format(
        Math.abs(value),
        value < 0
            ? RelativeDateTimeFormatter.Direction.LAST
            : RelativeDateTimeFormatter.Direction.NEXT,
        relativeUnit);
  }

  public static List<FormatPart> formatRelativeTimeToParts(
      double value, String unit, String locale, Map<String, Object> options) {
    return singlePart("literal", formatRelativeTime(value, unit, locale, options));
  }

  public static String formatPlural(double value, String locale, boolean ordinal) {
    PluralRules rules =
        PluralRules.forLocale(
            ULocale.forLanguageTag(locale),
            ordinal ? PluralRules.PluralType.ORDINAL : PluralRules.PluralType.CARDINAL);
    return rules.select(value);
  }

  public static String formatList(List<String> values, String locale, Map<String, Object> options) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return ListFormatter.getInstance(
            ULocale.forLanguageTag(locale), listType(options), listWidth(options))
        .format(values);
  }

  public static List<FormatPart> formatListToParts(
      List<String> values, String locale, Map<String, Object> options) {
    List<FormatPart> parts = new ArrayList<>();
    if (values != null) {
      for (String value : values) {
        parts.add(new FormatPart("element", value));
      }
    }
    return parts.isEmpty() ? singlePart("literal", "") : parts;
  }

  public static String formatDisplayName(String value, String locale, Map<String, Object> options) {
    Map<String, Object> resolvedOptions = safeOptions(options);
    ULocale resolvedLocale = ULocale.forLanguageTag(locale);
    LocaleDisplayNames names = LocaleDisplayNames.getInstance(resolvedLocale);
    String type = ObjectMaps.stringOption(resolvedOptions, "type");
    String resolved =
        switch (type == null ? "" : type) {
          case "language" -> names.languageDisplayName(value);
          case "region" -> names.regionDisplayName(value);
          case "script" -> names.scriptDisplayName(value);
          case "currency" -> Currency.getInstance(value).getName(resolvedLocale, Currency.LONG_NAME, null);
          default -> names.localeDisplayName(value);
        };
    if ((resolved == null || resolved.equals(value)) && "none".equals(resolvedOptions.get("fallback"))) {
      return null;
    }
    return resolved == null ? value : resolved;
  }

  public static List<String> formatterLimitations(String family, Map<String, Object> options) {
    Map<String, Object> resolved = safeOptions(options);
    Set<String> messages = new LinkedHashSet<>();
    if ((family.equals("date") || family.equals("time") || family.equals("dateTimeRange"))
        && resolved.containsKey("formatMatcher")) {
      messages.add("Java uses ICU locale pattern matching for formatMatcher.");
    }
    if (family.equals("list")) {
      messages.add("Java formatListToParts returns simplified element parts.");
    }
    if (family.equals("displayName")
        && (resolved.containsKey("languageDisplay") || resolved.containsKey("style"))) {
      messages.add("Java display names use ICU locale display-name behavior.");
    }
    if (family.equals("parts")) {
      messages.add("Java date/time parts use a simplified portable representation.");
    }
    return new ArrayList<>(messages);
  }

  private static LocalizedNumberFormatter numberFormatter(
      BigDecimal number, String locale, Map<String, Object> options, String runtimeCurrency) {
    Map<String, Object> resolved = safeOptions(options);
    String style = ObjectMaps.stringOption(resolved, "style");
    com.ibm.icu.number.UnlocalizedNumberFormatter formatter = NumberFormatter.with();

    String notation = ObjectMaps.stringOption(resolved, "notation");
    formatter =
        formatter.notation(
            switch (notation == null ? "standard" : notation) {
              case "scientific" -> Notation.scientific();
              case "engineering" -> Notation.engineering();
              case "compact" ->
                  "long".equals(ObjectMaps.stringOption(resolved, "compactDisplay"))
                      ? Notation.compactLong()
                      : Notation.compactShort();
              default -> Notation.simple();
            });

    if ("currency".equals(style)) {
      formatter = formatter.unit(Currency.getInstance(resolveCurrency(resolved, runtimeCurrency)));
      formatter = formatter.unitWidth(currencyWidth(resolved));
    } else if ("percent".equals(style)) {
      formatter = formatter.unit(MeasureUnit.PERCENT).scale(Scale.powerOfTen(2));
    } else if ("unit".equals(style)) {
      String unit = ObjectMaps.stringOption(resolved, "unit");
      if (unit != null && !unit.isBlank()) {
        formatter = formatter.unit(MeasureUnit.forIdentifier(unit)).unitWidth(unitWidth(resolved));
      }
    }

    formatter = formatter.grouping(grouping(resolved));
    formatter = formatter.sign(signDisplay(resolved));
    if (resolved.containsKey("minimumIntegerDigits")) {
      formatter =
          formatter.integerWidth(
              IntegerWidth.zeroFillTo(ObjectMaps.intOption(resolved, "minimumIntegerDigits", 1)));
    }

    Precision precision = precision(resolved, style);
    if (precision != null) {
      formatter = formatter.precision(precision);
    }
    formatter = formatter.roundingMode(roundingMode(resolved, number));
    return formatter.locale(numberLocale(locale, resolved));
  }

  private static Precision precision(Map<String, Object> options, String style) {
    boolean hasMinimumFraction = options.containsKey("minimumFractionDigits");
    boolean hasMaximumFraction = options.containsKey("maximumFractionDigits");
    boolean hasMinimumSignificant = options.containsKey("minimumSignificantDigits");
    boolean hasMaximumSignificant = options.containsKey("maximumSignificantDigits");
    int minimumFraction = ObjectMaps.intOption(options, "minimumFractionDigits", 0);
    int defaultMaximumFraction = "percent".equals(style) ? 0 : 3;
    int maximumFraction =
        ObjectMaps.intOption(
            options,
            "maximumFractionDigits",
            Math.max(minimumFraction, defaultMaximumFraction));
    int minimumSignificant = ObjectMaps.intOption(options, "minimumSignificantDigits", 1);
    int maximumSignificant = ObjectMaps.intOption(options, "maximumSignificantDigits", 21);

    Precision result;
    int increment = ObjectMaps.intOption(options, "roundingIncrement", 0);
    if (increment > 0) {
      result = Precision.increment(BigDecimal.valueOf(increment).movePointLeft(maximumFraction));
    } else if (hasMinimumSignificant || hasMaximumSignificant) {
      if ((hasMinimumFraction || hasMaximumFraction)
          && !"auto".equals(ObjectMaps.stringOption(options, "roundingPriority"))) {
        FractionPrecision fraction = Precision.minMaxFraction(minimumFraction, maximumFraction);
        NumberFormatter.RoundingPriority priority =
            "lessPrecision".equals(ObjectMaps.stringOption(options, "roundingPriority"))
                ? NumberFormatter.RoundingPriority.STRICT
                : NumberFormatter.RoundingPriority.RELAXED;
        result = fraction.withSignificantDigits(minimumSignificant, maximumSignificant, priority);
      } else {
        result = Precision.minMaxSignificantDigits(minimumSignificant, maximumSignificant);
      }
    } else if (hasMinimumFraction || hasMaximumFraction) {
      result = Precision.minMaxFraction(minimumFraction, maximumFraction);
    } else if ("currency".equals(style)) {
      result = Precision.currency(Currency.CurrencyUsage.STANDARD);
    } else if ("compact".equals(ObjectMaps.stringOption(options, "notation"))) {
      result = null;
    } else {
      result = Precision.minMaxFraction(0, defaultMaximumFraction);
    }

    if (result != null
        && "stripIfInteger".equals(ObjectMaps.stringOption(options, "trailingZeroDisplay"))) {
      result = result.trailingZeroDisplay(NumberFormatter.TrailingZeroDisplay.HIDE_IF_WHOLE);
    }
    return result;
  }

  private static String formatCurrencyName(
      BigDecimal number, String locale, Map<String, Object> options, String runtimeCurrency) {
    NumberFormat formatter =
        NumberFormat.getInstance(numberLocale(locale, options), NumberFormat.PLURALCURRENCYSTYLE);
    formatter.setCurrency(Currency.getInstance(resolveCurrency(options, runtimeCurrency)));
    formatter.setGroupingUsed(!Boolean.FALSE.equals(options.get("useGrouping")));
    formatter.setMinimumIntegerDigits(ObjectMaps.intOption(options, "minimumIntegerDigits", 1));
    if (options.containsKey("minimumFractionDigits")) {
      formatter.setMinimumFractionDigits(ObjectMaps.intOption(options, "minimumFractionDigits", 0));
    }
    if (options.containsKey("maximumFractionDigits")) {
      formatter.setMaximumFractionDigits(ObjectMaps.intOption(options, "maximumFractionDigits", 3));
    }
    formatter.setRoundingMode(roundingMode(options, number).ordinal());
    return formatter.format(number);
  }

  private static NumberFormatter.GroupingStrategy grouping(Map<String, Object> options) {
    Object value = options.get("useGrouping");
    if (Boolean.FALSE.equals(value) || "false".equals(String.valueOf(value))) {
      return NumberFormatter.GroupingStrategy.OFF;
    }
    return switch (String.valueOf(value)) {
      case "min2" -> NumberFormatter.GroupingStrategy.MIN2;
      case "always" -> NumberFormatter.GroupingStrategy.ON_ALIGNED;
      default -> NumberFormatter.GroupingStrategy.AUTO;
    };
  }

  private static NumberFormatter.SignDisplay signDisplay(Map<String, Object> options) {
    String sign = ObjectMaps.stringOption(options, "signDisplay");
    boolean accounting = "accounting".equals(ObjectMaps.stringOption(options, "currencySign"));
    if (accounting) {
      return switch (sign == null ? "auto" : sign) {
        case "always" -> NumberFormatter.SignDisplay.ACCOUNTING_ALWAYS;
        case "exceptZero" -> NumberFormatter.SignDisplay.ACCOUNTING_EXCEPT_ZERO;
        case "negative" -> NumberFormatter.SignDisplay.ACCOUNTING_NEGATIVE;
        case "never" -> NumberFormatter.SignDisplay.NEVER;
        default -> NumberFormatter.SignDisplay.ACCOUNTING;
      };
    }
    return switch (sign == null ? "auto" : sign) {
      case "always" -> NumberFormatter.SignDisplay.ALWAYS;
      case "exceptZero" -> NumberFormatter.SignDisplay.EXCEPT_ZERO;
      case "negative" -> NumberFormatter.SignDisplay.NEGATIVE;
      case "never" -> NumberFormatter.SignDisplay.NEVER;
      default -> NumberFormatter.SignDisplay.AUTO;
    };
  }

  private static RoundingMode roundingMode(Map<String, Object> options, BigDecimal number) {
    return switch (String.valueOf(options.getOrDefault("roundingMode", "halfExpand"))) {
      case "ceil" -> RoundingMode.CEILING;
      case "floor" -> RoundingMode.FLOOR;
      case "expand" -> RoundingMode.UP;
      case "trunc" -> RoundingMode.DOWN;
      case "halfCeil" -> number.signum() < 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP;
      case "halfFloor" -> number.signum() < 0 ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN;
      case "halfTrunc" -> RoundingMode.HALF_DOWN;
      case "halfEven" -> RoundingMode.HALF_EVEN;
      default -> RoundingMode.HALF_UP;
    };
  }

  private static NumberFormatter.UnitWidth currencyWidth(Map<String, Object> options) {
    return switch (String.valueOf(options.getOrDefault("currencyDisplay", "symbol"))) {
      case "code" -> NumberFormatter.UnitWidth.ISO_CODE;
      case "narrowSymbol" -> NumberFormatter.UnitWidth.NARROW;
      case "name" -> NumberFormatter.UnitWidth.FULL_NAME;
      default -> NumberFormatter.UnitWidth.SHORT;
    };
  }

  private static NumberFormatter.UnitWidth unitWidth(Map<String, Object> options) {
    return switch (String.valueOf(options.getOrDefault("unitDisplay", "short"))) {
      case "narrow" -> NumberFormatter.UnitWidth.NARROW;
      case "long" -> NumberFormatter.UnitWidth.FULL_NAME;
      default -> NumberFormatter.UnitWidth.SHORT;
    };
  }

  private static DateFormat dateFormatter(
      String locale, Map<String, Object> options, String runtimeTimeZone, boolean time) {
    Map<String, Object> resolved = safeOptions(options);
    ULocale resolvedLocale = dateLocale(locale, resolved);
    DateFormat formatter;
    if (time && resolved.containsKey("timeStyle")) {
      formatter = DateFormat.getTimeInstance(style(resolved, "timeStyle"), resolvedLocale);
    } else if (!time && resolved.containsKey("dateStyle")) {
      formatter = DateFormat.getDateInstance(style(resolved, "dateStyle"), resolvedLocale);
    } else {
      String skeleton = time ? timeSkeleton(resolved) : dateSkeleton(resolved);
      if (skeleton.isEmpty()) {
        formatter = DateFormat.getDateInstance(DateFormat.DEFAULT, resolvedLocale);
      } else {
        String pattern =
            DateTimePatternGenerator.getInstance(resolvedLocale)
                .getBestPattern(skeleton, DateTimePatternGenerator.MATCH_HOUR_FIELD_LENGTH);
        pattern = preserveRequestedHourCycle(pattern, resolved);
        formatter = new SimpleDateFormat(pattern, resolvedLocale);
      }
    }
    formatter.setTimeZone(TimeZone.getTimeZone(resolveTimeZone(resolved, runtimeTimeZone)));
    return formatter;
  }

  private static String dateSkeleton(Map<String, Object> options) {
    StringBuilder skeleton = new StringBuilder();
    appendWidth(skeleton, options, "era", "G", "GGG", "GGGG", "GGGGG");
    appendWidth(skeleton, options, "year", "y", "yy", "y", "y");
    appendWidth(skeleton, options, "month", "M", "MM", "MMMM", "MMMMM", "MMM");
    appendWidth(skeleton, options, "day", "d", "dd", "d", "d");
    appendWidth(skeleton, options, "weekday", "E", "E", "EEEE", "EEEEE", "EEE");
    return skeleton.toString();
  }

  private static String timeSkeleton(Map<String, Object> options) {
    StringBuilder skeleton = new StringBuilder();
    if (options.containsKey("hour")) {
      String width = ObjectMaps.stringOption(options, "hour");
      String hour = hourSymbol(options);
      skeleton.append("2-digit".equals(width) ? hour.repeat(2) : hour);
    }
    appendWidth(skeleton, options, "minute", "m", "mm", "m", "m");
    appendWidth(skeleton, options, "second", "s", "ss", "s", "s");
    if (options.containsKey("fractionalSecondDigits")) {
      skeleton.append("S".repeat(Math.max(1, Math.min(3, ObjectMaps.intOption(options, "fractionalSecondDigits", 3)))));
    }
    if (options.containsKey("dayPeriod")) {
      String width = ObjectMaps.stringOption(options, "dayPeriod");
      skeleton.append("narrow".equals(width) ? "BBBBB" : "long".equals(width) ? "BBBB" : "B");
    }
    String zone = ObjectMaps.stringOption(options, "timeZoneName");
    if (zone != null) {
      skeleton.append(
          switch (zone) {
            case "long" -> "zzzz";
            case "shortOffset" -> "O";
            case "longOffset" -> "OOOO";
            case "shortGeneric" -> "v";
            case "longGeneric" -> "vvvv";
            default -> "z";
          });
    }
    return skeleton.toString();
  }

  private static String dateTimeSkeleton(Map<String, Object> options) {
    if (options.containsKey("dateStyle") || options.containsKey("timeStyle")) {
      StringBuilder skeleton = new StringBuilder();
      String dateStyle = ObjectMaps.stringOption(options, "dateStyle");
      String timeStyle = ObjectMaps.stringOption(options, "timeStyle");
      if (dateStyle != null) {
        skeleton.append("full".equals(dateStyle) ? "yMMMMEEEEd" : "long".equals(dateStyle) ? "yMMMMd" : "yMd");
      }
      if (timeStyle != null) {
        skeleton.append("short".equals(timeStyle) ? "jm" : "jms");
      }
      return skeleton.toString();
    }
    return dateSkeleton(options) + timeSkeleton(options);
  }

  private static void appendWidth(
      StringBuilder output,
      Map<String, Object> options,
      String key,
      String numeric,
      String twoDigit,
      String longWidth,
      String narrow) {
    appendWidth(output, options, key, numeric, twoDigit, longWidth, narrow, numeric);
  }

  private static void appendWidth(
      StringBuilder output,
      Map<String, Object> options,
      String key,
      String numeric,
      String twoDigit,
      String longWidth,
      String narrow,
      String shortWidth) {
    if (!options.containsKey(key)) return;
    output.append(
        switch (String.valueOf(options.get(key))) {
          case "2-digit" -> twoDigit;
          case "long" -> longWidth;
          case "short" -> shortWidth;
          case "narrow" -> narrow;
          default -> numeric;
        });
  }

  private static String hourSymbol(Map<String, Object> options) {
    String cycle = ObjectMaps.stringOption(options, "hourCycle");
    if (cycle != null) {
      return switch (cycle) {
        case "h11" -> "K";
        case "h12" -> "h";
        case "h23" -> "H";
        case "h24" -> "k";
        default -> "j";
      };
    }
    if (options.containsKey("hour12")) {
      return ObjectMaps.boolOption(options, "hour12", true) ? "h" : "H";
    }
    return "j";
  }

  private static String preserveRequestedHourCycle(
      String pattern, Map<String, Object> options) {
    if (!options.containsKey("hourCycle") && !options.containsKey("hour12")) {
      return pattern;
    }
    String symbol = hourSymbol(options);
    int width = "2-digit".equals(ObjectMaps.stringOption(options, "hour")) ? 2 : 1;
    StringBuilder result = new StringBuilder();
    boolean quoted = false;
    for (int index = 0; index < pattern.length(); ) {
      char current = pattern.charAt(index);
      if (current == '\'') {
        quoted = !quoted;
        result.append(current);
        index++;
      } else if (!quoted && "hHKkj".indexOf(current) >= 0) {
        while (index < pattern.length() && pattern.charAt(index) == current) {
          index++;
        }
        result.append(symbol.repeat(width));
      } else {
        result.append(current);
        index++;
      }
    }
    return result.toString();
  }

  private static int style(Map<String, Object> options, String key) {
    return switch (String.valueOf(options.getOrDefault(key, "medium"))) {
      case "full" -> DateFormat.FULL;
      case "long" -> DateFormat.LONG;
      case "short" -> DateFormat.SHORT;
      default -> DateFormat.MEDIUM;
    };
  }

  private static String resolveCurrency(Map<String, Object> options, String runtimeCurrency) {
    String presetCurrency = ObjectMaps.stringOption(options, "currency");
    if (presetCurrency != null && !presetCurrency.isBlank()) {
      return presetCurrency;
    }
    return runtimeCurrency == null || runtimeCurrency.isBlank() ? "USD" : runtimeCurrency;
  }

  private static String resolveTimeZone(Map<String, Object> options, String runtimeTimeZone) {
    String formatTimeZone = ObjectMaps.stringOption(options, "timeZone");
    if (formatTimeZone != null && !formatTimeZone.isBlank()) {
      return formatTimeZone;
    }
    return runtimeTimeZone == null || runtimeTimeZone.isBlank()
        ? TimeZone.getDefault().getID()
        : runtimeTimeZone;
  }

  private static ULocale numberLocale(String locale, Map<String, Object> options) {
    return localeWithKeywords(locale, options, false);
  }

  private static ULocale dateLocale(String locale, Map<String, Object> options) {
    return localeWithKeywords(locale, options, true);
  }

  private static ULocale localeWithKeywords(
      String locale, Map<String, Object> options, boolean includeCalendar) {
    ULocale.Builder builder = new ULocale.Builder().setLanguageTag(locale);
    String numberingSystem = ObjectMaps.stringOption(options, "numberingSystem");
    if (numberingSystem != null && !numberingSystem.isBlank()) {
      builder.setUnicodeLocaleKeyword("nu", numberingSystem);
    }
    if (includeCalendar) {
      String calendar = ObjectMaps.stringOption(options, "calendar");
      if (calendar != null && !calendar.isBlank()) {
        builder.setUnicodeLocaleKeyword("ca", calendar);
      }
    }
    return builder.build();
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal decimal) return decimal;
    if (value instanceof Number number) return new BigDecimal(number.toString());
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (Exception error) {
      return null;
    }
  }

  private static Date toDate(Object value) {
    if (value instanceof Date date) return date;
    if (value instanceof Number number) return new Date(number.longValue());
    if (value instanceof Instant instant) return Date.from(instant);
    try {
      return Date.from(Instant.parse(String.valueOf(value)));
    } catch (Exception error) {
      return null;
    }
  }

  private static List<FormatPart> numberParts(FormattedNumber value) {
    List<FormatPart> parts = new ArrayList<>();
    ConstrainedFieldPosition position = new ConstrainedFieldPosition();
    int cursor = 0;
    while (value.nextPosition(position)) {
      if (position.getStart() > cursor) {
        parts.add(new FormatPart("literal", value.subSequence(cursor, position.getStart()).toString()));
      }
      parts.add(
          new FormatPart(
              numberFieldName(position.getField()),
              value.subSequence(position.getStart(), position.getLimit()).toString()));
      cursor = position.getLimit();
    }
    if (cursor < value.length()) {
      parts.add(new FormatPart("literal", value.subSequence(cursor, value.length()).toString()));
    }
    return parts.isEmpty() ? singlePart("literal", value.toString()) : parts;
  }

  private static String numberFieldName(Format.Field field) {
    if (field == com.ibm.icu.text.NumberFormat.Field.INTEGER) return "integer";
    if (field == com.ibm.icu.text.NumberFormat.Field.FRACTION) return "fraction";
    if (field == com.ibm.icu.text.NumberFormat.Field.DECIMAL_SEPARATOR) return "decimal";
    if (field == com.ibm.icu.text.NumberFormat.Field.GROUPING_SEPARATOR) return "group";
    if (field == com.ibm.icu.text.NumberFormat.Field.CURRENCY) return "currency";
    if (field == com.ibm.icu.text.NumberFormat.Field.PERCENT) return "percentSign";
    if (field == com.ibm.icu.text.NumberFormat.Field.SIGN) return "sign";
    if (field == com.ibm.icu.text.NumberFormat.Field.EXPONENT) return "exponentInteger";
    return "literal";
  }

  private static RelativeDateTimeFormatter.RelativeUnit relativeUnit(String unit) {
    return switch (unit) {
      case "second" -> RelativeDateTimeFormatter.RelativeUnit.SECONDS;
      case "minute" -> RelativeDateTimeFormatter.RelativeUnit.MINUTES;
      case "hour" -> RelativeDateTimeFormatter.RelativeUnit.HOURS;
      case "week" -> RelativeDateTimeFormatter.RelativeUnit.WEEKS;
      case "month" -> RelativeDateTimeFormatter.RelativeUnit.MONTHS;
      case "year" -> RelativeDateTimeFormatter.RelativeUnit.YEARS;
      default -> RelativeDateTimeFormatter.RelativeUnit.DAYS;
    };
  }

  private static RelativeDateTimeFormatter.AbsoluteUnit absoluteUnit(String unit) {
    return switch (unit) {
      case "minute" -> RelativeDateTimeFormatter.AbsoluteUnit.MINUTE;
      case "hour" -> RelativeDateTimeFormatter.AbsoluteUnit.HOUR;
      case "day" -> RelativeDateTimeFormatter.AbsoluteUnit.DAY;
      case "week" -> RelativeDateTimeFormatter.AbsoluteUnit.WEEK;
      case "month" -> RelativeDateTimeFormatter.AbsoluteUnit.MONTH;
      case "year" -> RelativeDateTimeFormatter.AbsoluteUnit.YEAR;
      default -> null;
    };
  }

  private static ListFormatter.Type listType(Map<String, Object> options) {
    String type = ObjectMaps.stringOption(safeOptions(options), "type");
    return switch (type == null ? "" : type) {
      case "disjunction" -> ListFormatter.Type.OR;
      case "unit" -> ListFormatter.Type.UNITS;
      default -> ListFormatter.Type.AND;
    };
  }

  private static ListFormatter.Width listWidth(Map<String, Object> options) {
    String style = ObjectMaps.stringOption(safeOptions(options), "style");
    return switch (style == null ? "" : style) {
      case "short" -> ListFormatter.Width.SHORT;
      case "narrow" -> ListFormatter.Width.NARROW;
      default -> ListFormatter.Width.WIDE;
    };
  }

  private static Map<String, Object> safeOptions(Map<String, Object> options) {
    return options == null ? Map.of() : options;
  }

  private static List<FormatPart> singlePart(String type, String value) {
    return List.of(new FormatPart(type, value));
  }
}
