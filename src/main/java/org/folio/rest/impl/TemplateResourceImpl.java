package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.resource.TemplateResource;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.util.Map;

public class TemplateResourceImpl implements TemplateResource {

  private static final String TEMPLATE_STUB_PATH = "ramls/examples/template.sample";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";

  @Override
  public void getTemplateByTemplateId(@NotNull String templateId, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    //TODO replace stub response
    vertxContext.owner().fileSystem().readFile(TEMPLATE_STUB_PATH, event -> {
      if (event.succeeded()) {
        asyncResultHandler.handle(
          Future.succeededFuture(
            Response.ok(event.result().toString()).header(CONTENT_TYPE_HEADER, APPLICATION_JSON).build()
          ));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(
          GetTemplateByTemplateIdResponse.withPlainNotFound("Template not found")
        ));
      }
    });
  }

  @Override
  public void deleteTemplateByTemplateId(@NotNull String templateId, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    //TODO replace stub response
    asyncResultHandler.handle(Future.succeededFuture(Response.noContent().build()));
  }
}
