package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.resource.TemplateRequest;
import org.folio.template.service.TemplateService;
import org.folio.template.service.TemplateServiceImpl;
import org.folio.template.util.TemplateEngineHelper;

import javax.ws.rs.core.Response;
import java.util.Map;

public class TemplateRequestImpl implements TemplateRequest {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  @Override
  public void postTemplateRequest(TemplateProcessingRequest entity,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    LOG.debug("postTemplateRequest:: Trying to post Template Request with Template ID : {}", entity.getTemplateId());
    vertxContext.runOnContext(v -> {
      try {
        TemplateService templateService = new TemplateServiceImpl(vertxContext.owner(), okapiHeaders);
        templateService.processTemplate(entity)
          .map(PostTemplateRequestResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        LOG.warn("Error in posting Template Request: {}", e.getMessage());
        asyncResultHandler.handle(Future.succeededFuture(
          TemplateEngineHelper.mapExceptionToResponse(e)));
      }
    });
  }
}
