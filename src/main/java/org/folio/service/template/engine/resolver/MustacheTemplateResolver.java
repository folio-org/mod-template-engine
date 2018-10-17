package org.folio.service.template.engine.resolver;

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
import java.util.Collections;

public class MustacheTemplateResolver implements TemplateResolver {

  private MustacheFactory mustacheFactory;

  public MustacheTemplateResolver() {
    mustacheFactory = new DefaultMustacheFactory();
  }

  @Override
  public void processTemplate(String template, JsonObject context, String outputFormat, Handler<AsyncResult<String>> resultHandler) {
    try {
      Mustache mustache = mustacheFactory.compile(new StringReader(template), null);
      StringWriter writer = new StringWriter();
      mustache.run(writer, Collections.singletonList(context.mapTo(Context.class).getAdditionalProperties()));

      resultHandler.handle(Future.succeededFuture(writer.toString()));
    } catch (Exception e) {
      resultHandler.handle(Future.failedFuture(e));
    }
  }
}
