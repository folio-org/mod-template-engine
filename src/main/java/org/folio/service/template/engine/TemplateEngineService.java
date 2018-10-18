package org.folio.service.template.engine;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;


@ProxyGen
public interface TemplateEngineService {

  static TemplateEngineService create(Vertx vertx) {
    return new TemplateEngineServiceImpl(vertx);
  }

  static TemplateEngineService createProxy(Vertx vertx, String address) {
    return new TemplateEngineServiceVertxEBProxy(vertx, address);
  }

  /**
   * Processes given templateJson ({@link org.folio.rest.jaxrs.model.Template})
   * using information from templateProcessingRequestJson ({@link org.folio.rest.jaxrs.model.TemplateProcessingRequest})
   * and forms result as {@link org.folio.rest.jaxrs.model.TemplateProcessingResult}
   * @param templateJson given template to process
   * @param templateProcessingRequestJson processing request info
   * @param asyncResultHandler result handler
   */
  void processTemplate(JsonObject templateJson, JsonObject templateProcessingRequestJson, Handler<AsyncResult<JsonObject>> asyncResultHandler);
}
