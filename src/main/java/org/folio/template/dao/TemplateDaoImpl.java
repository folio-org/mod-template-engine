package org.folio.template.dao;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class TemplateDaoImpl implements TemplateDao {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  public static final String TEMPLATE_SCHEMA_PATH = "ramls/template.json";
  private static final String TEMPLATES_TABLE = "template";

  private PostgresClient pgClient;

  public TemplateDaoImpl(Vertx vertx, String tenantId) {
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  public TemplateDaoImpl(PostgresClient postgresClient) {
    pgClient = postgresClient;
  }

  @Override
  public Future<List<Template>> getTemplates(String query, int offset, int limit) {
    LOG.debug("getTemplates:: Retrieving templates from database");
    Promise<Results<Template>> promise = Promise.promise();
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      pgClient.get(TEMPLATES_TABLE, Template.class, fieldList, cql, true, false, promise);
    } catch (Exception e) {
      LOG.warn("Failed to retrieve templates from database with exception {}", e.getMessage());
      promise.fail(e);
    }
    LOG.info("getTemplates:: Retrieved templates from database");
    return promise.future().map(Results::getResults);
  }

  @Override
  public Future<Optional<Template>> getTemplateById(String id) {
    LOG.debug("getTemplateById:: Retrieving template from database by Template ID: {}", id);
    Promise<Template> promise = Promise.promise();
    pgClient.getById(TEMPLATES_TABLE, id, Template.class, promise);
    LOG.info("getTemplateById:: Retrieved template from database by Template ID: {}", id);
    return promise.future().map(Optional::ofNullable);
  }

  @Override
  public Future<String> addTemplate(Template template) {
    LOG.debug("addTemplate:: Adding template to database by Template ID: {}", template.getId());
    Promise<String> promise = Promise.promise();
    pgClient.save(TEMPLATES_TABLE, template.getId(), template, promise);
    LOG.info("addTemplate:: Saved template to database by Template ID: {}", template.getId());
    return promise.future();
  }

  @Override
  public Future<Boolean> updateTemplate(Template template) {
    LOG.debug("updateTemplate:: Updating template in database by Template ID: {}", template.getId());
    Promise<RowSet<Row>> promise = Promise.promise();
    pgClient.update(TEMPLATES_TABLE, template, template.getId(), promise);
    LOG.info("updateTemplate:: Updated template to database by Template ID: {}", template.getId());
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<Boolean> deleteTemplate(String id) {
    LOG.debug("deleteTemplate:: Deleting template from database by Template ID: {}", id);
    Promise<RowSet<Row>> promise = Promise.promise();
    pgClient.delete(TEMPLATES_TABLE, id, promise);
    LOG.info("deleteTemplate:: Deleted template from database by Template ID: {}", id);
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  /**
   * Build CQL from request URL query
   *
   * @param query - query from URL
   * @param limit - limit of records for pagination
   * @return - CQL wrapper for building postgres request to database
   * @throws org.folio.cql2pgjson.exception.FieldException field exception
   */
  private CQLWrapper getCQL(String query, int limit, int offset)
    throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(TEMPLATES_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
