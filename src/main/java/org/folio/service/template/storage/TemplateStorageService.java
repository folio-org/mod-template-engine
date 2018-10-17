package org.folio.service.template.storage;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface TemplateStorageService {

  static TemplateStorageService create(Vertx vertx) {
    return new TemplateStorageServiceImpl(vertx);
  }

  static TemplateStorageService createProxy(Vertx vertx, String address) {
    return new TemplateStorageServiceVertxEBProxy(vertx, address);
  }

  void getTemplateById(String tenantId, String templateId, Handler<AsyncResult<JsonObject>> resultHandler);
}
