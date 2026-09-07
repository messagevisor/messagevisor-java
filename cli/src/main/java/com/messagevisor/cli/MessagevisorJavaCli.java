package com.messagevisor.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.messagevisor.modules.icu.IcuModule;
import com.messagevisor.modules.interpolation.InterpolationModule;
import com.messagevisor.sdk.ConditionEvaluator;
import com.messagevisor.sdk.DatafileContent;
import com.messagevisor.sdk.EvaluationOptions;
import com.messagevisor.sdk.FormatPresets;
import com.messagevisor.sdk.JsonSupport;
import com.messagevisor.sdk.LogLevel;
import com.messagevisor.sdk.Messagevisor;
import com.messagevisor.sdk.MessagevisorModule;
import com.messagevisor.sdk.MessagevisorOptions;
import com.messagevisor.sdk.Segment;
import com.messagevisor.sdk.TranslateOptions;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.IVersionProvider;

@Command(
    name = "messagevisor-java",
    mixinStandardHelpOptions = true,
    versionProvider = MessagevisorJavaCli.VersionProvider.class,
    subcommands = {
      MessagevisorJavaCli.TestCommand.class,
      MessagevisorJavaCli.EvaluateCommand.class,
      MessagevisorJavaCli.BenchmarkCommand.class,
      MessagevisorJavaCli.ExamplesCommand.class
    })
public final class MessagevisorJavaCli implements Callable<Integer> {
  private static final String RESET = "\u001b[0m";
  private static final String BOLD = "\u001b[1m";
  private static final String DIM = "\u001b[2m";
  private static final String CYAN = "\u001b[36m";
  private static final String GREEN = "\u001b[32m";

  public static final class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
      String implementationVersion = MessagevisorJavaCli.class.getPackage().getImplementationVersion();
      return new String[] {
        implementationVersion == null
            ? System.getProperty("messagevisor.version", "development")
            : implementationVersion
      };
    }
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new MessagevisorJavaCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    CommandLine.usage(this, System.err);
    return 2;
  }

  static final class SharedOptions {
    @Option(names = "--projectDirectoryPath", description = "Messagevisor project directory path")
    Path projectDirectoryPath = Path.of(".").toAbsolutePath().normalize();

    @Option(names = "--withIcuModule", description = "Install ICU formatting module")
    boolean withIcuModule;

    @Option(names = "--withInterpolationModule", description = "Install interpolation formatting module")
    boolean withInterpolationModule;

    List<MessagevisorModule> modules() {
      List<MessagevisorModule> modules = new ArrayList<>();
      if (withInterpolationModule) {
        modules.add(InterpolationModule.create());
      }
      if (withIcuModule) {
        modules.add(IcuModule.create());
      }
      return modules;
    }
  }

  @Command(name = "test", mixinStandardHelpOptions = true)
  static final class TestCommand implements Callable<Integer> {
    @CommandLine.Mixin SharedOptions shared = new SharedOptions();

    @Option(names = "--keyPattern")
    String keyPattern;

    @Option(names = "--assertionPattern")
    String assertionPattern;

    @Option(names = "--onlyFailures")
    boolean onlyFailures;

    @Option(names = "--quiet")
    boolean quiet;

    @Option(names = "--target", description = "Fallback target for assertions without one")
    String target = "java";

    @Option(
        names = "--normalizeSpaces",
        description = "Normalize non-breaking Unicode spaces in Java-evaluated strings before comparison")
    boolean normalizeSpaces;

    @Override
    public Integer call() throws Exception {
      List<Map<String, Object>> tests =
          readJson(runMessagevisor(shared.projectDirectoryPath, listArgs("list", "--tests", "--applyMatrix", "--json", keyPattern == null ? null : "--keyPattern=" + keyPattern)), new TypeReference<>() {});
      Map<String, Segment> segments = loadSegments(shared.projectDirectoryPath);
      Pattern assertionRegex = assertionPattern == null ? null : Pattern.compile(assertionPattern);
      Map<String, DatafileContent> datafiles = new LinkedHashMap<>();
      int passedTests = 0;
      int failedTests = 0;
      int passedAssertions = 0;
      int failedAssertions = 0;
      for (Map<String, Object> test : tests) {
        boolean failed = false;
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> assertion : listOfMaps(test.get("assertions"))) {
          String description = descriptionFor(test, assertion);
          if (assertionRegex != null && !assertionRegex.matcher(description).find()) {
            continue;
          }
          List<String> failures =
              runAssertion(
                  shared, segments, datafiles, test, assertion, target, normalizeSpaces);
          if (failures.isEmpty()) {
            passedAssertions++;
            lines.add("  ✓ " + description);
          } else {
            failed = true;
            failedAssertions++;
            lines.add("  x " + description);
            failures.forEach(failure -> lines.add("    " + failure));
          }
        }
        if (failed) {
          failedTests++;
        } else {
          passedTests++;
        }
        if (!quiet && (!onlyFailures || failed)) {
          System.out.println("\nTesting: " + test.get("key"));
          lines.forEach(System.out::println);
        }
      }
      System.out.printf("%nTest specs: %d passed, %d failed%n", passedTests, failedTests);
      System.out.printf("Assertions: %d passed, %d failed%n", passedAssertions, failedAssertions);
      return failedTests == 0 ? 0 : 1;
    }
  }

  @Command(name = "evaluate", mixinStandardHelpOptions = true)
  static class EvaluateCommand implements Callable<Integer> {
    @CommandLine.Mixin SharedOptions shared = new SharedOptions();

    @Option(names = "--target")
    String target = "web";

    @Option(names = "--locale")
    String locale;

    @Option(names = "--message")
    String message;

    @Option(names = "--rawMessage")
    String rawMessage;

    @Option(names = "--segment")
    String segment;

    @Option(names = "--context")
    String contextJson;

    @Option(names = "--values")
    String valuesJson;

    @Option(names = "--json")
    boolean json;

    @Option(names = "--pretty")
    boolean pretty;

    @Override
    public Integer call() throws Exception {
      Map<String, Object> context = parseObject(contextJson);
      Map<String, Object> values = parseObject(valuesJson);
      if (segment != null) {
        boolean matched =
            ConditionEvaluator.evaluateSegment(
                segment, new ConditionEvaluator.EvaluateOptions(context, loadSegments(shared.projectDirectoryPath), null, null));
        printResult(Map.of("segment", segment, "matched", matched), json, pretty, Boolean.toString(matched));
        return 0;
      }
      if (locale == null) {
        throw new CommandLine.ParameterException(new CommandLine(this), "pass --locale=<locale>");
      }
      Messagevisor m = new Messagevisor(MessagevisorOptions.builder().datafile(buildDatafile(shared.projectDirectoryPath, target, locale)).context(context).logLevel(LogLevel.FATAL).modules(shared.modules()).build());
      String result;
      if (message != null) {
        result = m.translate(message, values, TranslateOptions.builder().context(context).build());
        printResult(Map.of("message", message, "translation", result), json, pretty, result);
      } else if (rawMessage != null) {
        result = m.formatMessage(rawMessage, values);
        printResult(Map.of("rawMessage", rawMessage, "translation", result), json, pretty, result);
      } else {
        throw new CommandLine.ParameterException(new CommandLine(this), "pass --message, --rawMessage, or --segment");
      }
      return 0;
    }
  }

  @Command(name = "benchmark", mixinStandardHelpOptions = true)
  static final class BenchmarkCommand extends EvaluateCommand {
    @Option(names = "-n")
    int iterations = 1000;

    @Override
    public Integer call() throws Exception {
      if (message != null && rawMessage != null) {
        throw new CommandLine.ParameterException(new CommandLine(this), "pass either --message or --rawMessage, not both");
      }
      if (message == null && rawMessage == null) {
        throw new CommandLine.ParameterException(new CommandLine(this), "pass --message or --rawMessage");
      }
      if (locale == null) {
        throw new CommandLine.ParameterException(new CommandLine(this), "pass --locale=<locale>");
      }

      int count = Math.max(1, iterations);
      Map<String, Object> context = parseObject(contextJson);
      Map<String, Object> values = parseObject(valuesJson);
      DatafileContent datafile = buildDatafile(shared.projectDirectoryPath, target, locale);
      Messagevisor m =
          new Messagevisor(
              MessagevisorOptions.builder()
                  .datafile(datafile)
                  .context(context)
                  .logLevel(LogLevel.FATAL)
                  .modules(shared.modules())
                  .build());
      TranslateOptions translateOptions = TranslateOptions.builder().context(context).build();
      EvaluationOptions evaluationOptions = EvaluationOptions.builder().build();

      String last = null;
      long start = System.nanoTime();
      for (int i = 0; i < count; i++) {
        last =
            message != null
                ? m.translate(message, values, translateOptions)
                : m.formatMessage(rawMessage, values, evaluationOptions);
      }
      long elapsed = System.nanoTime() - start;
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("target", target);
      output.put("locale", locale);
      if (message != null) {
        output.put("message", message);
      } else {
        output.put("rawMessage", rawMessage);
      }
      output.put("iterations", count);
      output.put("durationMs", elapsed / 1_000_000.0);
      output.put("averageNs", elapsed / (double) count);
      output.put("averageMicros", elapsed / (double) count / 1000.0);
      output.put("lastResult", last);
      printResult(
          output,
          json,
          pretty,
          "Messagevisor Java benchmark\n"
              + "  Iterations: "
              + count
              + "\n"
              + "  Total:      "
              + output.get("durationMs")
              + "ms\n"
              + "  Average:    "
              + output.get("averageMicros")
              + "µs");
      return 0;
    }
  }

  @Command(name = "examples", mixinStandardHelpOptions = true)
  static final class ExamplesCommand implements Callable<Integer> {
    @CommandLine.Mixin SharedOptions shared = new SharedOptions();

    @Option(names = "--locale")
    String locale;

    @Option(names = "--json")
    boolean json;

    @Option(names = "--pretty")
    boolean pretty;

    @Option(names = "--normalizeSpaces", description = "Normalize only ordinary, no-break and narrow no-break spaces")
    boolean normalizeSpaces;

    @Option(names = "--onlyFailures")
    boolean onlyFailures;

    @Override
    public Integer call() throws Exception {
      List<String> args =
          listArgs(
              "examples",
              "--json",
              "--includeEvaluationInput",
              locale == null ? null : "--locale=" + locale);
      Map<String, Object> result = readJson(runMessagevisor(shared.projectDirectoryPath, args), new TypeReference<>() {});
      List<String> failures = new ArrayList<>();
      List<Map<String, Object>> examples = new ArrayList<>(listOfMaps(result.get("locales")));
      examples.addAll(listOfMaps(result.get("messages")));
      for (Map<String, Object> example : examples) {
        failures.addAll(verifyExample(example, shared.modules(), normalizeSpaces));
      }
      removeEvaluationInputs(result);
      result.put("failures", failures);
      if (json) {
        printJson(result, pretty);
      } else {
        if (!onlyFailures) printExamplesPlain(result);
        failures.forEach(System.out::println);
        System.out.println("Examples: " + examples.size() + " evaluated, " + failures.size() + " failed");
      }
      return failures.isEmpty() ? 0 : 1;
    }
  }

  static List<String> verifyExample(Map<String, Object> example, List<MessagevisorModule> modules, boolean normalizeSpaces) {
    Map<String, Object> expected = new LinkedHashMap<>(example);
    expected.put("expectedTranslation", example.get("evaluatedTranslation"));
    reevaluateExample(example, modules);
    List<String> failures = new ArrayList<>();
    compareTranslation(expected, stringValue(example.get("target")), stringValue(example.get("locale")),
        stringValue(example.get("evaluatedTranslation")), normalizeSpaces, failures);
    Map<String, Object> identity = new LinkedHashMap<>(expected);
    identity.remove("evaluationInput");
    identity.remove("expectedTranslation");
    identity.remove("evaluatedTranslation");
    identity.remove("expectedByRuntime");
    return failures.stream().map(failure -> "Example: " + identity + "\n" + failure).toList();
  }

  static void reevaluateExample(Map<String, Object> example, List<MessagevisorModule> modules) {
    Map<String, Object> evaluationInput = map(example.get("evaluationInput"));
    boolean hasDatafile = evaluationInput.get("datafile") != null;
    MessagevisorOptions.Builder options =
        MessagevisorOptions.builder()
            .context(map(evaluationInput.get("context")))
            .defaultFormats(formatPresetsByLocale(evaluationInput.get("defaultFormats")))
            .logLevel(LogLevel.FATAL)
            .modules(modules);
    if (hasDatafile) {
      options.datafile(datafile(evaluationInput.get("datafile")));
    } else {
      options.locale(stringValue(example.get("locale")));
    }
    Messagevisor m = new Messagevisor(options.build());
    FormatPresets formats = formatPresets(evaluationInput.get("formats"));
    String evaluated;
    if (example.get("message") != null) {
      evaluated =
          m.translate(
              String.valueOf(example.get("message")),
              map(evaluationInput.get("values")),
              TranslateOptions.builder()
                  .context(map(evaluationInput.get("context")))
                  .formats(formats)
                  .currency(stringValue(evaluationInput.get("currency")))
                  .timeZone(stringValue(evaluationInput.get("timeZone")))
                  .build());
    } else {
      evaluated =
          m.formatMessage(
              String.valueOf(example.get("rawMessage")),
              map(evaluationInput.get("values")),
              EvaluationOptions.builder()
                  .formats(formats)
                  .currency(stringValue(evaluationInput.get("currency")))
                  .timeZone(stringValue(evaluationInput.get("timeZone")))
                  .build());
    }
    example.put("evaluatedTranslation", evaluated);
  }

  private static void removeEvaluationInputs(Map<String, Object> result) {
    removeEvaluationInputsFromList(result.get("locales"));
    removeEvaluationInputsFromList(result.get("messages"));
  }

  @SuppressWarnings("unchecked")
  private static void removeEvaluationInputsFromList(Object value) {
    if (!(value instanceof List<?> list)) {
      return;
    }
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        ((Map<String, Object>) map).remove("evaluationInput");
      }
    }
  }

  private static List<String> listArgs(String... values) {
    List<String> args = new ArrayList<>();
    for (String value : values) {
      if (value != null) {
        args.add(value);
      }
    }
    return args;
  }

  private static String runMessagevisor(Path projectPath, List<String> args) throws Exception {
    Path resolvedProjectPath = projectPath.toAbsolutePath().normalize();
    if (!resolvedProjectPath.toFile().isDirectory() && !projectPath.isAbsolute()) {
      resolvedProjectPath = Path.of("..").toAbsolutePath().normalize().resolve(projectPath).normalize();
    }
    List<String> command = new ArrayList<>();
    command.add("npx");
    command.add("messagevisor");
    command.addAll(args);
    ProcessResult result = runProcess(resolvedProjectPath, command);
    if (result.exitCode() != 0) {
      throw new IllegalStateException(
          String.join(" ", command)
              + " failed with exit code "
              + result.exitCode()
              + "\nStderr:\n"
              + result.stderr()
              + "\nStdout excerpt:\n"
              + excerpt(result.stdout()));
    }
    return result.stdout();
  }

  static ProcessResult runProcess(Path directory, List<String> command) throws Exception {
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    CompletableFuture<String> stdout =
        CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
    CompletableFuture<String> stderr =
        CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
    int exit = process.waitFor();
    return new ProcessResult(exit, stdout.get(), stderr.get());
  }

  private static String readStream(java.io.InputStream stream) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      stream.transferTo(output);
      return output.toString(StandardCharsets.UTF_8);
    } catch (Exception error) {
      throw new RuntimeException(error);
    }
  }

  private static String excerpt(String value) {
    if (value == null || value.length() <= 1000) {
      return value == null ? "" : value;
    }
    return value.substring(0, 1000) + "\n...";
  }

  private static DatafileContent buildDatafile(Path projectPath, String target, String locale) throws Exception {
    return readJson(runMessagevisor(projectPath, listArgs("build", "--json", "--target=" + target, "--locale=" + locale)), new TypeReference<>() {});
  }

  private static Map<String, Segment> loadSegments(Path projectPath) throws Exception {
    List<Segment> raw = readJson(runMessagevisor(projectPath, listArgs("list", "--segments", "--json")), new TypeReference<>() {});
    Map<String, Segment> result = new LinkedHashMap<>();
    for (Segment segment : raw) {
      if (segment.getKey() != null) {
        result.put(segment.getKey(), segment);
      }
    }
    return result;
  }

  private static List<String> runAssertion(
      SharedOptions shared,
      Map<String, Segment> segments,
      Map<String, DatafileContent> datafiles,
      Map<String, Object> test,
      Map<String, Object> assertion,
      String defaultTarget,
      boolean normalizeSpaces)
      throws Exception {
    List<String> failures = new ArrayList<>();
    if (test.get("segment") != null) {
      boolean actual =
          ConditionEvaluator.evaluateSegment(
              String.valueOf(test.get("segment")),
              new ConditionEvaluator.EvaluateOptions(map(assertion.get("context")), segments, null, null));
      if (!String.valueOf(assertion.get("expectedToMatch")).equals(String.valueOf(actual))) {
        failures.add("Segment mismatch: expected " + assertion.get("expectedToMatch") + ", got " + actual);
      }
      return failures;
    }

    if (test.get("message") != null) {
      runMessageAssertion(
          shared, datafiles, test, assertion, defaultTarget, normalizeSpaces, failures);
      return failures;
    }

    if (test.get("locale") != null) {
      runLocaleAssertion(
          shared, datafiles, test, assertion, defaultTarget, normalizeSpaces, failures);
      return failures;
    }

    if (test.get("target") != null) {
      runTargetAssertion(shared, datafiles, test, assertion, normalizeSpaces, failures);
      return failures;
    }

    failures.add("Unsupported test shape: expected message, segment, locale, or target");
    return failures;
  }

  private static void runMessageAssertion(
      SharedOptions shared,
      Map<String, DatafileContent> datafiles,
      Map<String, Object> test,
      Map<String, Object> assertion,
      String defaultTarget,
      boolean normalizeSpaces,
      List<String> failures) {
    String target = stringValue(assertion.get("target"));
    if (target == null) {
      target = defaultTarget;
    }
    String locale = String.valueOf(assertion.get("locale"));
    DatafileContent datafile =
        cachedDatafile(shared, datafiles, target, locale);
    Map<String, Boolean> flags = boolMap(assertion.get("withFlags"));
    Map<String, String> variations = stringMap(assertion.get("withVariations"));
    Messagevisor m = createTestMessagevisor(shared, datafile, assertion, flags, variations);
    String actual =
        m.translate(
            String.valueOf(test.get("message")),
            map(assertion.get("values")),
            translateOptions(assertion));
    compareTranslation(assertion, target, locale, actual, normalizeSpaces, failures);
  }

  private static void runLocaleAssertion(
      SharedOptions shared,
      Map<String, DatafileContent> datafiles,
      Map<String, Object> test,
      Map<String, Object> assertion,
      String defaultTarget,
      boolean normalizeSpaces,
      List<String> failures) {
    String target = stringValue(assertion.get("target"));
    if (target == null) {
      target = defaultTarget;
    }
    String locale = String.valueOf(test.get("locale"));
    DatafileContent datafile = cachedDatafile(shared, datafiles, target, locale);
    compareFormats(assertion, datafile, failures);

    if (assertion.get("rawMessage") == null) {
      return;
    }

    Messagevisor m = createTestMessagevisor(shared, datafile, assertion, Map.of(), Map.of());
    String actual =
        m.formatMessage(
            String.valueOf(assertion.get("rawMessage")),
            map(assertion.get("values")),
            evaluationOptions(assertion));
    compareTranslation(assertion, target, locale, actual, normalizeSpaces, failures);
  }

  private static void runTargetAssertion(
      SharedOptions shared,
      Map<String, DatafileContent> datafiles,
      Map<String, Object> test,
      Map<String, Object> assertion,
      boolean normalizeSpaces,
      List<String> failures) {
    String target = String.valueOf(test.get("target"));
    String locale = String.valueOf(assertion.get("locale"));
    DatafileContent datafile = cachedDatafile(shared, datafiles, target, locale);
    compareFormats(assertion, datafile, failures);

    for (Object message : list(assertion.get("expectedToIncludeMessages"))) {
      if (!datafile.getTranslations().containsKey(String.valueOf(message))) {
        failures.add("Expected datafile to include message: " + message);
      }
    }
    for (Object message : list(assertion.get("expectedToNotIncludeMessages"))) {
      if (datafile.getTranslations().containsKey(String.valueOf(message))) {
        failures.add("Expected datafile to not include message: " + message);
      }
    }

    if (assertion.get("rawMessage") == null && assertion.get("message") == null) {
      return;
    }

    Messagevisor m = createTestMessagevisor(shared, datafile, assertion, Map.of(), Map.of());
    String actual =
        assertion.get("rawMessage") != null
            ? m.formatMessage(
                String.valueOf(assertion.get("rawMessage")),
                map(assertion.get("values")),
                evaluationOptions(assertion))
            : m.translate(
                String.valueOf(assertion.get("message")),
                map(assertion.get("values")),
                translateOptions(assertion));
    compareTranslation(assertion, target, locale, actual, normalizeSpaces, failures);
  }

  private static DatafileContent cachedDatafile(
      SharedOptions shared,
      Map<String, DatafileContent> datafiles,
      String target,
      String locale) {
    return datafiles.computeIfAbsent(target + "/" + locale, key -> {
      try {
        return buildDatafile(shared.projectDirectoryPath, target, locale);
      } catch (Exception error) {
        throw new RuntimeException(error);
      }
    });
  }

  private static Messagevisor createTestMessagevisor(
      SharedOptions shared,
      DatafileContent datafile,
      Map<String, Object> assertion,
      Map<String, Boolean> flags,
      Map<String, String> variations) {
    return new Messagevisor(
        MessagevisorOptions.builder()
            .datafile(datafile)
            .context(map(assertion.get("context")))
            .logLevel(LogLevel.FATAL)
            .resolveFlag((key, context) -> Boolean.TRUE.equals(flags.get(key)))
            .resolveVariation((key, context) -> variations.get(key))
            .modules(shared.modules())
            .build());
  }

  private static EvaluationOptions evaluationOptions(Map<String, Object> assertion) {
    return EvaluationOptions.builder()
        .formats(formatPresets(assertion.get("formats")))
        .currency(stringValue(assertion.get("currency")))
        .timeZone(stringValue(assertion.get("timeZone")))
        .build();
  }

  private static TranslateOptions translateOptions(Map<String, Object> assertion) {
    return TranslateOptions.builder()
        .context(map(assertion.get("context")))
        .formats(formatPresets(assertion.get("formats")))
        .currency(stringValue(assertion.get("currency")))
        .timeZone(stringValue(assertion.get("timeZone")))
        .build();
  }

  private static void compareFormats(
      Map<String, Object> assertion,
      DatafileContent datafile,
      List<String> failures) {
    if (assertion.get("expectedFormats") == null) {
      return;
    }
    Object actual = JsonSupport.MAPPER.convertValue(datafile.getFormats(), Object.class);
    if (!containsSubset(actual, assertion.get("expectedFormats"))) {
      failures.add(
          "Formats subset mismatch\n      expected: "
              + assertion.get("expectedFormats")
              + "\n      actual: "
              + actual);
    }
  }

  private static void compareTranslation(
      Map<String, Object> assertion,
      String target,
      String locale,
      String actual,
      boolean normalizeSpaces,
      List<String> failures) {
    Object expectedByRuntime = assertion.get("expectedByRuntime");
    boolean usedRuntimeExpectation = map(expectedByRuntime).containsKey("java");
    Object expected = usedRuntimeExpectation ? map(expectedByRuntime).get("java") : assertion.get("expectedTranslation");
    String comparedExpected =
        normalizeSpaces ? normalizeSpaces(String.valueOf(expected)) : String.valueOf(expected);
    String comparedActual = normalizeSpaces ? normalizeSpaces(actual) : actual;
    if (!comparedExpected.equals(comparedActual)) {
      failures.add(
          "Translation mismatch"
              + "\n      runtime: java"
              + "\n      target: "
              + target
              + "\n      locale: "
              + locale
              + "\n      expectedSource: "
              + (usedRuntimeExpectation ? "expectedByRuntime.java" : "expectedTranslation")
              + "\n      expected: "
              + comparedExpected
              + "\n      actual: "
              + comparedActual
              + "\n      currency: "
              + assertion.getOrDefault("currency", "<default>")
              + "\n      timeZone: "
              + assertion.getOrDefault("timeZone", "<default>")
              + "\n      values: "
              + map(assertion.get("values")));
    }
  }

  private static String normalizeSpaces(String value) {
    return value.replace('\u00A0', ' ').replace('\u202F', ' ');
  }

  private static boolean containsSubset(Object actual, Object expected) {
    if (expected instanceof Map<?, ?> expectedMap) {
      if (!(actual instanceof Map<?, ?> actualMap)) {
        return false;
      }
      for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
        if (!actualMap.containsKey(entry.getKey())
            || !containsSubset(actualMap.get(entry.getKey()), entry.getValue())) {
          return false;
        }
      }
      return true;
    }
    if (expected instanceof List<?> expectedList) {
      if (!(actual instanceof List<?> actualList) || actualList.size() != expectedList.size()) {
        return false;
      }
      for (int i = 0; i < expectedList.size(); i++) {
        if (!containsSubset(actualList.get(i), expectedList.get(i))) {
          return false;
        }
      }
      return true;
    }
    return java.util.Objects.equals(actual, expected);
  }

  private static List<?> list(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private static String descriptionFor(Map<String, Object> test, Map<String, Object> assertion) {
    if (assertion.get("description") != null) {
      return String.valueOf(assertion.get("description"));
    }
    return String.valueOf(test.get("key"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    if (value instanceof Map<?, ?> raw) {
      Map<String, Object> result = new LinkedHashMap<>();
      raw.forEach((key, child) -> result.put(String.valueOf(key), child));
      return result;
    }
    return new LinkedHashMap<>();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listOfMaps(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    list.forEach(item -> result.add(map(item)));
    return result;
  }

  private static Map<String, Object> parseObject(String json) throws Exception {
    return json == null || json.isBlank()
        ? new LinkedHashMap<>()
        : readJson(json, new TypeReference<>() {});
  }

  private static Map<String, Boolean> boolMap(Object value) {
    Map<String, Boolean> result = new LinkedHashMap<>();
    map(value).forEach((key, child) -> result.put(key, Boolean.TRUE.equals(child)));
    return result;
  }

  private static Map<String, String> stringMap(Object value) {
    Map<String, String> result = new LinkedHashMap<>();
    map(value).forEach((key, child) -> result.put(key, child == null ? null : String.valueOf(child)));
    return result;
  }

  private static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static DatafileContent datafile(Object value) {
    return JsonSupport.MAPPER.convertValue(value, DatafileContent.class);
  }

  private static FormatPresets formatPresets(Object value) {
    return value == null ? null : JsonSupport.MAPPER.convertValue(value, FormatPresets.class);
  }

  private static Map<String, FormatPresets> formatPresetsByLocale(Object value) {
    Map<String, FormatPresets> result = new LinkedHashMap<>();
    map(value).forEach((locale, formats) -> result.put(locale, formatPresets(formats)));
    return result;
  }

  private static <T> T readJson(String json, TypeReference<T> type) throws Exception {
    return JsonSupport.MAPPER.readValue(json, type);
  }

  private static void printResult(Map<String, Object> output, boolean json, boolean pretty, String plain)
      throws Exception {
    if (json) {
      printJson(output, pretty);
    } else {
      System.out.println(plain);
    }
  }

  private static void printExamplesPlain(Map<String, Object> result) throws Exception {
    List<Map<String, Object>> localeExamples = listOfMaps(result.get("locales"));
    List<Map<String, Object>> messageExamples = listOfMaps(result.get("messages"));
    System.out.println(BOLD + "Messagevisor Java examples" + RESET);
    System.out.printf("  Locale examples:  %d%n", localeExamples.size());
    System.out.printf("  Message examples: %d%n", messageExamples.size());

    if (!localeExamples.isEmpty()) {
      System.out.println();
      System.out.println(BOLD + "Locales" + RESET);
      printGroupedExamples(localeExamples, "locale", "Locale");
    }
    if (!messageExamples.isEmpty()) {
      System.out.println();
      System.out.println(BOLD + "Messages" + RESET);
      printGroupedExamples(messageExamples, "message", "Message");
    }
    System.out.println();
    System.out.println(GREEN + "Found " + (localeExamples.size() + messageExamples.size()) + " examples." + RESET);
    System.out.println(DIM + "Tip: use --json --pretty for structured output." + RESET);
  }

  private static void printGroupedExamples(
      List<Map<String, Object>> examples, String groupKey, String groupLabel) throws Exception {
    String currentGroup = null;
    for (Map<String, Object> example : examples) {
      String group = String.valueOf(example.get(groupKey));
      if (!group.equals(currentGroup)) {
        currentGroup = group;
        System.out.println();
        System.out.println(BOLD + groupLabel + " \"" + group + "\":" + RESET);
      }
      printExample(example);
    }
  }

  private static void printExample(Map<String, Object> example) throws Exception {
    Object index = example.get("exampleIndex");
    String sourceLocale = stringValue(example.get("sourceLocale"));
    String heading =
        "  Example #"
            + (index instanceof Number number ? number.intValue() + 1 : index)
            + (sourceLocale == null ? "" : " · from " + sourceLocale);
    System.out.println(CYAN + heading + RESET);
    printExampleField("Description", example.get("description"));
    printExampleField("Message", example.get("message"));
    printExampleField("Raw message", example.get("rawMessage"));
    printJsonExampleField("Context", example.get("context"));
    printJsonExampleField("Values", example.get("values"));
    printExampleField("Currency", example.get("currency"));
    printExampleField("Time zone", example.get("timeZone"));
    printExampleField("Evaluated translation", "\"" + stringValue(example.get("evaluatedTranslation")) + "\"", GREEN);
    System.out.println();
  }

  private static void printExampleField(String label, Object value) {
    printExampleField(label, value, "");
  }

  private static void printExampleField(String label, Object value, String valueColor) {
    if (value == null) {
      return;
    }
    System.out.println(DIM + "    " + label + ":" + RESET);
    System.out.println("      " + valueColor + value + RESET);
  }

  private static void printJsonExampleField(String label, Object value) throws Exception {
    if (value == null) {
      return;
    }
    printExampleField(label, JsonSupport.MAPPER.writeValueAsString(value));
  }

  private static void printJson(Object output, boolean pretty) throws Exception {
    if (pretty) {
      System.out.println(JsonSupport.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    } else {
      System.out.println(JsonSupport.MAPPER.writeValueAsString(output));
    }
  }

  record ProcessResult(int exitCode, String stdout, String stderr) {}
}
