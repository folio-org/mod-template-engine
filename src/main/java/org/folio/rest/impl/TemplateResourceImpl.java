package org.folio.rest.impl;

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplatesCollection;
import org.folio.rest.jaxrs.resource.Templates;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.service.template.storage.TemplateStorageService;
import org.folio.service.template.util.TemplateEngineHelper;
import org.folio.util.handler.AbstractRequestHandler;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TemplateResourceImpl implements Templates {

  public static final String TEMPLATE_SCHEMA_PATH = "ramls/template.json";
  private static final String TEMPLATES_TABLE = "template";
  private static final String TEMPLATES_ID_FIELD = "'id'";
  private static final Logger LOG = LoggerFactory.getLogger("mod-template-engine");

  private String tenantId;
  private TemplateStorageService templateStorageProxy;
  private Map<String, String> templateResolversMap;

  public TemplateResourceImpl(Vertx vertx, String tenantId) {
    this.tenantId = TenantTool.calculateTenantId(tenantId);
    this.templateStorageProxy =
      TemplateStorageService.createProxy(vertx, TemplateEngineHelper.TEMPLATE_STORAGE_SERVICE_ADDRESS);
    this.templateResolversMap = vertx.sharedData().getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP);
  }

  @Override
  public void postTemplates(Template entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new PostTemplatesHandler(tenantId, entity, asyncResultHandler, vertxContext).run();
  }

  @Override
  public void getTemplates(int offset, int limit,
                           String query, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {
    new GetTemplatesHandler(offset, limit, query, asyncResultHandler, vertxContext).run();
  }

  @Override
  public void getTemplatesByTemplateId(@NotNull String templateId, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new GetTemplatesByTemplateIdHandler(tenantId, templateId, asyncResultHandler, templateStorageProxy).run();
  }

  @Override
  public void putTemplatesByTemplateId(@NotNull String templateId, Template entity, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    new PutTemplatesByTemplateIdHandler(tenantId, templateId, entity, asyncResultHandler, vertxContext).run();
  }

  @Override
  public void deleteTemplatesByTemplateId(@NotNull String templateId,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {
    new DeleteTemplatesByTemplateIdHandler(tenantId, templateId, asyncResultHandler, vertxContext).run();
  }


  private class GetTemplatesHandler extends AbstractRequestHandler {

    private final int offset;
    private final int limit;
    private final String query;
    private final Handler<AsyncResult<Response>> asyncResultHandler;
    private final Context vertxContext;

    public GetTemplatesHandler(int offset, int limit, String query,
                               Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
      this.offset = offset;
      this.limit = limit;
      this.query = query;
      this.asyncResultHandler = asyncResultHandler;
      this.vertxContext = vertxContext;
    }

    @Override
    protected void handle() {
      vertxContext.runOnContext(v -> catchException(this::requestDatabase));
    }

    private void requestDatabase() throws FieldException {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
        TEMPLATES_TABLE, Template.class, fieldList, cql, true, false,
        wrapWithFailureHandler(this::handleGetTemplatesResponse));
    }

    private void handleGetTemplatesResponse(Results<Template> result) {
      TemplatesCollection templatesCollection = new TemplatesCollection();
      List<Template> templateJsonList = result.getResults();
      templatesCollection.setTemplates(templateJsonList);
      templatesCollection.setTotalRecords(result.getResultInfo().getTotalRecords());
      asyncResultHandler.handle(Future.succeededFuture(GetTemplatesResponse.respond200WithApplicationJson(templatesCollection)));
    }

    @Override
    protected void handleException(Throwable e) {
      if (e instanceof GenericDatabaseException) {
        ValidationHelper.handleError(e, asyncResultHandler);
        return;
      }
      LOG.error(e.getMessage(), e);
      if (e.getCause() != null && e.getCause().getClass().getSimpleName().contains("CQLParseException")) {
        asyncResultHandler.handle(Future.succeededFuture(GetTemplatesResponse.respond400WithTextPlain("CQL Parsing Error for '" + query + "': " +
          e.getLocalizedMessage())));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(
          GetTemplatesResponse.respond500WithTextPlain(
            Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
      }
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

  private class GetTemplatesByTemplateIdHandler extends AbstractRequestHandler {

    private String tenantId;
    private String templateId;
    private Handler<AsyncResult<Response>> asyncResultHandler;

    private TemplateStorageService templateStorageProxy;

    public GetTemplatesByTemplateIdHandler(String tenantId,
                                           String templateId,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           TemplateStorageService templateStorageProxy) {
      this.tenantId = tenantId;
      this.templateId = templateId;
      this.asyncResultHandler = asyncResultHandler;
      this.templateStorageProxy = templateStorageProxy;
    }

    @Override
    protected void handle() {
      templateStorageProxy.getTemplateById(tenantId, templateId,
        wrapWithFailureHandler(this::handleGetTemplateByIdResponse));
    }

    private void handleGetTemplateByIdResponse(JsonObject result) {
      if (result == null) {
        asyncResultHandler.handle(Future.succeededFuture(
          GetTemplatesByTemplateIdResponse.respond404WithTextPlain("No templates for id " + templateId + " found")));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(
        GetTemplatesByTemplateIdResponse.respond200WithApplicationJson(result.mapTo(Template.class))));
    }

    @Override
    protected void handleException(Throwable e) {
      if (e instanceof GenericDatabaseException) {
        ValidationHelper.handleError(e, asyncResultHandler);
        return;
      }
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetTemplatesByTemplateIdResponse.respond500WithTextPlain(
          Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }

  }

  private class PostTemplatesHandler extends AbstractRequestHandler {

    private final String tenantId;
    private final Template entity;
    private final Handler<AsyncResult<Response>> asyncResultHandler;
    private final Context vertxContext;

    private String generatedId;

    public PostTemplatesHandler(String tenantId,
                                Template entity,
                                Handler<AsyncResult<Response>> asyncResultHandler,
                                Context vertxContext) {
      this.tenantId = tenantId;
      this.entity = entity;
      this.asyncResultHandler = asyncResultHandler;
      this.vertxContext = vertxContext;
    }

    @Override
    protected void handle() {
      vertxContext.runOnContext(v -> catchException(this::requestDatabase));
    }

    private void requestDatabase() {
      boolean templateIsValid = validateTemplate(entity, asyncResultHandler);
      if (!templateIsValid) {
        return;
      }
      generatedId = UUID.randomUUID().toString();
      entity.setId(generatedId);
      PostgresClient.getInstance(vertxContext.owner(), tenantId).save(TEMPLATES_TABLE, generatedId, entity,
        wrapWithFailureHandler(reply -> handleSaveTemplateResponse()));
    }

    private void handleSaveTemplateResponse() {
      LOG.info("Template created with id: " + generatedId);
      asyncResultHandler.handle(Future.succeededFuture(PostTemplatesResponse.respond201WithApplicationJson(entity)));
    }

    @Override
    protected void handleException(Throwable e) {
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetTemplatesByTemplateIdResponse.respond500WithTextPlain(
          Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }

  private class PutTemplatesByTemplateIdHandler extends AbstractRequestHandler {

    private final String tenantId;
    private final String templateId;
    private final Template entity;
    private final Handler<AsyncResult<Response>> asyncResultHandler;
    private final Context vertxContext;

    private Criteria idCrit;

    public PutTemplatesByTemplateIdHandler(String tenantId,
                                           String templateId,
                                           Template entity,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
      this.tenantId = tenantId;
      this.templateId = templateId;
      this.entity = entity;
      this.asyncResultHandler = asyncResultHandler;
      this.vertxContext = vertxContext;
    }

    @Override
    protected void handle() {
      vertxContext.runOnContext(v -> catchException(this::requestDatabase));
    }

    private void requestDatabase() {
      boolean templateIsValid = validateTemplate(entity, asyncResultHandler);
      if (!templateIsValid) {
        return;
      }
      idCrit = new Criteria();
      idCrit.addField(TEMPLATES_ID_FIELD);
      idCrit.setOperation("=");
      idCrit.setValue(templateId);
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(TEMPLATES_TABLE, Template.class, new Criterion(idCrit), true,
          wrapWithFailureHandler(this::handleGetTemplateResponse));
    }

    private void handleGetTemplateResponse(Results<Template> result) {
      List<Template> templateList = result.getResults();
      if (templateList.isEmpty()) {
        asyncResultHandler.handle(Future.succeededFuture(
          PutTemplatesByTemplateIdResponse.respond404WithTextPlain("No templates was found")));
        return;
      }
      if (entity.getId() == null || entity.getId().isEmpty()) {
        entity.setId(templateId);
      }
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .update(TEMPLATES_TABLE, entity, new Criterion(idCrit), true,
          wrapWithFailureHandler(putReply -> handleUpdateTemplate()));
    }

    private void handleUpdateTemplate() {
      asyncResultHandler.handle(Future.succeededFuture(
        PutTemplatesByTemplateIdResponse.respond200WithApplicationJson(entity)));
    }

    @Override
    protected void handleException(Throwable e) {
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetTemplatesByTemplateIdResponse.respond500WithTextPlain(
          Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }

  private boolean validateTemplate(Template template, Handler<AsyncResult<Response>> asyncResultHandler) {
    boolean templateResolverIsSupported = templateResolversMap.containsKey(template.getTemplateResolver());
    if (!templateResolverIsSupported) {
      String message = String.format("Template resolver '%s' is not supported", template.getTemplateResolver());
      asyncResultHandler.handle(Future.succeededFuture(
        PostTemplatesResponse.respond400WithTextPlain(message)));
      return false;
    }
    return true;
  }

  private class DeleteTemplatesByTemplateIdHandler extends AbstractRequestHandler {

    private final String tenantId;
    private final String templateId;
    private final Handler<AsyncResult<Response>> asyncResultHandler;
    private final Context vertxContext;

    public DeleteTemplatesByTemplateIdHandler(String tenantId,
                                              String templateId,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {
      this.tenantId = tenantId;
      this.templateId = templateId;
      this.asyncResultHandler = asyncResultHandler;
      this.vertxContext = vertxContext;
    }

    @Override
    protected void handle() {
      vertxContext.runOnContext(v -> catchException(this::requestDatabase));
    }

    private void requestDatabase() {
      PostgresClient
        .getInstance(vertxContext.owner(), tenantId)
        .delete(TEMPLATES_TABLE, templateId,
          wrapWithFailureHandler(this::handleDeleteTemplateResponse));
    }

    private void handleDeleteTemplateResponse(UpdateResult result) {
      if (result.getUpdated() == 1) {
        LOG.info("Template with id: " + templateId + " deleted");
        asyncResultHandler.handle(Future.succeededFuture(
          DeleteTemplatesByTemplateIdResponse.respond204WithTextPlain("Template with id: " + templateId + " deleted")));
      } else {
        LOG.error("Delete count error");
        asyncResultHandler.handle(Future.succeededFuture(DeleteTemplatesByTemplateIdResponse
          .respond404WithTextPlain("Delete count error  not found id")));
      }
    }

    @Override
    protected void handleException(Throwable e) {
      if (e instanceof GenericDatabaseException) {
        ValidationHelper.handleError(e, asyncResultHandler);
        return;
      }
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(
        GetTemplatesByTemplateIdResponse.respond500WithTextPlain(
          Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
    }
  }
}
