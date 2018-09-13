package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.TemplateJson;
import org.folio.rest.jaxrs.resource.TemplateResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;

public class TemplateResourceImpl implements TemplateResource {

  private static final String TEMPLATE_STUB_PATH = "ramls/examples/template.sample";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String TEMPLATES_TABLE = "template";
  private final Logger logger = LoggerFactory.getLogger("mod-template-engine");

  @Override
  public void postTemplate(
    TemplateJson entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(h -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        String id = UUID.randomUUID().toString();
        entity.setId(id);

        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(TEMPLATES_TABLE, id, entity,
          reply -> {
            if (reply.succeeded()) {
              logger.info("Template created with id: " + id);
              asyncResultHandler.handle(Future.succeededFuture(PostTemplateResponse.withJsonCreated(entity)));
            } else {
              logger.error("Template creation error");
              ValidationHelper.handleError(reply.cause(), asyncResultHandler);
            }
          });
      } catch (Exception e) {
        String message = "Template creation internal error";
        logger.error(message, e);
        asyncResultHandler.handle(Future.succeededFuture(
          PostTemplateResponse.withPlainInternalServerError(message)));
      }
    });
  }

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
  public void deleteTemplateByTemplateId(@NotNull String templateId,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {

    vertxContext.runOnContext(h -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        PostgresClient
          .getInstance(vertxContext.owner(), tenantId)
          .delete(TEMPLATES_TABLE, templateId,
            reply -> {
              if (reply.succeeded()) {

                if (reply.result().getUpdated() == 1) {
                  logger.info("Template with id: " + templateId + " deleted");
                  asyncResultHandler.handle(succeededFuture(
                    DeleteTemplateByTemplateIdResponse.withPlainNoContent("Template with id: " + templateId + " deleted")));
                } else {
                  logger.error("Delete count error");
                  asyncResultHandler.handle(succeededFuture(DeleteTemplateByTemplateIdResponse
                    .withPlainNotFound("Delete count error  not found id")));
                }
              } else {
                logger.error("Template deletion error");
                ValidationHelper.handleError(reply.cause(), asyncResultHandler);
              }
            });

      } catch (Exception e) {
        String message = "Template deletion internal error";
        logger.error(message, e);
        asyncResultHandler.handle(Future.succeededFuture(
          PostTemplateResponse.withPlainInternalServerError(message)));
      }
    });
  }
}
