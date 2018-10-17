package org.folio.service.template.storage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;
import org.folio.util.handler.AbstractRequestHandler;

import java.util.List;

public class TemplateStorageServiceImpl implements TemplateStorageService {

  private static final String TEMPLATE_SCHEMA_PATH = "ramls/template.json";
  private static final String TEMPLATES_TABLE = "template";
  private static final String TEMPLATES_ID_FIELD = "'id'";

  private Vertx vertx;

  public TemplateStorageServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void getTemplateById(String tenantId, String templateId, Handler<AsyncResult<JsonObject>> resultHandler) {
    new GetTemplateByIdHandler(vertx, tenantId, templateId, resultHandler).run();
  }

  private static class GetTemplateByIdHandler extends AbstractRequestHandler {

    private final Logger logger = LoggerFactory.getLogger(GetTemplateByIdHandler.class);

    private Vertx vertx;
    private String tenantId;
    private String templateId;
    private Handler<AsyncResult<JsonObject>> resultHandler;

    public GetTemplateByIdHandler(Vertx vertx, String tenantId, String templateId,
                                  Handler<AsyncResult<JsonObject>> resultHandler) {
      this.vertx = vertx;
      this.tenantId = tenantId;
      this.templateId = templateId;
      this.resultHandler = resultHandler;
    }

    @Override
    protected void handle() {
      vertx.getOrCreateContext().runOnContext(v -> catchException(this::requestDatabase));
    }

    void requestDatabase() {
      try {
        Criteria idCrit = new Criteria(TEMPLATE_SCHEMA_PATH);
        idCrit.addField(TEMPLATES_ID_FIELD);
        idCrit.setOperation("=");
        idCrit.setValue(templateId);
        PostgresClient.getInstance(vertx, tenantId).get(TEMPLATES_TABLE, Template.class, new Criterion(idCrit), true,
          wrapWithFailureHandler(this::handlePostgresResponse));
      } catch (Exception e) {
        handleException(e);
      }
    }

    @Override
    protected void handleException(Throwable e) {
      logger.error(e.getMessage(), e);
      resultHandler.handle(Future.failedFuture(e));
    }

    private void handlePostgresResponse(Results<Template> results) {
      List<Template> templates = results.getResults();
      if (!templates.isEmpty()) {
        resultHandler.handle(Future.succeededFuture(JsonObject.mapFrom(templates.get(0))));
      } else {
        resultHandler.handle(Future.succeededFuture(null));
      }
    }
  }
}
