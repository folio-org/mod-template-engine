package org.folio.service.template.engine.resolver;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface TemplateResolver {

  static TemplateResolver createProxy(Vertx vertx, String address) {
    return new TemplateResolverVertxEBProxy(vertx, address);
  }

  void processTemplate(String template, JsonObject context, String outputFormat, Handler<AsyncResult<String>> resultHandler);
}
