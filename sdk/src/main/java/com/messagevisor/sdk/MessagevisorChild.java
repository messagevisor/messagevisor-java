package com.messagevisor.sdk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Request-scoped Messagevisor instance.
 *
 * <p>Children share their parent's datafiles, modules, and formatting configuration, while owning their
 * locale, context, currency, time zone, resolvers, events, and lifecycle. Root-only operations such
 * as replacing datafiles or managing modules are intentionally not exposed.
 */
public final class MessagevisorChild implements AutoCloseable {
  private final Messagevisor delegate;

  MessagevisorChild(Messagevisor delegate) {
    this.delegate = delegate;
  }

  public MessagevisorUnsubscribe subscribe(Runnable callback) { return delegate.subscribe(callback); }
  public MessagevisorUnsubscribe on(EventName eventName, MessagevisorEventCallback callback) { return delegate.on(eventName, callback); }
  public MessagevisorSnapshot getSnapshot() { return delegate.getSnapshot(); }
  public void setLogLevel(LogLevel logLevel) { delegate.setLogLevel(logLevel); }
  public void setFlagResolver(FlagResolver resolver) { delegate.setFlagResolver(resolver); }
  public void setVariationResolver(VariationResolver resolver) { delegate.setVariationResolver(resolver); }
  public void setCurrency(String currency) { delegate.setCurrency(currency); }
  public String getCurrency() { return delegate.getCurrency(); }
  public void setTimeZone(String timeZone) { delegate.setTimeZone(timeZone); }
  public String getTimeZone() { return delegate.getTimeZone(); }
  public void setContext(Map<String, Object> context) { delegate.setContext(context); }
  public void setContext(Map<String, Object> context, boolean replace) { delegate.setContext(context, replace); }
  public Map<String, Object> getContext() { return delegate.getContext(); }
  public void setLocale(String locale) { delegate.setLocale(locale); }
  public String getLocale() { return delegate.getLocale(); }
  public String getDirection() { return delegate.getDirection(); }
  public String getDirection(String locale) { return delegate.getDirection(locale); }
  public DatafileContent getDatafile() { return delegate.getDatafile(); }
  public DatafileContent getDatafile(String locale) { return delegate.getDatafile(locale); }
  public String getRevision() { return delegate.getRevision(); }
  public String getRevision(String locale) { return delegate.getRevision(locale); }
  public Map<String, String> getDefaultTranslations(String locale) { return delegate.getDefaultTranslations(locale); }
  public FormatPresets getDefaultFormats(String locale) { return delegate.getDefaultFormats(locale); }
  public String getRawTranslation(String messageKey) { return delegate.getRawTranslation(messageKey); }
  public String getRawTranslation(String messageKey, TranslateOptions options) { return delegate.getRawTranslation(messageKey, options); }
  public String translate(String messageKey) { return delegate.translate(messageKey); }
  public String translate(String messageKey, Map<String, Object> values) { return delegate.translate(messageKey, values); }
  public String t(String messageKey, Map<String, Object> values, TranslateOptions options) { return delegate.t(messageKey, values, options); }
  public String translate(String messageKey, Map<String, Object> values, TranslateOptions options) { return delegate.translate(messageKey, values, options); }
  public String formatMessage(String message, Map<String, Object> values) { return delegate.formatMessage(message, values); }
  public String formatMessage(String message, Map<String, Object> values, EvaluationOptions options) { return delegate.formatMessage(message, values, options); }
  public String formatNumber(double value) { return delegate.formatNumber(value); }
  public String formatNumber(double value, String preset) { return delegate.formatNumber(value, preset); }
  public String formatNumber(double value, String preset, EvaluationOptions options) { return delegate.formatNumber(value, preset, options); }
  public String formatNumber(double value, Map<String, Object> format) { return delegate.formatNumber(value, format); }
  public String formatNumber(double value, Map<String, Object> format, EvaluationOptions options) { return delegate.formatNumber(value, format, options); }
  public List<FormatPart> formatNumberToParts(double value, String preset) { return delegate.formatNumberToParts(value, preset); }
  public List<FormatPart> formatNumberToParts(double value, String preset, EvaluationOptions options) { return delegate.formatNumberToParts(value, preset, options); }
  public List<FormatPart> formatNumberToParts(double value, Map<String, Object> format, EvaluationOptions options) { return delegate.formatNumberToParts(value, format, options); }
  public String formatDate(Object value, String preset) { return delegate.formatDate(value, preset); }
  public String formatDate(Object value, String preset, EvaluationOptions options) { return delegate.formatDate(value, preset, options); }
  public String formatDate(Object value, Map<String, Object> format) { return delegate.formatDate(value, format); }
  public String formatDate(Object value, Map<String, Object> format, EvaluationOptions options) { return delegate.formatDate(value, format, options); }
  public List<FormatPart> formatDateToParts(Object value, String preset, EvaluationOptions options) { return delegate.formatDateToParts(value, preset, options); }
  public String formatTime(Object value, String preset) { return delegate.formatTime(value, preset); }
  public String formatTime(Object value, String preset, EvaluationOptions options) { return delegate.formatTime(value, preset, options); }
  public String formatTime(Object value, Map<String, Object> format) { return delegate.formatTime(value, format); }
  public String formatTime(Object value, Map<String, Object> format, EvaluationOptions options) { return delegate.formatTime(value, format, options); }
  public List<FormatPart> formatTimeToParts(Object value, String preset, EvaluationOptions options) { return delegate.formatTimeToParts(value, preset, options); }
  public String formatDateTimeRange(Object start, Object end, String preset) { return delegate.formatDateTimeRange(start, end, preset); }
  public String formatDateTimeRange(Object start, Object end, String preset, EvaluationOptions options) { return delegate.formatDateTimeRange(start, end, preset, options); }
  public String formatDateTimeRange(Object start, Object end, Map<String, Object> format, EvaluationOptions options) { return delegate.formatDateTimeRange(start, end, format, options); }
  public String formatRelativeTime(double value, String unit, String preset) { return delegate.formatRelativeTime(value, unit, preset); }
  public String formatRelativeTime(double value, String unit, String preset, EvaluationOptions options) { return delegate.formatRelativeTime(value, unit, preset, options); }
  public List<FormatPart> formatRelativeTimeToParts(double value, String unit, String preset) { return delegate.formatRelativeTimeToParts(value, unit, preset); }
  public List<FormatPart> formatRelativeTimeToParts(double value, String unit, String preset, EvaluationOptions options) { return delegate.formatRelativeTimeToParts(value, unit, preset, options); }
  public String formatPlural(double value) { return delegate.formatPlural(value); }
  public String formatPlural(double value, boolean ordinal) { return delegate.formatPlural(value, ordinal); }
  public String formatPlural(double value, EvaluationOptions options) { return delegate.formatPlural(value, options); }
  public String formatPlural(double value, boolean ordinal, EvaluationOptions options) { return delegate.formatPlural(value, ordinal, options); }
  public String formatList(List<String> values) { return delegate.formatList(values); }
  public String formatList(List<String> values, Map<String, Object> options) { return delegate.formatList(values, options); }
  public List<FormatPart> formatListToParts(List<String> values) { return delegate.formatListToParts(values); }
  public List<FormatPart> formatListToParts(List<String> values, Map<String, Object> options) { return delegate.formatListToParts(values, options); }
  public String formatDisplayName(String value, Map<String, Object> options) { return delegate.formatDisplayName(value, options); }

  @Override
  public void close() { delegate.close(); }
  public CompletableFuture<Void> closeAsync() { return delegate.closeAsync(); }
}
