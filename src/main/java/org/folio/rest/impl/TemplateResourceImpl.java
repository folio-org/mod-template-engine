package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplatesCollection;
import org.folio.rest.jaxrs.resource.Templates;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.template.service.TemplateService;
import org.folio.template.service.TemplateServiceImpl;
import org.folio.template.util.TemplateEngineHelper;

import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

public class TemplateResourceImpl implements Templates {

  private TemplateService templateService;

  public TemplateResourceImpl(Vertx vertx, String tenantId) {
    String calculatedTenantId = TenantTool.calculateTenantId(tenantId);
    this.templateService = new TemplateServiceImpl(vertx, calculatedTenantId);
  }

  @Override
  public void postTemplates(Template entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        templateService.addTemplate(entity)
          .map((Response) PostTemplatesResponse.respond201WithApplicationJson(entity))
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .setHandler(asyncResultHandler);
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
        templateService.getTemplates(query, offset, limit)
          .map(templates -> new TemplatesCollection()
            .withTemplates(templates)
            .withTotalRecords(templates.size())
          ).map(GetTemplatesResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .setHandler(asyncResultHandler);
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
        templateService.getTemplateById(templateId)
          .map(optionalTemplate -> optionalTemplate.orElseThrow(() ->
            new NotFoundException(String.format("Template with id '%s' not found", templateId))))
          .map(GetTemplatesByTemplateIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .setHandler(asyncResultHandler);
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
        entity.setId(templateId);
        templateService.updateTemplate(entity)
          .map(updated -> updated ?
            PutTemplatesByTemplateIdResponse.respond200WithApplicationJson(entity) :
            buildTemplateNotFound(templateId)
          )
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .setHandler(asyncResultHandler);
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
    vertxContext.runOnContext(v -> {
      try {
        templateService.deleteTemplate(templateId).map(deleted -> deleted ?
          DeleteTemplatesByTemplateIdResponse.respond204WithTextPlain(
            String.format("Template with id: %s deleted", templateId)) :
          buildTemplateNotFound(templateId)
        )
          .otherwise(TemplateEngineHelper::mapExceptionToResponse)
          .setHandler(asyncResultHandler);
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          TemplateEngineHelper.mapExceptionToResponse(e)));
      }
    });
  }

  private Response buildTemplateNotFound(String templateId) {
    return Response
      .status(HttpStatus.SC_NOT_FOUND)
      .type(MediaType.TEXT_PLAIN)
      .entity(String.format("Template with id '%s' not found", templateId))
      .build();
  }
}
