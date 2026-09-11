package org.folio.template.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;

class HandlebarsTemplateResolverTest {

  private final HandlebarsTemplateResolver resolver = new HandlebarsTemplateResolver();

  private String render(String body, JsonObject context) {
    Future<JsonObject> future = resolver.processTemplate(
      new JsonObject().put("body", body), context, "text/html");
    assertTrue(future.succeeded(),
      () -> "render failed: " + (future.cause() == null ? "" : future.cause().getMessage()));
    return future.result().getString("body");
  }

  private void assertClientError(String body, JsonObject context) {
    Future<JsonObject> future = resolver.processTemplate(
      new JsonObject().put("body", body), context, "text/html");
    assertTrue(future.failed());
    assertInstanceOf(ServiceException.class, future.cause());
    assertEquals(400, ((ServiceException) future.cause()).failureCode());
  }

  @Test
  void plainTokenSubstitution() {
    JsonObject context = new JsonObject().put("user", new JsonObject().put("name", "Ada"));
    assertEquals("Hello Ada", render("Hello {{user.name}}", context));
  }

  @Test
  void missingTokenRendersEmptyString() {
    assertEquals("Hello ", render("Hello {{user.name}}", new JsonObject()));
  }

  @Test
  void conditionalAndEachAndComparisonHelpers() {
    JsonObject context = new JsonObject()
      .put("count", 7)
      .put("items", new JsonArray().add("a").add("b").add("c"));
    assertEquals("big", render("{{#if (gt count 5)}}big{{else}}small{{/if}}", context));
    assertEquals("abc", render("{{#each items}}{{this}}{{/each}}", context));
  }

  @Test
  void lookupSelectsArrayElementByIndex() {
    JsonObject context = new JsonObject()
      .put("user", new JsonObject().put("phrases", new JsonArray().add("first").add("second")));
    assertEquals("second", render("{{lookup user.phrases 1}}", context));
  }

  @Test
  void htmlEscapingSetIsPinned() {
    // jknack default HTML_ENTITY escaping is a superset of Mustache: & < > " ' PLUS backtick and =.
    JsonObject context = new JsonObject().put("val", "<>&\"'`=");
    assertEquals("&lt;&gt;&amp;&quot;&#x27;&#x60;&#x3D;", render("{{val}}", context));
    // triple-mustache emits raw (barcode tokens rely on this)
    assertEquals("<>&\"'`=", render("{{{val}}}", context));
  }

  @Test
  void moduleHelpersAreRegistered() {
    // Helper behaviour is covered by ModuleHelpersTest; this only checks the resolver wires them in.
    JsonObject context = new JsonObject()
      .put("notes", "a\nb")
      .put("format", "P/E Mix");
    assertEquals("a<br>b", render("{{nl2br notes}}", context));
    assertEquals("yes", render("{{#equalsAny format \"P/E Mix\"}}yes{{/equalsAny}}", context));
  }

  @Test
  void stringHelpersAreNotRegistered() {
    assertClientError("{{upper name}}", new JsonObject().put("name", "Ada"));
  }

  @Test
  void helperArgumentErrorFailsWithClientError() {
    assertClientError("{{nl2sep notes}}", new JsonObject().put("notes", "a\nb"));
  }

  @Test
  void malformedTemplateFailsWithClientError() {
    assertClientError("{{#if x}}never closed", new JsonObject());
  }
}
