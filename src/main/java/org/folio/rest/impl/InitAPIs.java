package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.resource.interfaces.InitAPI;

import java.net.URL;
import java.util.MissingResourceException;

public class InitAPIs implements InitAPI {

  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    URL u = InitAPIs.class.getClassLoader().getResource(TemplateResourceImpl.TEMPLATE_SCHEMA_PATH);
    if (u == null) {
      resultHandler.handle(Future.failedFuture(new MissingResourceException(TemplateResourceImpl.TEMPLATE_SCHEMA_PATH,
        InitAPIs.class.getName(), TemplateResourceImpl.TEMPLATE_SCHEMA_PATH)));
    } else {
      resultHandler.handle(Future.succeededFuture(true));
    }
  }

}
