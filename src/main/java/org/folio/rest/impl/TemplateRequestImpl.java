package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.resource.TemplateRequest;
import org.folio.template.service.TemplateService;
import org.folio.template.service.TemplateServiceImpl;
import org.folio.template.util.OkapiConnectionParams;
import org.folio.template.util.TemplateEngineHelper;

import javax.ws.rs.core.Response;
import java.util.Map;

public class TemplateRequestImpl implements TemplateRequest {

  @Override
  public void postTemplateRequest(TemplateProcessingRequest entity,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        TemplateService templateService = new TemplateServiceImpl(vertxContext.owner(), new OkapiConnectionParams(okapiHeaders));
        templateService.processTemplate(entity, new OkapiConnectionParams(okapiHeaders))
          .map(PostTemplateRequestResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .setHandler(asyncResultHandler);
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          TemplateEngineHelper.mapExceptionToResponse(e)));
      }
    });
  }
}
