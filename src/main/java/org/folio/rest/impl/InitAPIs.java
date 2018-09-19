package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.resource.interfaces.InitAPI;

import java.net.URL;
import java.util.MissingResourceException;

import static org.folio.rest.impl.TemplateResourceImpl.TEMPLATE_SCHEMA_PATH;

public class InitAPIs implements InitAPI {

  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    URL u = InitAPIs.class.getClassLoader().getResource(TEMPLATE_SCHEMA_PATH);
    if(u == null) {
      resultHandler.handle(Future.failedFuture(new MissingResourceException(TEMPLATE_SCHEMA_PATH, InitAPIs.class.getName(), TEMPLATE_SCHEMA_PATH)));
    } else {
      resultHandler.handle(Future.succeededFuture(true));
    }
  }

}
