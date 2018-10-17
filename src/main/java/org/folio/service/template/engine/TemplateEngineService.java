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

  void processTemplate(JsonObject template, JsonObject templateProcessingRequestJson, Handler<AsyncResult<JsonObject>> asyncResultHandler);
}
