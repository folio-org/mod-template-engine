package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplatesCollection;
import org.folio.rest.jaxrs.resource.Templates;
import org.folio.template.service.TemplateService;
import org.folio.template.service.TemplateServiceImpl;
import org.folio.template.util.TemplateEngineHelper;

import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

public class TemplateResourceImpl implements Templates {


  @Override
  public void postTemplates(Template entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        TemplateService templateService = new TemplateServiceImpl(vertxContext.owner(), okapiHeaders);
        templateService.addTemplate(entity)
          .map((Response) PostTemplatesResponse.respond201WithApplicationJson(entity))
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          TemplateEngineHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getTemplates(int offset, int limit,
                           String query, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        TemplateService templateService = new TemplateServiceImpl(vertxContext.owner(), okapiHeaders);
        templateService.getTemplates(query, offset, limit)
          .map(templates -> new TemplatesCollection()
            .withTemplates(templates)
            .withTotalRecords(templates.size())
          ).map(GetTemplatesResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          TemplateEngineHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getTemplatesByTemplateId(@NotNull String templateId, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        TemplateService templateService = new TemplateServiceImpl(vertxContext.owner(), okapiHeaders);
        templateService.getTemplateById(templateId)
          .map(optionalTemplate -> optionalTemplate.orElseThrow(() ->
            new NotFoundException(String.format("Template with id '%s' not found", templateId))))
          .map(GetTemplatesByTemplateIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          TemplateEngineHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void putTemplatesByTemplateId(@NotNull String templateId, Template
    entity, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        TemplateService templateService = new TemplateServiceImpl(vertxContext.owner(), okapiHeaders);
        entity.setId(templateId);
        templateService.updateTemplate(entity)
          .map(updated -> updated ?
            PutTemplatesByTemplateIdResponse.respond200WithApplicationJson(entity) :
            buildTemplateNotFound(templateId)
          )
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          TemplateEngineHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void deleteTemplatesByTemplateId(@NotNull String templateId,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {

    TemplateService templateService = new TemplateServiceImpl(vertxContext.owner(), okapiHeaders);
    templateService.deleteTemplate(templateId).map(deleted -> deleted ?
      DeleteTemplatesByTemplateIdResponse.respond204WithTextPlain(
        String.format("Template with id: %s deleted", templateId)) :
      buildTemplateNotFound(templateId))
      .otherwise(TemplateEngineHelper::mapExceptionToResponse)
      .onComplete(asyncResultHandler);
  }

  private Response buildTemplateNotFound(String templateId) {
    return Response
      .status(HttpStatus.SC_NOT_FOUND)
      .type(MediaType.TEXT_PLAIN)
      .entity(String.format("Template with id '%s' not found", templateId))
      .build();
  }
}
