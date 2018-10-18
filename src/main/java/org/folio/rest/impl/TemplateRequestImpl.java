package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.model.TemplateProcessingResult;
import org.folio.rest.jaxrs.resource.TemplateRequest;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.service.template.engine.TemplateEngineService;
import org.folio.service.template.storage.TemplateStorageService;
import org.folio.service.template.util.TemplateEngineHelper;
import org.folio.util.handler.AbstractRequestHandler;

import javax.ws.rs.core.Response;
import java.util.Map;

public class TemplateRequestImpl implements TemplateRequest {

  private String tenantId;
  private TemplateStorageService templateStorageProxy;
  private TemplateEngineService templateEngineProxy;

  public TemplateRequestImpl(Vertx vertx, String tenantId) {
    this.tenantId = TenantTool.calculateTenantId(tenantId);
    this.templateStorageProxy =
      TemplateStorageService.createProxy(vertx, TemplateEngineHelper.TEMPLATE_STORAGE_SERVICE_ADDRESS);
    this.templateEngineProxy =
      TemplateEngineService.createProxy(vertx, TemplateEngineHelper.TEMPLATE_ENGINE_SERVICE_ADDRESS);
  }

  @Override
  public void postTemplateRequest(TemplateProcessingRequest entity,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    new PostTemplateRequestHandler(tenantId, entity, asyncResultHandler,
      templateStorageProxy, templateEngineProxy).run();
  }

  private static class PostTemplateRequestHandler extends AbstractRequestHandler {

    private final Logger logger = LoggerFactory.getLogger(PostTemplateRequestHandler.class);

    private final String tenantId;
    private final TemplateProcessingRequest entity;
    private final Handler<AsyncResult<Response>> asyncResultHandler;
    private final TemplateStorageService templateStorageProxy;
    private final TemplateEngineService templateEngineProxy;

    public PostTemplateRequestHandler(String tenantId,
                                      TemplateProcessingRequest entity,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      TemplateStorageService templateStorageProxy,
                                      TemplateEngineService templateEngineProxy) {
      this.tenantId = tenantId;
      this.entity = entity;
      this.asyncResultHandler = asyncResultHandler;
      this.templateStorageProxy = templateStorageProxy;
      this.templateEngineProxy = templateEngineProxy;
    }

    @Override
    protected void handle() {
      templateStorageProxy.getTemplateById(tenantId, entity.getTemplateId(),
        wrapWithFailureHandler(this::handleGetTemplateById));
    }

    private void handleGetTemplateById(JsonObject result) {
      if (result == null) {
        String message = "No templates for id " + entity.getTemplateId() + " found";
        asyncResultHandler.handle(Future.succeededFuture(PostTemplateRequestResponse.respond400WithTextPlain(message)));
        return;
      }
      Template template = result.mapTo(Template.class);
      boolean templateRequestIsValid =
        validateTemplateProcessingRequest(entity, template);
      if (!templateRequestIsValid) {
        return;
      }
      templateEngineProxy.processTemplate(JsonObject.mapFrom(template), JsonObject.mapFrom(entity),
        wrapWithFailureHandler(this::handleProcessTemplateResponse));
    }

    private void handleProcessTemplateResponse(JsonObject result) {
      asyncResultHandler.handle(Future.succeededFuture(
        PostTemplateRequestResponse.respond200WithApplicationJson(result.mapTo(TemplateProcessingResult.class))));
    }

    /**
     * Checks if request for template processing is valid
     * and returns response with error to <code>asyncResultHandler</code> if not valid.
     *
     * @param templateRequest template processing request entity
     * @param template        template
     * @return true if template processing request is valid, false otherwise
     */
    private boolean validateTemplateProcessingRequest(TemplateProcessingRequest templateRequest, Template template) {
      if (!template.getOutputFormats().contains(templateRequest.getOutputFormat())) {
        String message = String.format("Requested template does not support '%s' output format",
          templateRequest.getOutputFormat());
        asyncResultHandler.handle(Future.succeededFuture(PostTemplateRequestResponse.respond400WithTextPlain(message)));
        return false;
      }

      boolean templateSupportsGivenLanguage =
        template.getLocalizedTemplates().getAdditionalProperties()
          .containsKey(templateRequest.getLang());
      if (!templateSupportsGivenLanguage) {
        String message = String.format("Requested template does not have localized template for language '%s'",
          templateRequest.getLang());
        asyncResultHandler.handle(Future.succeededFuture(PostTemplateRequestResponse.respond400WithTextPlain(message)));
        return false;
      }
      return true;
    }

    @Override
    protected void handleException(Throwable e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(
        PostTemplateRequestResponse.respond500WithTextPlain(
          Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }
}
