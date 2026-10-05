package org.folio.template.resolver.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;

import org.folio.template.util.TemplateEngineHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ModuleHelpersTest {

  private Handlebars handlebars;

  @BeforeEach
  void setUp() {
    handlebars = new Handlebars().with(EscapingStrategy.HTML_ENTITY);
    ModuleHelpers.register(handlebars);
  }

  // Mirrors HandlebarsTemplateResolver: the context reaches Handlebars as plain maps and lists.
  @SuppressWarnings("unchecked")
  private String render(String template, JsonObject context) throws IOException {
    Map<String, Object> contextMap = context.mapTo(Map.class);
    return handlebars.compileInline(template).apply(Context.newContext(contextMap));
  }

  private JsonObject tenantLocale(String languageTag) {
    return new JsonObject().put(TemplateEngineHelper.TENANT_LOCALE_CONTEXT_KEY, languageTag);
  }

  @ParameterizedTest
  @EnumSource(ModuleHelpers.class)
  void everyHelperIsRegisteredUnderItsHelperName(ModuleHelpers helper) {
    assertSame(helper, handlebars.helper(helper.helperName()));
    // the upper-case constant name is not a helper name
    assertNull(handlebars.helper(helper.name()));
  }

  // nl2br

  @Test
  void nl2brReplacesEveryLineBreakStyleWithBrAndEscapesValue() throws IOException {
    JsonObject context = new JsonObject().put("notes", "<b>one</b>\r\ntwo\rthree\nfour & five");
    assertEquals("&lt;b&gt;one&lt;/b&gt;<br>two<br>three<br>four &amp; five", render("{{nl2br notes}}", context));
  }

  @Test
  void nl2brRendersMissingValueAsEmpty() throws IOException {
    assertEquals("", render("{{nl2br missing}}", new JsonObject()));
  }

  // nl2sep

  @Test
  void nl2sepReplacesEveryLineBreakStyle() throws IOException {
    JsonObject context = new JsonObject().put("notes", "one\r\ntwo\rthree\nfour");
    assertEquals("one | two | three | four", render("{{nl2sep notes \" | \"}}", context));
  }

  @Test
  void nl2sepUsesGivenSeparator() throws IOException {
    JsonObject context = new JsonObject().put("notes", "Main St 1\n12345 Town");
    assertEquals("Main St 1, 12345 Town", render("{{nl2sep notes \", \"}}", context));
    // regex replacement metacharacters ($ and \) in the separator are emitted literally
    assertEquals("Main St 1 $1 \\\\ 12345 Town", render("{{nl2sep notes \" $1 \\\\ \"}}", context));
  }

  @Test
  void nl2sepEscapesValueButNotSeparator() throws IOException {
    JsonObject context = new JsonObject().put("notes", "<b>a</b>\nb & c");
    assertEquals("&lt;b&gt;a&lt;/b&gt;<hr>b &amp; c", render("{{nl2sep notes \"<hr>\"}}", context));
  }

  @Test
  void nl2sepRendersMissingValueAsEmpty() throws IOException {
    assertEquals("", render("{{nl2sep missing \", \"}}", new JsonObject()));
  }

  @Test
  void nl2sepAllowsEmptySeparator() throws IOException {
    JsonObject context = new JsonObject().put("notes", "a\nb");
    assertEquals("ab", render("{{nl2sep notes \"\"}}", context));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{{nl2sep notes}}", "{{nl2sep notes undefinedToken}}", "{{nl2sep missing}}"})
  void nl2sepWithoutSeparatorFails(String template) {
    JsonObject context = new JsonObject().put("notes", "a\nb");
    HandlebarsException exception = assertThrows(HandlebarsException.class, () -> render(template, context));
    assertTrue(exception.getMessage().contains("nl2sep requires a separator"), exception::getMessage);
  }

  // numberFormat

  @Test
  void numberFormatUsesExplicitLocaleAndDecimals() throws IOException {
    JsonObject context = new JsonObject().put("amount", 1234.5);
    assertEquals("1.234,50", render("{{numberFormat amount locale=\"de-DE\" minDecimals=2 maxDecimals=2}}", context));
  }

  @Test
  void numberFormatRoundsToMaxDecimals() throws IOException {
    JsonObject context = new JsonObject().put("amount", 1234.567);
    assertEquals("1,234.6", render("{{numberFormat amount locale=\"en-US\" maxDecimals=1}}", context));
  }

  @Test
  void numberFormatDefaultsToTenantLocale() throws IOException {
    JsonObject context = tenantLocale("de-DE").put("amount", 1234.5);
    assertEquals("1.234,5", render("{{numberFormat amount}}", context));
  }

  @Test
  void numberFormatExplicitLocaleOverridesTenantLocale() throws IOException {
    JsonObject context = tenantLocale("de-DE").put("amount", 1234.5);
    assertEquals("1,234.5", render("{{numberFormat amount locale=\"en-US\"}}", context));
  }

  @Test
  void numberFormatFallsBackToEnUsWithoutTenantLocale() throws IOException {
    JsonObject context = new JsonObject().put("amount", 1234.5);
    assertEquals("1,234.5", render("{{numberFormat amount}}", context));
  }

  @Test
  void numberFormatParsesNumericString() throws IOException {
    JsonObject context = new JsonObject().put("price", "19.9");
    assertEquals("19,90", render("{{numberFormat price locale=\"de-DE\" minDecimals=2}}", context));
  }

  @Test
  void numberFormatRendersMissingValueAsEmpty() throws IOException {
    assertEquals("", render("{{numberFormat missing minDecimals=2}}", new JsonObject()));
  }

  @Test
  void numberFormatFailsForNonNumericValue() {
    JsonObject context = new JsonObject().put("price", "abc");
    assertThrows(HandlebarsException.class, () -> render("{{numberFormat price}}", context));
  }

  // dateFormat

  @Test
  void dateFormatRendersLocalizedMediumDateForExplicitLocale() throws IOException {
    JsonObject context = new JsonObject().put("date", "2026-09-11");
    assertEquals("11.09.2026", render("{{dateFormat date locale=\"de-DE\"}}", context));
  }

  @Test
  void dateFormatDefaultsToTenantLocale() throws IOException {
    JsonObject context = tenantLocale("de-DE").put("date", "2026-09-11");
    assertEquals("11.09.2026", render("{{dateFormat date}}", context));
  }

  @Test
  void dateFormatFallsBackToEnUsWithoutTenantLocale() throws IOException {
    JsonObject context = new JsonObject().put("date", "2026-09-11");
    assertEquals("Sep 11, 2026", render("{{dateFormat date}}", context));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-09-11T10:15:30.123Z", "2026-09-11T10:15:30+02:00", "2026-09-11T10:15:30"})
  void dateFormatAppliesPatternToDateTimes(String value) throws IOException {
    JsonObject context = new JsonObject().put("date", value);
    assertEquals("11/09/2026 10:15", render("{{dateFormat date pattern=\"dd/MM/yyyy HH:mm\"}}", context));
  }

  @Test
  void dateFormatAppliesLocaleToPattern() throws IOException {
    JsonObject context = new JsonObject().put("date", "2026-09-11");
    assertEquals("11. September 2026", render("{{dateFormat date locale=\"de-DE\" pattern=\"d. MMMM yyyy\"}}", context));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not a date", "11.09.2026"})
  void dateFormatReturnsUnparseableValueUnchanged(String value) throws IOException {
    JsonObject context = new JsonObject().put("date", value);
    assertEquals(value, render("{{dateFormat date}}", context));
  }

  @Test
  void dateFormatReturnsValueUnchangedWhenPatternDoesNotFitValue() throws IOException {
    JsonObject context = new JsonObject().put("date", "2026-09-11");
    assertEquals("2026-09-11", render("{{dateFormat date pattern=\"HH:mm\"}}", context));
  }

  @Test
  void dateFormatRendersMissingOrBlankValueAsEmpty() throws IOException {
    assertEquals("", render("{{dateFormat missing}}", new JsonObject()));
    assertEquals("", render("{{dateFormat date}}", new JsonObject().put("date", "  ")));
  }

  // equalsAny

  @Test
  void equalsAnyMatchesAnyCandidateAsSubexpression() throws IOException {
    JsonObject context = new JsonObject().put("format", "P/E Mix");
    assertEquals("yes", render("{{#if (equalsAny format \"Physical Resource\" \"P/E Mix\")}}yes{{else}}no{{/if}}", context));
    // whole-string equality, not substring
    assertEquals("no", render("{{#if (equalsAny format \"Mix\")}}yes{{else}}no{{/if}}", context));
    // missing/null value yields false rather than failing
    assertEquals("no", render("{{#if (equalsAny missing \"x\")}}yes{{else}}no{{/if}}", new JsonObject()));
  }

  @Test
  void equalsAnyWorksAsBlockHelper() throws IOException {
    JsonObject context = new JsonObject().put("format", "P/E Mix");
    assertEquals("yes", render("{{#equalsAny format \"Physical Resource\" \"P/E Mix\"}}yes{{else}}no{{/equalsAny}}", context));
    assertEquals("no", render("{{#equalsAny format \"Physical Resource\"}}yes{{else}}no{{/equalsAny}}", context));
  }

  @Test
  void equalsAnyRendersBooleanInline() throws IOException {
    JsonObject context = new JsonObject().put("format", "P/E Mix");
    assertEquals("true", render("{{equalsAny format \"P/E Mix\"}}", context));
    assertEquals("false", render("{{equalsAny format \"Physical Resource\"}}", context));
  }

  @Test
  void equalsAnyComparesByStringForm() throws IOException {
    JsonObject context = new JsonObject().put("qty", 2);
    assertEquals("yes", render("{{#equalsAny qty \"1\" \"2\"}}yes{{else}}no{{/equalsAny}}", context));
  }

  // where

  private JsonObject contributorsContext() {
    return new JsonObject()
      .put("label", "Authors")
      .put("contributors", new JsonArray()
        .add(contributor("Ada", "Personal name"))
        .add(contributor("ACME Corp", "Corporate name"))
        .add(contributor("Grace", "Personal name")));
  }

  private JsonObject contributor(String name, String type) {
    return new JsonObject()
      .put("contributor", name)
      .put("contributorNameType", new JsonObject().put("name", type));
  }

  @Test
  void whereRendersOnlyElementsMatchingNestedPath() throws IOException {
    assertEquals("Ada; Grace", render(
      "{{#where contributors \"contributorNameType.name\" \"Personal name\"}}"
        + "{{#unless @first}}; {{/unless}}{{contributor}}{{/where}}",
      contributorsContext()));
  }

  @Test
  void whereLoopVariablesCountFilteredMatchesOnly() throws IOException {
    // @index/@index_1/@last must refer to the filtered list, not the source list, where
    // Grace is the third element.
    assertEquals("0/1:Ada,1/2:Grace.", render(
      "{{#where contributors \"contributorNameType.name\" \"Personal name\"}}"
        + "{{@index}}/{{@index_1}}:{{contributor}}{{#if @last}}.{{else}},{{/if}}{{/where}}",
      contributorsContext()));
  }

  @Test
  void whereSupportsBlockParamsAndParentContext() throws IOException {
    assertEquals("Authors 0=Ada Authors 1=Grace ", render(
      "{{#where contributors \"contributorNameType.name\" \"Personal name\" as |person idx|}}"
        + "{{../label}} {{idx}}={{person.contributor}} {{/where}}",
      contributorsContext()));
  }

  @Test
  void whereRendersElseBlockWhenNothingMatches() throws IOException {
    assertEquals("none", render(
      "{{#where contributors \"contributorNameType.name\" \"Meeting name\"}}{{contributor}}{{else}}none{{/where}}",
      contributorsContext()));
  }

  @Test
  void whereRendersElseBlockWhenValueIsNotAList() throws IOException {
    JsonObject context = new JsonObject().put("contributors", "Ada");
    assertEquals("none", render(
      "{{#where contributors \"contributorNameType.name\" \"Personal name\"}}x{{else}}none{{/where}}", context));
    assertEquals("none", render(
      "{{#where missing \"contributorNameType.name\" \"Personal name\"}}x{{else}}none{{/where}}", new JsonObject()));
  }

  @Test
  void whereComparesByStringForm() throws IOException {
    JsonObject context = new JsonObject().put("lines", new JsonArray()
      .add(new JsonObject().put("qty", 2).put("title", "A"))
      .add(new JsonObject().put("qty", 3).put("title", "B")));
    assertEquals("A", render("{{#where lines \"qty\" \"2\"}}{{title}}{{/where}}", context));
  }

  @Test
  void whereWithNullExpectedMatchesElementsWithoutValue() throws IOException {
    // An absent variable as the expected value resolves to null and matches only absent/null paths.
    JsonObject context = new JsonObject().put("lines", new JsonArray()
      .add(new JsonObject().put("code", "X").put("title", "A"))
      .add(new JsonObject().put("title", "B")));
    assertEquals("B", render("{{#where lines \"code\" undefinedToken}}{{title}}{{/where}}", context));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{{#where contributors}}x{{/where}}", "{{#where contributors \"contributorNameType.name\"}}x{{/where}}"})
  void whereWithoutPathOrExpectedValueFails(String template) {
    JsonObject context = contributorsContext();
    assertThrows(HandlebarsException.class, () -> render(template, context));
  }
}
