package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.TemplateJson;
import org.folio.rest.jaxrs.model.TemplatesCollectionJson;
import org.folio.rest.jaxrs.resource.TemplateResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;

public class TemplateResourceImpl implements TemplateResource {

  private static final String TEMPLATES_TABLE = "template";
  private static final String TEMPLATES_ID_FIELD = "'id'";
  public static final String TEMPLATE_SCHEMA_PATH = "ramls/template.json";
  private static final String INTERNAL_ERROR = "Internal Server error";
  private static final String POSTGRES_ERROR = "Error from PostgresClient: ";
  private static final String INTERNAL_CONTEXT_ERROR = "Error running on vertx context: ";
  private final Logger logger = LoggerFactory.getLogger("mod-template-engine");

  @Override
  public void postTemplate(TemplateJson entity, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(h -> {
        String tenantId = getTenant(okapiHeaders);
        String id = UUID.randomUUID().toString();
        entity.setId(id);
        try {
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
          logger.error(POSTGRES_ERROR + e.getLocalizedMessage());
          asyncResultHandler.handle(Future.succeededFuture(PostTemplateResponse.withPlainInternalServerError(INTERNAL_ERROR)));
        }
      });
    } catch (Exception e) {
      String message = "Template creation internal error";
      logger.error(message, e);
      asyncResultHandler.handle(Future.succeededFuture(PostTemplateResponse.withPlainInternalServerError(message)));
    }
  }

  @Override
  public void getTemplate(int length, int start,
                          String query, Map<String, String> okapiHeaders,
                          Handler<AsyncResult<Response>> asyncResultHandler,
                          Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        String tenantId = getTenant(okapiHeaders);
        String[] fieldList = {"*"};
        try {
          CQLWrapper cql = getCQL(query, length, start - 1);
          PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
            TEMPLATES_TABLE, TemplateJson.class, fieldList, cql, true, false, getReply -> {
              if (getReply.failed()) {
                logger.error("Error in PostgresClient get operation " + getReply.cause().getLocalizedMessage());
                asyncResultHandler.handle(Future.succeededFuture(GetTemplateResponse.withPlainInternalServerError(INTERNAL_ERROR)));
              } else {
                TemplatesCollectionJson templatesCollection = new TemplatesCollectionJson();
                List<TemplateJson> templateJsonList = (List<TemplateJson>) getReply.result().getResults();
                templatesCollection.setTemplates(templateJsonList);
                templatesCollection.setTotalRecords(getReply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(Future.succeededFuture(GetTemplateResponse.withJsonOK(templatesCollection)));
              }
            });
        } catch (Exception e) {
          logger.error("Error invoking Postgresclient: " + e.getLocalizedMessage());
          asyncResultHandler.handle(Future.succeededFuture(GetTemplateResponse.withPlainInternalServerError(INTERNAL_ERROR)));
        }
      });
    } catch (Exception e) {
      logger.error(INTERNAL_CONTEXT_ERROR + e.getLocalizedMessage());
      if (e.getCause() != null && e.getCause().getClass().getSimpleName().contains("CQLParseException")) {
        asyncResultHandler.handle(Future.succeededFuture(GetTemplateResponse.withPlainBadRequest("CQL Parsing Error for '" + query + "': " +
          e.getLocalizedMessage())));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(GetTemplateResponse.withPlainInternalServerError(INTERNAL_ERROR)));
      }
    }
  }

  @Override
  public void getTemplateByTemplateId(@NotNull String templateId, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      String tenantId = getTenant(okapiHeaders);
      vertxContext.runOnContext(v -> {
        try {
          Criteria idCrit = new Criteria(TEMPLATE_SCHEMA_PATH);
          idCrit.addField(TEMPLATES_ID_FIELD);
          idCrit.setOperation("=");
          idCrit.setValue(templateId);
          PostgresClient.getInstance(vertxContext.owner(), tenantId).get(TEMPLATES_TABLE, TemplateJson.class, new Criterion(idCrit), true, getReply -> {
            if (getReply.failed()) {
              logger.error("Error in PostgresClient get operation: " + getReply.cause().getLocalizedMessage());
              asyncResultHandler.handle(Future.succeededFuture(GetTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
            } else {
              List<TemplateJson> templates = (List<TemplateJson>) getReply.result().getResults();
              if (templates.isEmpty()) {
                asyncResultHandler.handle(Future.succeededFuture(GetTemplateByTemplateIdResponse.withPlainNotFound("No templates for id " + templateId + " found")));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(GetTemplateByTemplateIdResponse.withJsonOK(templates.get(0))));
              }
            }
          });
        } catch (Exception e) {
          logger.error(POSTGRES_ERROR + e.getLocalizedMessage());
          asyncResultHandler.handle(Future.succeededFuture(GetTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
        }
      });
    } catch (Exception e) {
      logger.error(INTERNAL_CONTEXT_ERROR + e.getLocalizedMessage());
      asyncResultHandler.handle(Future.succeededFuture(GetTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
    }
  }

  @Override
  public void putTemplateByTemplateId(@NotNull String templateId, TemplateJson entity, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> {
        String tenantId = getTenant(okapiHeaders);
        Criteria idCrit = new Criteria();
        idCrit.addField(TEMPLATES_ID_FIELD);
        idCrit.setOperation("=");
        idCrit.setValue(templateId);
        try {
          PostgresClient.getInstance(vertxContext.owner(), tenantId).get(TEMPLATES_TABLE, TemplateJson.class, new Criterion(idCrit), true, getReply -> {
            if (getReply.failed()) {
              logger.error("PostgresClient get operation failed: " + getReply.cause().getLocalizedMessage());
              asyncResultHandler.handle(Future.succeededFuture(PutTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
            } else {
              List<TemplateJson> templateList = (List<TemplateJson>) getReply.result().getResults();
              if (templateList.isEmpty()) {
                asyncResultHandler.handle(Future.succeededFuture(PutTemplateByTemplateIdResponse.withPlainNotFound("No templates was found")));
              } else {
                try {
                  if (entity.getId() == null || entity.getId().isEmpty()) {
                    entity.setId(templateId);
                  }
                  PostgresClient.getInstance(vertxContext.owner(), tenantId).update(TEMPLATES_TABLE, entity, new Criterion(idCrit), true, putReply -> {
                    if (putReply.failed()) {
                      logger.error("Error with PostgresClient update operation: " + putReply.cause().getLocalizedMessage());
                      asyncResultHandler.handle(Future.succeededFuture(PutTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
                    } else {
                      asyncResultHandler.handle(Future.succeededFuture(PutTemplateByTemplateIdResponse.withJsonOK(entity)));
                    }
                  });
                } catch (Exception e) {
                  logger.error("Error with PostgresClient: " + e.getLocalizedMessage());
                  asyncResultHandler.handle(Future.succeededFuture(PutTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
                }
              }
            }
          });
        } catch (Exception e) {
          logger.error("Error with PostgresClient: " + e.getLocalizedMessage());
          asyncResultHandler.handle(Future.succeededFuture(PutTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
        }
      });
    } catch (Exception e) {
      logger.error(INTERNAL_CONTEXT_ERROR + e.getLocalizedMessage());
      asyncResultHandler.handle(Future.succeededFuture(PutTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
    }
  }

  @Override
  public void deleteTemplateByTemplateId(@NotNull String templateId,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {
    try {
      vertxContext.runOnContext(h -> {
        String tenantId = getTenant(okapiHeaders);
        try {
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
          logger.error(POSTGRES_ERROR + e.getLocalizedMessage());
          asyncResultHandler.handle(Future.succeededFuture(DeleteTemplateByTemplateIdResponse.withPlainInternalServerError(INTERNAL_ERROR)));
        }
      });
    } catch (Exception e) {
      String message = "Template deletion internal error";
      logger.error(message, e);
      asyncResultHandler.handle(Future.succeededFuture(DeleteTemplateByTemplateIdResponse.withPlainInternalServerError(message)));
    }
  }

  /**
   * Get okapi tenant id from request headers
   *
   * @param headers - map with headers
   * @return - okapi tenant id
   */
  private String getTenant(Map<String, String> headers) {
    return TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
  }

  /**
   * Build CQL from request URL query
   *
   * @param query - query from URL
   * @param limit - limit of records for pagination
   * @return - CQL wrapper for building postgres request to database
   * @throws org.z3950.zing.cql.cql2pgjson.FieldException
   */
  private CQLWrapper getCQL(String query, int limit, int offset)
    throws org.z3950.zing.cql.cql2pgjson.FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(TEMPLATES_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
