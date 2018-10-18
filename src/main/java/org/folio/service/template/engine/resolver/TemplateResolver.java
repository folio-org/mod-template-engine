package org.folio.service.template.engine.resolver;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Service interface for template resolvers
 */
@ProxyGen
public interface TemplateResolver {

  static TemplateResolver createProxy(Vertx vertx, String address) {
    return new TemplateResolverVertxEBProxy(vertx, address);
  }

  /**
   * Processes template with given context and returns result to result handler in requested output format
   * @param template template
   * @param context context
   * @param outputFormat output format
   * @param resultHandler result handler
   */
  void processTemplate(String template, JsonObject context, String outputFormat, Handler<AsyncResult<String>> resultHandler);
}
