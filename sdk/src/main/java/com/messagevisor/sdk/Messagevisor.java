package com.messagevisor.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class Messagevisor implements AutoCloseable {
  private final Map<String, DatafileContent> datafiles;
  private final Map<String, Map<String, String>> defaultTranslationsByLocale;
  private final Map<String, FormatPresets> defaultFormatsByLocale;
  private final Map<EventName, List<MessagevisorEventCallback>> listeners = new EnumMap<>(EventName.class);
  private final List<MessagevisorModule> modules = new CopyOnWriteArrayList<>();
  private final List<ModuleDiagnosticSubscription> moduleDiagnosticSubscriptions = new CopyOnWriteArrayList<>();
  private final Map<String, MessagevisorModuleApi> moduleApis = new ConcurrentHashMap<>();
  private final Map<MessagevisorModule, String> anonymousModuleApiKeys =
      Collections.synchronizedMap(new java.util.IdentityHashMap<>());
  private final List<ModuleFlagResolver> moduleFlagResolvers = new CopyOnWriteArrayList<>();
  private final List<ModuleVariationResolver> moduleVariationResolvers = new CopyOnWriteArrayList<>();
  private final List<MessagevisorUnsubscribe> parentUnsubscribers = new CopyOnWriteArrayList<>();
  private final Object moduleLock = new Object();
  private final Object contextLock = new Object();
  private final Object parentEventLock = new Object();
  private final Messagevisor parent;
  private volatile Map<String, Object> context;
  private volatile String locale;
  private volatile String currency;
  private volatile String timeZone;
  private volatile FlagResolver resolveFlag;
  private volatile VariationResolver resolveVariation;
  private volatile boolean hasOwnFlagResolver;
  private volatile boolean hasOwnVariationResolver;
  private volatile MessagevisorDiagnosticHandler onDiagnostic;
  private volatile LogLevel logLevel;
  private final AtomicInteger version = new AtomicInteger();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicInteger moduleApiId = new AtomicInteger();
  private volatile List<String> observedParentDatafileLocales = List.of();
  private volatile Map<String, String> observedParentDatafileRevisions = Map.of();
  private volatile String observedParentDirection;

  public Messagevisor() {
    this(MessagevisorOptions.builder().build());
  }

  public Messagevisor(MessagevisorOptions options) {
    this(options, null);
  }

  private Messagevisor(MessagevisorOptions options, Messagevisor parent) {
    this.parent = parent;
    this.datafiles = parent == null ? new ConcurrentHashMap<>() : parent.datafiles;
    this.defaultTranslationsByLocale = parent == null ? options.defaultTranslations() : parent.defaultTranslationsByLocale;
    this.defaultFormatsByLocale = parent == null ? options.defaultFormats() : parent.defaultFormatsByLocale;
    this.context = options.context();
    this.locale = options.locale();
    this.currency = options.currency();
    this.timeZone = options.timeZone();
    this.resolveFlag = options.resolveFlag();
    this.resolveVariation = options.resolveVariation();
    this.hasOwnFlagResolver = parent == null && options.resolveFlag() != null;
    this.hasOwnVariationResolver = parent == null && options.resolveVariation() != null;
    this.onDiagnostic = options.onDiagnostic();
    this.logLevel = options.logLevel();
    for (EventName eventName : EventName.values()) {
      listeners.put(eventName, new CopyOnWriteArrayList<>());
    }
    if (parent == null) for (MessagevisorModule module : options.modules()) addModule(module);
    if (options.datafile() != null) {
      setDatafile(options.datafile());
    }
    if (parent != null) {
      captureObservedParentDatafileState();
      trackParentSubscription(parent.on(EventName.DATAFILE_SET, this::forwardParentDatafileEvent));
    }
    if (parent == null) reportDiagnostic(
        MessagevisorDiagnostic.builder(LogLevel.INFO, "sdk_initialized", "SDK initialized").build());
  }

  public static Messagevisor create(MessagevisorOptions options) {
    return new Messagevisor(options);
  }

  public static Messagevisor create() {
    return new Messagevisor();
  }

  public MessagevisorUnsubscribe subscribe(Runnable callback) {
    return on(EventName.CHANGE, ignored -> callback.run());
  }

  public MessagevisorChild spawn(Map<String, Object> context) {
    return spawn(context, new MessagevisorSpawnOptions(null, null, null));
  }

  public MessagevisorChild spawn(Map<String, Object> context, MessagevisorSpawnOptions options) {
    MessagevisorSpawnOptions resolved = options == null ? new MessagevisorSpawnOptions(null, null, null) : options;
    MessagevisorOptions childOptions = MessagevisorOptions.builder()
        .context(shallowMerge(this.context, context == null ? Map.of() : context))
        .locale(resolved.locale() == null ? this.locale : resolved.locale())
        .currency(resolved.currency() == null ? this.currency : resolved.currency())
        .timeZone(resolved.timeZone() == null ? this.timeZone : resolved.timeZone())
        .logLevel(this.logLevel)
        .onDiagnostic(this.onDiagnostic)
        .build();
    return new MessagevisorChild(new Messagevisor(childOptions, this));
  }

  public void setLogLevel(LogLevel logLevel) {
    this.logLevel = logLevel == null ? LogLevel.INFO : logLevel;
  }

  public MessagevisorUnsubscribe on(EventName eventName, MessagevisorEventCallback callback) {
    if (closed.get()) {
      return () -> {};
    }
    return addLocalListener(eventName, callback);
  }

  private void captureObservedParentDatafileState() {
    MessagevisorSnapshot snapshot = getSnapshot();
    observedParentDatafileLocales = List.copyOf(snapshot.datafileLocales());
    observedParentDatafileRevisions = Map.copyOf(snapshot.datafileRevisionsByLocale());
    observedParentDirection = snapshot.direction();
  }

  private void forwardParentDatafileEvent(MessagevisorEvent event) {
    synchronized (parentEventLock) {
      if (closed.get()) return;
      MessagevisorSnapshot current = getSnapshot();
      MessagevisorSnapshot previous =
          new MessagevisorSnapshot(
              version.get(),
              current.locale(),
              observedParentDirection,
              current.context(),
              current.currency(),
              current.timeZone(),
              observedParentDatafileLocales,
              observedParentDatafileRevisions);
      emit(
          EventName.DATAFILE_SET,
          previous,
          EventDetails.builder()
              .datafile(event.datafile())
              .locale(event.locale())
              .activeLocale(locale)
              .previousLocale(locale)
              .replaced(event.replaced())
              .build());
      captureObservedParentDatafileState();
    }
  }

  private MessagevisorUnsubscribe addLocalListener(
      EventName eventName, MessagevisorEventCallback callback) {
    List<MessagevisorEventCallback> callbacks = listeners.get(eventName);
    if (!callbacks.contains(callback)) {
      callbacks.add(callback);
    }
    return () -> callbacks.remove(callback);
  }

  private MessagevisorUnsubscribe trackParentSubscription(MessagevisorUnsubscribe parentUnsubscribe) {
    AtomicBoolean active = new AtomicBoolean(true);
    final MessagevisorUnsubscribe[] tracked = new MessagevisorUnsubscribe[1];
    tracked[0] = () -> {
      if (!active.compareAndSet(true, false)) return;
      parentUnsubscribe.unsubscribe();
      parentUnsubscribers.remove(tracked[0]);
    };
    parentUnsubscribers.add(tracked[0]);
    return tracked[0];
  }

  public MessagevisorSnapshot getSnapshot() {
    Map<String, String> revisions = new LinkedHashMap<>();
    for (Map.Entry<String, DatafileContent> entry : datafiles.entrySet()) {
      revisions.put(entry.getKey(), entry.getValue().getRevision());
    }
    DatafileContent current = locale == null ? null : datafiles.get(locale);
    return new MessagevisorSnapshot(
        version.get(),
        locale,
        current == null ? null : current.getDirection(),
        CloneUtils.deepCopyMap(context),
        currency,
        timeZone,
        new ArrayList<>(datafiles.keySet()),
        revisions);
  }

  public MessagevisorUnsubscribe addModule(MessagevisorModule module) {
    if (closed.get()) {
      return () -> {};
    }
    synchronized (moduleLock) {
      String name = module.name();
      if (name != null
          && modules.stream().anyMatch(current -> Objects.equals(current.name(), name))) {
        reportDiagnostic(
            MessagevisorDiagnostic.builder(LogLevel.ERROR, "duplicate_module", "Duplicate module name")
                .moduleName(name)
                .build());
        return () -> {};
      }
      if (parent != null) {
        throw new UnsupportedOperationException(
            "Modules are managed by the parent Messagevisor instance.");
      }
      try {
        module.setup(getModuleApi(module));
      } catch (RuntimeException error) {
        clearModuleDiagnosticSubscriptions(module);
        reportDiagnostic(
            MessagevisorDiagnostic.builder(
                    LogLevel.ERROR, "module_setup_error", "Module setup failed")
                .moduleName(module.name())
                .originalError(error)
                .build());
        try {
          module.close();
        } catch (Exception ignored) {
          // Setup already failed; the setup diagnostic remains the primary failure.
        }
        return () -> {};
      }
      modules.add(module);
      AtomicBoolean removed = new AtomicBoolean(false);
      return () -> {
        if (removed.compareAndSet(false, true)) removeModuleInstance(module);
      };
    }
  }

  public void removeModule(String name) {
    if (closed.get()) {
      return;
    }

    if (parent != null) throw new UnsupportedOperationException("Modules are managed by the parent Messagevisor instance.");
    synchronized (moduleLock) {
      List<MessagevisorModule> removed =
          modules.stream().filter(module -> Objects.equals(module.name(), name)).toList();
      for (MessagevisorModule module : removed) {
        removeModuleInstance(module);
      }
    }
  }

  private void removeModuleInstance(MessagevisorModule module) {
    if (!modules.remove(module)) return;
    clearModuleDiagnosticSubscriptions(module);
    try {
      module.close();
    } catch (Exception error) {
      reportDiagnostic(MessagevisorDiagnostic.builder(LogLevel.ERROR, "module_close_error", "Module close failed")
          .moduleName(module.name()).originalError(error).build());
      throw new MessagevisorCloseException("One or more Messagevisor modules failed to close.", List.of(error));
    }
  }

  public void setFlagResolver(FlagResolver resolver) {
    this.resolveFlag = resolver;
    this.hasOwnFlagResolver = true;
  }

  public void setVariationResolver(VariationResolver resolver) {
    this.resolveVariation = resolver;
    this.hasOwnVariationResolver = true;
  }

  private FlagResolver getFlagResolver() {
    if (!moduleFlagResolvers.isEmpty()) return moduleFlagResolvers.get(moduleFlagResolvers.size() - 1).resolver();
    if (hasOwnFlagResolver) return resolveFlag;
    return parent == null ? null : parent.getFlagResolver();
  }

  private VariationResolver getVariationResolver() {
    if (!moduleVariationResolvers.isEmpty()) return moduleVariationResolvers.get(moduleVariationResolvers.size() - 1).resolver();
    if (hasOwnVariationResolver) return resolveVariation;
    return parent == null ? null : parent.getVariationResolver();
  }

  public void setCurrency(String currency) {
    MessagevisorSnapshot previousSnapshot = getSnapshot();
    String previousCurrency = this.currency;
    this.currency = currency;
    emit(EventName.CURRENCY_SET, previousSnapshot, EventDetails.builder().currency(currency).previousCurrency(previousCurrency).build());
  }

  public String getCurrency() {
    return currency;
  }

  public void setTimeZone(String timeZone) {
    MessagevisorSnapshot previousSnapshot = getSnapshot();
    String previousTimeZone = this.timeZone;
    this.timeZone = timeZone;
    emit(EventName.TIME_ZONE_SET, previousSnapshot, EventDetails.builder().timeZone(timeZone).previousTimeZone(previousTimeZone).build());
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setDatafile(Object datafile) {
    setDatafile(datafile, false);
  }

  public void setDatafile(Object datafile, boolean replace) {
    DatafileContent incoming;
    try {
      incoming = JsonSupport.parseDatafile(datafile);
      if (incoming.getLocale() == null || incoming.getLocale().isBlank()) {
        throw new IllegalArgumentException("Datafile must include locale.");
      }
    } catch (RuntimeException error) {
      reportDiagnostic(
          MessagevisorDiagnostic.builder(LogLevel.ERROR, "invalid_datafile", "could not parse datafile")
              .originalError(error)
              .build());
      return;
    }
    MessagevisorSnapshot previousSnapshot = getSnapshot();
    String previousLocale = locale;
    DatafileContent stored = datafiles.compute(
        incoming.getLocale(),
        (ignored, existing) -> !replace && existing != null ? mergeStoredDatafile(existing, incoming) : incoming);
    if (locale == null) {
      locale = stored.getLocale();
    }
    emit(
        EventName.DATAFILE_SET,
        previousSnapshot,
        EventDetails.builder()
            .datafile(stored)
            .locale(stored.getLocale())
            .activeLocale(locale)
            .previousLocale(previousLocale)
            .replaced(replace)
            .build());
  }

  public void setContext(Map<String, Object> context) {
    setContext(context, false);
  }

  public void setContext(Map<String, Object> context, boolean replace) {
    MessagevisorSnapshot previousSnapshot;
    Map<String, Object> previousContext;
    synchronized (contextLock) {
      previousSnapshot = getSnapshot();
      previousContext = CloneUtils.deepCopyMap(this.context);
      this.context =
          replace
              ? CloneUtils.deepCopyMap(context)
              : shallowMerge(this.context, context == null ? Map.of() : context);
    }
    emit(
        EventName.CONTEXT_SET,
        previousSnapshot,
        EventDetails.builder()
            .context(CloneUtils.deepCopyMap(this.context))
            .previousContext(previousContext)
            .replaced(replace)
            .build());
  }

  public Map<String, Object> getContext() {
    return CloneUtils.deepCopyMap(context);
  }

  public void setLocale(String locale) {
    if (!datafiles.containsKey(locale)) {
      throw new MessagevisorException("Datafile not found for locale: " + locale);
    }
    MessagevisorSnapshot previousSnapshot = getSnapshot();
    String previousLocale = this.locale;
    this.locale = locale;
    emit(
        EventName.LOCALE_SET,
        previousSnapshot,
        EventDetails.builder().locale(locale).previousLocale(previousLocale).build());
  }

  public String getLocale() {
    return locale;
  }

  public String getDirection() {
    return getDirection(locale);
  }

  public String getDirection(String locale) {
    return locale == null ? null : getDatafile(locale).getDirection();
  }

  public DatafileContent getDatafile() {
    return getDatafile(locale);
  }

  public DatafileContent getDatafile(String locale) {
    if (locale == null) {
      reportDiagnostic(
          MessagevisorDiagnostic.builder(
                  LogLevel.ERROR, "missing_locale", "Datafile not found: no locale is set")
              .detail("locale", this.locale)
              .build());
      throw new MessagevisorException("Datafile not found: no locale is set");
    }
    DatafileContent datafile = datafiles.get(locale);
    if (datafile == null) {
      reportDiagnostic(
          MessagevisorDiagnostic.builder(LogLevel.ERROR, "missing_datafile", "Datafile not found for locale")
              .detail("locale", locale)
              .build());
      throw new MessagevisorException("Datafile not found for locale: " + locale);
    }
    return datafile;
  }

  public String getRevision() {
    return getRevision(locale);
  }

  public String getRevision(String locale) {
    return getDatafile(locale).getRevision();
  }

  public Map<String, String> getDefaultTranslations(String locale) {
    return locale == null ? null : defaultTranslationsByLocale.get(locale);
  }

  public FormatPresets getDefaultFormats(String locale) {
    return locale == null ? null : defaultFormatsByLocale.get(locale);
  }

  public String getRawTranslation(String messageKey) {
    return getRawTranslation(messageKey, TranslateOptions.empty());
  }

  public String getRawTranslation(String messageKey, TranslateOptions options) {
    return resolveMessage(messageKey, options.defaultTranslation(), options).formatted();
  }

  public String translate(String messageKey) {
    return translate(messageKey, Map.of(), TranslateOptions.empty());
  }

  public String translate(String messageKey, Map<String, Object> values) {
    return translate(messageKey, values, TranslateOptions.empty());
  }

  public String t(String messageKey, Map<String, Object> values, TranslateOptions options) {
    return translate(messageKey, values, options);
  }

  public String translate(String messageKey, Map<String, Object> values, TranslateOptions options) {
    String rawMessage = getRawTranslation(messageKey, options);
    String currentLocale = getCurrentLocale(options);
    FormatPresets formats = getEvaluationFormats(options);
    Map<String, Object> meta = getMessageMeta(messageKey, currentLocale);
    Object formatted =
        runFormats(
            rawMessage,
            values,
            new MessagevisorFormatPayload(
                rawMessage,
                values,
                currentLocale,
                "translation",
                messageKey,
                meta,
                formats,
                options.moduleOptions(),
                options.currency(),
                options.timeZone()));
    Object transformed =
        runTransforms(
            formatted,
            new MessagevisorTransformPayload(
                formatted, currentLocale, "translation", messageKey, meta));
    return String.valueOf(transformed);
  }

  public String formatMessage(String message, Map<String, Object> values) {
    return formatMessage(message, values, EvaluationOptions.builder().build());
  }

  public String formatMessage(String message, Map<String, Object> values, EvaluationOptions options) {
    String currentLocale = getCurrentLocale(options);
    FormatPresets formats = getEvaluationFormats(options);
    Object formatted =
        runFormats(
            message,
            values,
            new MessagevisorFormatPayload(
                message,
                values,
                currentLocale,
                "formatMessage",
                null,
                null,
                formats,
                options.moduleOptions(),
                options.currency(),
                options.timeZone()));
    Object transformed =
        runTransforms(
            formatted,
            new MessagevisorTransformPayload(formatted, currentLocale, "formatMessage", null, null));
    return String.valueOf(transformed);
  }

  public String formatNumber(double value, String preset) {
    return formatNumber(value, preset, EvaluationOptions.builder().build());
  }

  public String formatNumber(double value) {
    return formatNumber(value, (Map<String, Object>) null, EvaluationOptions.builder().build());
  }

  public String formatNumber(double value, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("number", preset, getEvaluationFormats(options).getNumber(), options);
    reportFormatterDiagnostics("number", format, "formatNumber", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withCurrencyOption(format, options);
    return formatSafely("number", currentLocale, resolved,
        () -> MessagevisorFormatters.formatNumber(value, currentLocale, resolved, currency));
  }

  public String formatNumber(double value, Map<String, Object> format) {
    return formatNumber(value, format, EvaluationOptions.builder().build());
  }

  public String formatNumber(double value, Map<String, Object> format, EvaluationOptions options) {
    reportFormatterDiagnostics("number", format, "formatNumber", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withCurrencyOption(format, options);
    return formatSafely("number", currentLocale, resolved,
        () -> MessagevisorFormatters.formatNumber(value, currentLocale, resolved, currency));
  }

  public List<FormatPart> formatNumberToParts(double value, String preset) {
    return formatNumberToParts(value, preset, EvaluationOptions.builder().build());
  }

  public List<FormatPart> formatNumberToParts(double value, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("number", preset, getEvaluationFormats(options).getNumber(), options);
    reportFormatterDiagnostics("number", format, "formatNumberToParts", null);
    reportFormatterDiagnostics("parts", format, "formatNumberToParts", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withCurrencyOption(format, options);
    return formatSafely("number", currentLocale, resolved,
        () -> MessagevisorFormatters.formatNumberToParts(value, currentLocale, resolved, currency));
  }

  public List<FormatPart> formatNumberToParts(double value, Map<String, Object> format, EvaluationOptions options) {
    reportFormatterDiagnostics("number", format, "formatNumberToParts", null);
    reportFormatterDiagnostics("parts", format, "formatNumberToParts", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withCurrencyOption(format, options);
    return formatSafely("number", currentLocale, resolved,
        () -> MessagevisorFormatters.formatNumberToParts(value, currentLocale, resolved, currency));
  }

  public String formatDate(Object value, String preset) {
    return formatDate(value, preset, EvaluationOptions.builder().build());
  }

  public String formatDate(Object value, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("date", preset, getEvaluationFormats(options).getDate(), options);
    reportFormatterDiagnostics("date", format, "formatDate", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("date", currentLocale, resolved,
        () -> MessagevisorFormatters.formatDate(value, currentLocale, resolved, timeZone));
  }

  public String formatDate(Object value, Map<String, Object> format) {
    return formatDate(value, format, EvaluationOptions.builder().build());
  }

  public String formatDate(Object value, Map<String, Object> format, EvaluationOptions options) {
    reportFormatterDiagnostics("date", format, "formatDate", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("date", currentLocale, resolved,
        () -> MessagevisorFormatters.formatDate(value, currentLocale, resolved, timeZone));
  }

  public List<FormatPart> formatDateToParts(Object value, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("date", preset, getEvaluationFormats(options).getDate(), options);
    reportFormatterDiagnostics("date", format, "formatDateToParts", null);
    reportFormatterDiagnostics("parts", format, "formatDateToParts", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("date", currentLocale, resolved,
        () -> MessagevisorFormatters.formatDateToParts(value, currentLocale, resolved, timeZone));
  }

  public String formatTime(Object value, String preset) {
    return formatTime(value, preset, EvaluationOptions.builder().build());
  }

  public String formatTime(Object value, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("time", preset, getEvaluationFormats(options).getTime(), options);
    reportFormatterDiagnostics("time", format, "formatTime", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("time", currentLocale, resolved,
        () -> MessagevisorFormatters.formatTime(value, currentLocale, resolved, timeZone));
  }

  public String formatTime(Object value, Map<String, Object> format) {
    return formatTime(value, format, EvaluationOptions.builder().build());
  }

  public String formatTime(Object value, Map<String, Object> format, EvaluationOptions options) {
    reportFormatterDiagnostics("time", format, "formatTime", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("time", currentLocale, resolved,
        () -> MessagevisorFormatters.formatTime(value, currentLocale, resolved, timeZone));
  }

  public List<FormatPart> formatTimeToParts(Object value, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("time", preset, getEvaluationFormats(options).getTime(), options);
    reportFormatterDiagnostics("time", format, "formatTimeToParts", null);
    reportFormatterDiagnostics("parts", format, "formatTimeToParts", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("time", currentLocale, resolved,
        () -> MessagevisorFormatters.formatTimeToParts(value, currentLocale, resolved, timeZone));
  }

  public String formatDateTimeRange(Object start, Object end, String preset) {
    return formatDateTimeRange(start, end, preset, EvaluationOptions.builder().build());
  }

  public String formatDateTimeRange(Object start, Object end, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("dateTimeRange", preset, getEvaluationFormats(options).getDateTimeRange(), options);
    reportFormatterDiagnostics("dateTimeRange", format, "formatDateTimeRange", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("dateTimeRange", currentLocale, resolved,
        () -> MessagevisorFormatters.formatDateTimeRange(start, end, currentLocale, resolved, timeZone));
  }

  public String formatDateTimeRange(Object start, Object end, Map<String, Object> format, EvaluationOptions options) {
    reportFormatterDiagnostics("dateTimeRange", format, "formatDateTimeRange", null);
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = withTimeZoneOption(format, options);
    return formatSafely("dateTimeRange", currentLocale, resolved,
        () -> MessagevisorFormatters.formatDateTimeRange(start, end, currentLocale, resolved, timeZone));
  }

  public String formatRelativeTime(double value, String unit, String preset) {
    EvaluationOptions options = EvaluationOptions.builder().build();
    Map<String, Object> format = getNamedFormat("relative", preset, getEvaluationFormats(options).getRelative(), options);
    reportFormatterDiagnostics("relative", format, "formatRelativeTime", null);
    String currentLocale = getCurrentLocale(options);
    return formatSafely("relative", currentLocale, format,
        () -> MessagevisorFormatters.formatRelativeTime(value, unit, currentLocale, format));
  }

  public String formatRelativeTime(double value, String unit, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("relative", preset, getEvaluationFormats(options).getRelative(), options);
    reportFormatterDiagnostics("relative", format, "formatRelativeTime", null);
    String currentLocale = getCurrentLocale(options);
    return formatSafely("relative", currentLocale, format,
        () -> MessagevisorFormatters.formatRelativeTime(value, unit, currentLocale, format));
  }

  public List<FormatPart> formatRelativeTimeToParts(double value, String unit, String preset) {
    EvaluationOptions options = EvaluationOptions.builder().build();
    Map<String, Object> format = getNamedFormat("relative", preset, getEvaluationFormats(options).getRelative(), options);
    reportFormatterDiagnostics("relative", format, "formatRelativeTimeToParts", null);
    reportFormatterDiagnostics("parts", format, "formatRelativeTimeToParts", null);
    String currentLocale = getCurrentLocale(options);
    return formatSafely("relative", currentLocale, format,
        () -> MessagevisorFormatters.formatRelativeTimeToParts(value, unit, currentLocale, format));
  }

  public List<FormatPart> formatRelativeTimeToParts(double value, String unit, String preset, EvaluationOptions options) {
    Map<String, Object> format = getNamedFormat("relative", preset, getEvaluationFormats(options).getRelative(), options);
    reportFormatterDiagnostics("relative", format, "formatRelativeTimeToParts", null);
    reportFormatterDiagnostics("parts", format, "formatRelativeTimeToParts", null);
    String currentLocale = getCurrentLocale(options);
    return formatSafely("relative", currentLocale, format,
        () -> MessagevisorFormatters.formatRelativeTimeToParts(value, unit, currentLocale, format));
  }

  public String formatPlural(double value) {
    return formatPlural(value, false);
  }

  public String formatPlural(double value, boolean ordinal) {
    return formatPlural(value, ordinal, EvaluationOptions.builder().build());
  }

  public String formatPlural(double value, EvaluationOptions options) {
    return formatPlural(value, false, options);
  }

  public String formatPlural(double value, boolean ordinal, EvaluationOptions options) {
    String currentLocale = getCurrentLocale(options);
    Map<String, Object> resolved = Map.of("ordinal", ordinal);
    return formatSafely("plural", currentLocale, resolved,
        () -> MessagevisorFormatters.formatPlural(value, currentLocale, ordinal));
  }

  public String formatList(List<String> values) {
    return formatList(values, Map.of());
  }

  public String formatList(List<String> values, Map<String, Object> options) {
    reportFormatterDiagnostics("list", options, "formatList", null);
    String currentLocale = getOptionLocale(options);
    Map<String, Object> resolved = withoutLocaleOption(options);
    return formatSafely("list", currentLocale, resolved,
        () -> MessagevisorFormatters.formatList(values, currentLocale, resolved));
  }

  public List<FormatPart> formatListToParts(List<String> values) {
    return formatListToParts(values, Map.of());
  }

  public List<FormatPart> formatListToParts(List<String> values, Map<String, Object> options) {
    reportFormatterDiagnostics("list", options, "formatListToParts", null);
    reportFormatterDiagnostics("parts", options, "formatListToParts", null);
    String currentLocale = getOptionLocale(options);
    Map<String, Object> resolved = withoutLocaleOption(options);
    return formatSafely("list", currentLocale, resolved,
        () -> MessagevisorFormatters.formatListToParts(values, currentLocale, resolved));
  }

  public String formatDisplayName(String value, Map<String, Object> options) {
    reportFormatterDiagnostics("displayName", options, "formatDisplayName", null);
    String currentLocale = getOptionLocale(options);
    Map<String, Object> resolved = withoutLocaleOption(options);
    return formatSafely("displayName", currentLocale, resolved,
        () -> MessagevisorFormatters.formatDisplayName(value, currentLocale, resolved));
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    List.copyOf(parentUnsubscribers).forEach(MessagevisorUnsubscribe::unsubscribe);
    parentUnsubscribers.clear();
    listeners.values().forEach(List::clear);
    moduleDiagnosticSubscriptions.clear();
    moduleApis.clear();
    if (parent != null) return;
    List<Exception> errors = new ArrayList<>();
    synchronized (moduleLock) {
      for (int index = modules.size() - 1; index >= 0; index--) {
        try {
          modules.get(index).close();
        } catch (Exception error) {
          errors.add(error);
          reportDiagnostic(
              MessagevisorDiagnostic.builder(LogLevel.ERROR, "module_close_error", "Module close failed")
                  .moduleName(modules.get(index).name())
                  .originalError(error)
                  .build());
        }
      }
      modules.clear();
    }
    if (!errors.isEmpty()) {
      throw new MessagevisorCloseException("One or more Messagevisor modules failed to close.", errors);
    }
  }

  public CompletableFuture<Void> closeAsync() {
    return CompletableFuture.runAsync(this::close);
  }

  private String getCurrentLocale() {
    return getCurrentLocale(EvaluationOptions.builder().build());
  }

  private String getCurrentLocale(EvaluationOptions options) {
    String currentLocale = options == null || options.locale() == null ? locale : options.locale();

    if (currentLocale == null) {
      reportDiagnostic(
          MessagevisorDiagnostic.builder(LogLevel.ERROR, "missing_locale", "Locale not set")
              .detail("locale", null)
              .build());
      throw new MessagevisorException("Locale not set");
    }
    return currentLocale;
  }

  private String getMessageFromDatafile(String messageKey, TranslateOptions options) {
    String currentLocale = getCurrentLocale(options);
    DatafileContent datafile = datafiles.get(currentLocale);
    if (datafile == null) {
      return null;
    }
    Map<String, Object> evaluationContext = shallowMerge(context, options.context());
    DatafileMessage message = datafile.getMessages().get(messageKey);
    List<MessageOverride> overrides = message == null ? List.of() : message.getOverrides();
    for (MessageOverride override : overrides) {
      ConditionEvaluator.EvaluateOptions evaluateOptions =
          new ConditionEvaluator.EvaluateOptions(
              evaluationContext, datafile.getSegments(), getFlagResolver(), getVariationResolver());
      boolean matchesConditions =
          ConditionEvaluator.evaluateCondition(override.getConditions(), evaluateOptions);
      boolean matchesSegments =
          ConditionEvaluator.evaluateGroupSegment(override.getSegments(), evaluateOptions);
      if (matchesConditions && matchesSegments) {
        reportDiagnostic(
            MessagevisorDiagnostic.builder(
                    LogLevel.DEBUG, "message_override_matched", "Message override matched")
                .detail("locale", currentLocale)
                .detail("messageKey", messageKey)
                .detail("overrideKey", override.getKey())
                .build());
        return override.getTranslation();
      }
    }
    return datafile.getTranslations().get(messageKey);
  }

  private Map<String, Object> getMessageMeta(String messageKey, String locale) {
    DatafileContent datafile = datafiles.get(locale);
    DatafileMessage message = datafile == null ? null : datafile.getMessages().get(messageKey);
    return message == null ? null : message.getMeta();
  }

  private DatafileMessage getMessageDefinition(String messageKey, String locale) {
    DatafileContent datafile = datafiles.get(locale);
    return datafile == null ? null : datafile.getMessages().get(messageKey);
  }

  private ResolvedMessage resolveMessage(
      String messageKey, String defaultTranslation, TranslateOptions options) {
    String currentLocale = getCurrentLocale(options);
    String translated = null;
    if (messageKey != null) {
      translated = getMessageFromDatafile(messageKey, options);
      if (translated == null) {
        Map<String, String> defaults = getDefaultTranslations(currentLocale);
        translated = defaults == null ? null : defaults.get(messageKey);
      }
    }
    if (messageKey == null && defaultTranslation != null) {
      return new ResolvedMessage(currentLocale, defaultTranslation, defaultTranslation, null);
    }
    if (translated != null) {
      DatafileMessage message = messageKey == null ? null : getMessageDefinition(messageKey, currentLocale);
      if (message != null && message.isDeprecated()) {
        reportDiagnostic(
            MessagevisorDiagnostic.builder(
                    LogLevel.WARN, "deprecated_message", "Deprecated message evaluated")
                .detail("locale", currentLocale)
                .detail("messageKey", messageKey)
                .detail("deprecationWarning", message.getDeprecationWarning())
                .detail("source", "translation")
                .build());
      }
      return new ResolvedMessage(currentLocale, translated, translated, messageKey);
    }
    if (messageKey != null) {
      boolean hasDatafile = datafiles.containsKey(currentLocale);
      reportDiagnostic(
          MessagevisorDiagnostic.builder(
                  LogLevel.ERROR,
                  hasDatafile ? "missing_translation" : "missing_datafile",
                  hasDatafile ? "Missing translation" : "Datafile not found for locale")
              .detail("locale", currentLocale)
              .detail("messageKey", messageKey)
              .detail("source", "translation")
              .build());
    }
    if (defaultTranslation != null) {
      return new ResolvedMessage(currentLocale, defaultTranslation, defaultTranslation, messageKey);
    }
    return new ResolvedMessage(currentLocale, messageKey == null ? "" : messageKey, messageKey == null ? "" : messageKey, messageKey);
  }

  private Object runFormats(Object translation, Map<String, Object> values, MessagevisorFormatPayload payload) {
    Object current = translation;
    for (MessagevisorModule module : getModules()) {
      try {
        Object next =
            module.format(
                new MessagevisorFormatPayload(
                    current,
                    values,
                    payload.locale(),
                    payload.source(),
                    payload.messageKey(),
                    payload.meta(),
                    payload.formats(),
                    payload.moduleOptions(),
                    payload.currency(),
                    payload.timeZone()),
                getModuleApi(module));
        if (next != null) {
          current = next;
        }
      } catch (RuntimeException error) {
        reportDiagnostic(
            MessagevisorDiagnostic.builder(LogLevel.ERROR, "invalid_message", "Unable to format message")
                .detail("locale", payload.locale())
                .detail("messageKey", payload.messageKey())
                .detail("source", payload.source())
                .originalError(error)
                .build());
        throw error;
      }
    }
    return current;
  }

  private Object runTransforms(Object translation, MessagevisorTransformPayload payload) {
    Object current = translation;
    for (MessagevisorModule module : getModules()) {
      Object next =
          module.transform(
              new MessagevisorTransformPayload(
                  current, payload.locale(), payload.source(), payload.messageKey(), payload.meta()),
              getModuleApi(module));
      if (next != null) {
        current = next;
      }
    }
    return current;
  }

  private FormatPresets getEvaluationFormats(EvaluationOptions options) {
    String currentLocale = getCurrentLocale(options);
    FormatPresets merged = new FormatPresets();
    mergeFormats(merged, defaultFormatsByLocale.get(currentLocale));
    if (datafiles.containsKey(currentLocale)) {
      mergeFormats(merged, datafiles.get(currentLocale).getFormats());
    }
    mergeFormats(merged, options.formats());
    resolveFormatRuntimeOptions(merged, options);
    return merged;
  }

  private void resolveFormatRuntimeOptions(FormatPresets formats, EvaluationOptions options) {
    formats
        .getNumber()
        .values()
        .forEach(
            format -> {
              if ("currency".equals(format.get("style"))) {
                format.put(
                    "currency",
                    firstNonBlank(options.currency(), ObjectMaps.stringOption(format, "currency"), currency, "USD"));
              }
            });
    formats.getDate().values().forEach(format -> format.put("timeZone", firstNonBlank(options.timeZone(), ObjectMaps.stringOption(format, "timeZone"), timeZone, null)));
    formats.getTime().values().forEach(format -> format.put("timeZone", firstNonBlank(options.timeZone(), ObjectMaps.stringOption(format, "timeZone"), timeZone, null)));
    formats.getDateTimeRange().values().forEach(format -> format.put("timeZone", firstNonBlank(options.timeZone(), ObjectMaps.stringOption(format, "timeZone"), timeZone, null)));
  }

  private void reportFormatterDiagnostics(
      String family, Map<String, Object> format, String source, String messageKey) {
    for (String message : MessagevisorFormatters.formatterLimitations(family, format)) {
      reportDiagnostic(
          MessagevisorDiagnostic.builder(LogLevel.WARN, "unsupported_formatter", message)
              .detail("locale", locale)
              .detail("messageKey", messageKey)
              .detail("source", source)
              .build());
    }
  }

  private Map<String, Object> getNamedFormat(
      String family,
      String preset,
      Map<String, Map<String, Object>> presets,
      EvaluationOptions options) {
    Map<String, Object> format = presets == null ? null : presets.get(preset);
    if (format == null) {
      reportDiagnostic(
          MessagevisorDiagnostic.builder(LogLevel.ERROR, "missing_format", "Named format preset not found")
              .detail("locale", getCurrentLocale(options))
              .detail("type", family)
              .detail("preset", preset)
              .build());
    }
    return format;
  }

  private <T> T formatSafely(
      String family, String currentLocale, Map<String, Object> options, Supplier<T> formatter) {
    try {
      return formatter.get();
    } catch (RuntimeException error) {
      reportDiagnostic(
          MessagevisorDiagnostic.builder(LogLevel.ERROR, "invalid_format", "Invalid format options")
              .detail("locale", currentLocale)
              .detail("type", family)
              .detail("options", options)
              .originalError(error)
              .build());
      throw error;
    }
  }

  private static void mergeFormats(FormatPresets target, FormatPresets incoming) {
    if (incoming == null) {
      return;
    }
    deepMergeFamily(target.getNumber(), incoming.getNumber());
    deepMergeFamily(target.getDate(), incoming.getDate());
    deepMergeFamily(target.getTime(), incoming.getTime());
    deepMergeFamily(target.getRelative(), incoming.getRelative());
    deepMergeFamily(target.getDateTimeRange(), incoming.getDateTimeRange());
  }

  private static void deepMergeFamily(
      Map<String, Map<String, Object>> target, Map<String, Map<String, Object>> incoming) {
    if (incoming == null) {
      return;
    }
    incoming.forEach(
        (key, value) -> {
          Map<String, Object> merged = target.getOrDefault(key, new LinkedHashMap<>());
          merged.putAll(CloneUtils.deepCopyMap(value));
          target.put(key, merged);
        });
  }

  private MessagevisorModuleApi getModuleApi(MessagevisorModule module) {
    String key = getModuleApiKey(module);
    return moduleApis.computeIfAbsent(key, ignored -> createModuleApi(module, key));
  }

  private MessagevisorModuleApi createModuleApi(MessagevisorModule module, String moduleKey) {
    return new MessagevisorModuleApi() {
      @Override
      public void setFlagResolver(FlagResolver resolver) {
        moduleFlagResolvers.removeIf(registration -> Objects.equals(registration.moduleKey(), moduleKey));
        if (resolver != null) moduleFlagResolvers.add(new ModuleFlagResolver(moduleKey, resolver));
      }

      @Override
      public void setVariationResolver(VariationResolver resolver) {
        moduleVariationResolvers.removeIf(registration -> Objects.equals(registration.moduleKey(), moduleKey));
        if (resolver != null) moduleVariationResolvers.add(new ModuleVariationResolver(moduleKey, resolver));
      }

      @Override
      public String getRevision(String locale) {
        return Messagevisor.this.getRevision(locale == null ? Messagevisor.this.locale : locale);
      }

      @Override
      public MessagevisorUnsubscribe onDiagnostic(
          MessagevisorDiagnosticHandler handler, MessagevisorModuleDiagnosticOptions options) {
        MessagevisorModuleDiagnosticOptions resolvedOptions =
            options == null ? new MessagevisorModuleDiagnosticOptions(LogLevel.INFO) : options;
        ModuleDiagnosticSubscription subscription =
            new ModuleDiagnosticSubscription(moduleKey, handler, resolvedOptions.logLevel());
        moduleDiagnosticSubscriptions.add(subscription);
        return () -> moduleDiagnosticSubscriptions.remove(subscription);
      }

      @Override
      public void reportDiagnostic(MessagevisorModuleReportedDiagnostic diagnostic) {
        MessagevisorDiagnostic.Builder builder =
            MessagevisorDiagnostic.builder(diagnostic.level(), diagnostic.code(), diagnostic.message())
                .details(diagnostic.details())
                .originalError(diagnostic.originalError());
        if (module.name() != null) {
          builder.module(module.name());
        }
        Messagevisor.this.reportDiagnostic(builder.build(), moduleKey);
      }
    };
  }

  private String getModuleApiKey(MessagevisorModule module) {
    if (module.name() != null) {
      return "name:" + module.name();
    }
    return anonymousModuleApiKeys.computeIfAbsent(
        module, ignored -> "anonymous:" + moduleApiId.incrementAndGet());
  }

  private void clearModuleDiagnosticSubscriptions(MessagevisorModule module) {
    String key = getModuleApiKey(module);
    moduleDiagnosticSubscriptions.removeIf(subscription -> Objects.equals(subscription.moduleKey, key));
    moduleFlagResolvers.removeIf(registration -> Objects.equals(registration.moduleKey(), key));
    moduleVariationResolvers.removeIf(registration -> Objects.equals(registration.moduleKey(), key));
    moduleApis.remove(key);
    anonymousModuleApiKeys.remove(module);
  }

  private void reportDiagnostic(MessagevisorDiagnostic diagnostic) {
    reportDiagnostic(diagnostic, null);
  }

  private void reportDiagnostic(MessagevisorDiagnostic diagnostic, String sourceModuleKey) {
    for (ModuleDiagnosticSubscription subscription : List.copyOf(moduleDiagnosticSubscriptions)) {
      if (Objects.equals(subscription.moduleKey, sourceModuleKey)) {
        continue;
      }
      if (subscription.logLevel.allows(diagnostic.level())) {
        subscription.handler.handle(diagnostic);
      }
    }
    if (logLevel.allows(diagnostic.level())) {
      if (onDiagnostic != null) {
        onDiagnostic.handle(diagnostic);
      } else {
        System.err.printf("[Messagevisor] %s %s%n", diagnostic.message(), diagnostic);
      }
    }
    if (diagnostic.level() == LogLevel.ERROR) {
      emitError(diagnostic);
    }
  }

  private void emitError(MessagevisorDiagnostic diagnostic) {
    MessagevisorSnapshot snapshot = getSnapshot();
    MessagevisorEvent event =
        new MessagevisorEvent(
            EventName.ERROR,
            null,
            version.get(),
            snapshot,
            snapshot,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            diagnostic);
    listeners.get(EventName.ERROR).forEach(callback -> callback.handle(event));
  }

  private void emit(EventName type, MessagevisorSnapshot previousSnapshot, EventDetails details) {
    if (closed.get()) {
      return;
    }
    int eventVersion = version.incrementAndGet();
    MessagevisorEvent event =
        new MessagevisorEvent(
            type,
            null,
            eventVersion,
            getSnapshot(),
            previousSnapshot,
            details.locale,
            details.activeLocale,
            details.previousLocale,
            details.datafile,
            details.context,
            details.previousContext,
            details.currency,
            details.previousCurrency,
            details.timeZone,
            details.previousTimeZone,
            details.replaced,
            null);
    listeners.get(type).forEach(callback -> callback.handle(event));
    MessagevisorEvent changeEvent =
        new MessagevisorEvent(
            EventName.CHANGE,
            type,
            event.version(),
            event.snapshot(),
            event.previousSnapshot(),
            event.locale(),
            event.activeLocale(),
            event.previousLocale(),
            event.datafile(),
            event.context(),
            event.previousContext(),
            event.currency(),
            event.previousCurrency(),
            event.timeZone(),
            event.previousTimeZone(),
            event.replaced(),
            null);
    listeners.get(EventName.CHANGE).forEach(callback -> callback.handle(changeEvent));
  }

  private static DatafileContent mergeStoredDatafile(
      DatafileContent existing, DatafileContent incoming) {
    incoming.setDirection(incoming.getDirection() == null ? existing.getDirection() : incoming.getDirection());
    incoming.setSegments(shallowMergeTyped(existing.getSegments(), incoming.getSegments()));
    incoming.setMessages(shallowMergeTyped(existing.getMessages(), incoming.getMessages()));
    incoming.setTranslations(shallowMergeTyped(existing.getTranslations(), incoming.getTranslations()));
    return incoming;
  }

  private static <T> Map<String, T> shallowMergeTyped(Map<String, T> parent, Map<String, T> child) {
    Map<String, T> result = new LinkedHashMap<>();
    if (parent != null) {
      result.putAll(parent);
    }
    if (child != null) {
      result.putAll(child);
    }
    return result;
  }

  private static Map<String, Object> shallowMerge(Map<String, Object> parent, Map<String, Object> child) {
    Map<String, Object> result = CloneUtils.deepCopyMap(parent);
    if (child != null) {
      child.forEach((key, value) -> result.put(key, CloneUtils.deepCopyValue(value)));
    }
    return result;
  }

  private Map<String, Object> withCurrencyOption(Map<String, Object> format, EvaluationOptions options) {
    Map<String, Object> result = CloneUtils.deepCopyMap(format);
    if (options.currency() != null) {
      result.put("currency", options.currency());
    }
    return result;
  }

  private Map<String, Object> withTimeZoneOption(Map<String, Object> format, EvaluationOptions options) {
    Map<String, Object> result = CloneUtils.deepCopyMap(format);
    if (options.timeZone() != null) {
      result.put("timeZone", options.timeZone());
    }
    return result;
  }

  private String getOptionLocale(Map<String, Object> options) {
    Object optionLocale = options == null ? null : options.get("locale");
    return optionLocale == null ? getCurrentLocale() : String.valueOf(optionLocale);
  }

  private Map<String, Object> withoutLocaleOption(Map<String, Object> options) {
    Map<String, Object> result = CloneUtils.deepCopyMap(options);
    result.remove("locale");
    return result;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private List<MessagevisorModule> getModules() {
    return parent == null ? modules : parent.getModules();
  }

  private record ModuleFlagResolver(String moduleKey, FlagResolver resolver) {}
  private record ModuleVariationResolver(String moduleKey, VariationResolver resolver) {}

  private record ModuleDiagnosticSubscription(
      String moduleKey, MessagevisorDiagnosticHandler handler, LogLevel logLevel) {}

  private record ResolvedMessage(String locale, String source, String formatted, String messageKey) {}

  private static final class EventDetails {
    private String locale;
    private String activeLocale;
    private String previousLocale;
    private DatafileContent datafile;
    private Map<String, Object> context;
    private Map<String, Object> previousContext;
    private String currency;
    private String previousCurrency;
    private String timeZone;
    private String previousTimeZone;
    private Boolean replaced;

    static Builder builder() {
      return new Builder();
    }

    static final class Builder {
      private final EventDetails details = new EventDetails();

      Builder locale(String value) {
        details.locale = value;
        return this;
      }

      Builder previousLocale(String value) {
        details.previousLocale = value;
        return this;
      }

      Builder activeLocale(String value) {
        details.activeLocale = value;
        return this;
      }

      Builder datafile(DatafileContent value) {
        details.datafile = value;
        return this;
      }

      Builder context(Map<String, Object> value) {
        details.context = value;
        return this;
      }

      Builder previousContext(Map<String, Object> value) {
        details.previousContext = value;
        return this;
      }

      Builder currency(String value) {
        details.currency = value;
        return this;
      }

      Builder previousCurrency(String value) {
        details.previousCurrency = value;
        return this;
      }

      Builder timeZone(String value) {
        details.timeZone = value;
        return this;
      }

      Builder previousTimeZone(String value) {
        details.previousTimeZone = value;
        return this;
      }

      Builder replaced(boolean value) {
        details.replaced = value;
        return this;
      }

      EventDetails build() {
        return details;
      }
    }
  }
}
