package org.folio.template.resolver;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
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
   *
   * @param templateContent templateContent
   * @param context         context
   * @param outputFormat    output format
   * @return Future containing the processed template as JsonObject
   */
  Future<JsonObject> processTemplate(JsonObject templateContent, JsonObject context, String outputFormat);
}
