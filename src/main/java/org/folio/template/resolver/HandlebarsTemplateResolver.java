package org.folio.template.resolver;

import static org.folio.HttpStatus.SC_BAD_REQUEST;

import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.template.resolver.helper.ModuleHelpers;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.helper.ConditionalHelpers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;

public class HandlebarsTemplateResolver implements TemplateResolver {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");

  private final Handlebars handlebars;

  public HandlebarsTemplateResolver() {
    // Single reusable instance. ConcurrentMapTemplateCache avoids recompiling stored
    // templates on every render (H4); the default NullTemplateCache would recompile on
    // the event-loop thread each time. Cache size is bounded by the set of stored templates.
    // Keep the default HTML escaping (a superset of Mustache - escapes & < > " ' plus
    // backtick and =); set it explicitly for clarity (H5). Missing tokens render as empty
    // string (lenient mode, matching Mustache).
    this.handlebars = new Handlebars()
      .with(EscapingStrategy.HTML_ENTITY)
      .with(new ConcurrentMapTemplateCache());
    this.handlebars.registerHelpers(ConditionalHelpers.class);
    ModuleHelpers.register(this.handlebars);
  }

  @Override
  public Future<JsonObject> processTemplate(JsonObject templateContent, JsonObject context, String outputFormat) {
    LOG.debug("processTemplate:: Processing Template");
    JsonObject result = new JsonObject();
    try {
      Map<String, Object> contextMap = Optional.of(context)
        .map(jsonObject -> jsonObject.mapTo(org.folio.rest.jaxrs.model.Context.class))
        .map(org.folio.rest.jaxrs.model.Context::getAdditionalProperties)
        .orElse(null);
      for (Map.Entry<String, Object> property : templateContent) {
        if (property.getValue() instanceof String) {
          result.put(property.getKey(), processTemplateProperty(property.getValue().toString(), contextMap));
        }
      }
      return Future.succeededFuture(result);
    } catch (HandlebarsException e) {
      // Malformed author syntax (unclosed block, unknown helper) is a client error, not a
      // server fault. The original type is erased across the EventBus service proxy, so we
      // signal it with a 400 failure code that mapExceptionToResponse translates to HTTP 400 (H2).
      LOG.warn("Malformed Handlebars template: {}", e.getMessage());
      return Future.failedFuture(new ServiceException(SC_BAD_REQUEST,
        "Failed to process template: " + e.getMessage()));
    } catch (Exception e) {
      LOG.warn("Failed to Process Template {}", e.getMessage());
      return Future.failedFuture(e);
    }
  }

  private String processTemplateProperty(String templateProperty, Map<String, Object> contextMap) throws java.io.IOException {
    LOG.debug("processTemplateProperty:: Processing template property");
    Context context = Context.newContext(contextMap);
    String processed = handlebars.compileInline(templateProperty).apply(context);
    LOG.info("processTemplateProperty:: Processed template property");
    return processed;
  }
}
