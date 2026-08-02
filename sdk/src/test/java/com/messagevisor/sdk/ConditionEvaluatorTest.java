package com.messagevisor.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConditionEvaluatorTest {
  @Test
  void evaluatesPortableConditionOperators() {
    Map<String, Object> context =
        TestDatafiles.map(
            "platform", "web",
            "age", 42,
            "name", "Ada Lovelace",
            "roles", List.of("admin"),
            "createdAt", "2026-01-02T00:00:00Z",
            "account", TestDatafiles.map("plan", "pro"));
    ConditionEvaluator.EvaluateOptions options =
        new ConditionEvaluator.EvaluateOptions(context, Map.of(), null, null);

    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "platform", "operator", "equals", "value", "web"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "account.plan", "operator", "equals", "value", "pro"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "roles", "operator", "includes", "value", "admin"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "createdAt", "operator", "after", "value", "2026-01-01T00:00:00Z"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "platform", "operator", "notEquals", "value", "ios"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "missing", "operator", "notExists"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "age", "operator", "lessThanOrEquals", "value", 42), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "name", "operator", "contains", "value", "Love"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "name", "operator", "notContains", "value", "Grace"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "name", "operator", "startsWith", "value", "Ada"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "name", "operator", "endsWith", "value", "Lovelace"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "roles", "operator", "notIncludes", "value", "owner"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "platform", "operator", "in", "value", List.of("web", "mobile")), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("attribute", "platform", "operator", "notIn", "value", List.of("ios", "android")), options))
        .isTrue();
  }

  @Test
  void keepsOperatorsTypeStrictAndPortable() {
    Map<String, Object> context = TestDatafiles.map(
        "number", 42,
        "numericString", "42",
        "text", "Hello\nWORLD",
        "nullable", null,
        "scalar", "admin",
        "date", "2026-01-02T00:00:00Z",
        "dateOnly", "2026-01-02");
    var options = new ConditionEvaluator.EvaluateOptions(context, Map.of(), null, null);

    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "nullable", "operator", "exists"), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "numericString", "operator", "greaterThan", "value", 1), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "number", "operator", "contains", "value", "4"), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "scalar", "operator", "notIncludes", "value", "viewer"), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "scalar", "operator", "notIn", "value", "viewer"), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "text", "operator", "matches", "value", "^world$", "regexFlags", "im"), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "text", "operator", "notMatches", "value", "missing", "regexFlags", "i"), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "text", "operator", "matches", "value", "["), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "text", "operator", "matches", "value", "Hello", "regexFlags", "g"), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "date", "operator", "before", "value", "2026-02-01T00:00:00+01:00"), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
        TestDatafiles.map("attribute", "dateOnly", "operator", "before", "value", "2026-02-01T00:00:00Z"), options)).isFalse();
  }

  @Test
  void rejectsNonportableRegexSyntax() {
    var options = new ConditionEvaluator.EvaluateOptions(Map.of("text", "valuex"), Map.of(), null, null);
    for (String pattern : List.of(
        "value(?=x)",
        "(?<=x)value",
        "(?:value)",
        "(?<name>value)",
        "(value)\\1",
        "(?<name>value)\\k<name>",
        "value++")) {
      assertThat(ConditionEvaluator.evaluateCondition(
          TestDatafiles.map("attribute", "text", "operator", "matches", "value", pattern), options))
          .as(pattern)
          .isFalse();
      assertThat(ConditionEvaluator.evaluateCondition(
          TestDatafiles.map("attribute", "text", "operator", "notMatches", "value", pattern), options))
          .as(pattern)
          .isFalse();
    }
  }

  @Test
  void evaluatesConditionStringsGroupsAndResolverConditions() {
    ConditionEvaluator.EvaluateOptions options =
        new ConditionEvaluator.EvaluateOptions(
            TestDatafiles.map("platform", "web", "plan", "pro"),
            Map.of(),
            (key, context) -> key.equals("new-checkout"),
            (key, context) -> key.equals("checkout-copy") ? "b" : null);

    assertThat(ConditionEvaluator.evaluateCondition(
            "{\"attribute\":\"plan\",\"operator\":\"equals\",\"value\":\"pro\"}", options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            "[{\"attribute\":\"platform\",\"operator\":\"equals\",\"value\":\"web\"},{\"attribute\":\"plan\",\"operator\":\"equals\",\"value\":\"pro\"}]", options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition("not-json", options)).isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map(
                "and",
                List.of(
                    TestDatafiles.map("attribute", "platform", "operator", "equals", "value", "web"),
                    TestDatafiles.map("attribute", "plan", "operator", "equals", "value", "pro"))),
            options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map(
                "or",
                List.of(
                    TestDatafiles.map("attribute", "platform", "operator", "equals", "value", "ios"),
                    TestDatafiles.map("attribute", "plan", "operator", "equals", "value", "pro"))),
            options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map(
                "not",
                List.of(
                    TestDatafiles.map("attribute", "platform", "operator", "equals", "value", "web"),
                    TestDatafiles.map("attribute", "plan", "operator", "equals", "value", "pro"))),
            options))
        .isFalse();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("feature", "new-checkout", "operator", "isEnabled"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("feature", "old-checkout", "operator", "isDisabled"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(
            TestDatafiles.map("experiment", "checkout-copy", "operator", "hasVariation", "value", "b"), options))
        .isTrue();
    assertThat(ConditionEvaluator.evaluateCondition(TestDatafiles.map("not", List.of()), options))
        .isFalse();
  }

  @Test
  void evaluatesSegmentsAndArchivedSegments() {
    Segment active = new Segment();
    active.setConditions(TestDatafiles.map("attribute", "platform", "operator", "equals", "value", "web"));
    Segment archived = new Segment();
    archived.setArchived(true);
    archived.setConditions("*");

    ConditionEvaluator.EvaluateOptions options =
        new ConditionEvaluator.EvaluateOptions(
            TestDatafiles.map("platform", "web"),
            Map.of("active", active, "archived", archived),
            null,
            null);

    assertThat(ConditionEvaluator.evaluateGroupSegment("active", options)).isTrue();
    assertThat(ConditionEvaluator.evaluateGroupSegment("archived", options)).isFalse();
  }

  @Test
  void evaluatesSegmentGroupsAndStringifiedSegmentGroups() {
    Segment web = new Segment();
    web.setConditions(TestDatafiles.map("attribute", "platform", "operator", "equals", "value", "web"));
    Segment pro = new Segment();
    pro.setConditions("{\"attribute\":\"plan\",\"operator\":\"equals\",\"value\":\"pro\"}");

    ConditionEvaluator.EvaluateOptions options =
        new ConditionEvaluator.EvaluateOptions(
            TestDatafiles.map("platform", "web", "plan", "pro"),
            Map.of("web", web, "pro", pro),
            null,
            null);

    assertThat(ConditionEvaluator.evaluateGroupSegment(List.of("web", "pro"), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateGroupSegment(TestDatafiles.map("and", List.of("web", "pro")), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateGroupSegment(TestDatafiles.map("or", List.of("missing", "pro")), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateGroupSegment(TestDatafiles.map("not", List.of("web", "missing")), options)).isTrue();
    assertThat(ConditionEvaluator.evaluateGroupSegment(TestDatafiles.map("not", List.of("web", "pro")), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateGroupSegment("{\"and\":[\"web\",\"pro\"]}", options)).isTrue();
    assertThat(ConditionEvaluator.evaluateGroupSegment("{\"and\":", options)).isFalse();
    assertThat(ConditionEvaluator.evaluateGroupSegment(TestDatafiles.map("nor", List.of("web")), options)).isFalse();
    assertThat(ConditionEvaluator.evaluateGroupSegment(TestDatafiles.map("not", List.of()), options)).isFalse();
  }
}
