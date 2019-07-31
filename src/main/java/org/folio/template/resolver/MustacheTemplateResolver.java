package org.folio.template.resolver;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Context;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;

public class MustacheTemplateResolver implements TemplateResolver {

  private MustacheFactory mustacheFactory;

  public MustacheTemplateResolver() {
    mustacheFactory = new DefaultMustacheFactory();
  }

  @Override
  public void processTemplate(JsonObject templateContent, JsonObject context, String outputFormat, Handler<AsyncResult<JsonObject>> resultHandler) {
    JsonObject result = new JsonObject();
    try {
      for (Map.Entry<String, Object> property : templateContent) {
        if (property.getValue() instanceof String) {
          String processedPropertyValue = processTemplateProperty(property.getValue().toString(), context);
          result.put(property.getKey(), processedPropertyValue);
        }
      }
      resultHandler.handle(Future.succeededFuture(result));
    } catch (Exception e) {
      resultHandler.handle(Future.failedFuture(e));
    }
  }

  private String processTemplateProperty(String templateProperty, JsonObject context) {
    Mustache mustache = mustacheFactory.compile(new StringReader(templateProperty), null);
    StringWriter writer = new StringWriter();
    Map<String, Object> contextMap = Optional.of(context)
      .map(jsonObject -> jsonObject.mapTo(Context.class))
      .map(Context::getAdditionalProperties)
      .orElse(null);
    mustache.execute(writer, contextMap);
    return writer.toString();
  }
}
