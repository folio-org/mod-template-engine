package org.folio.template.dao;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.UpdateResult;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.cql2pgjson.CQL2PgJSON;

import java.util.List;
import java.util.Optional;

public class TemplateDaoImpl implements TemplateDao {

  public static final String TEMPLATE_SCHEMA_PATH = "ramls/template.json";
  private static final String TEMPLATES_TABLE = "template";
  private static final String TEMPLATES_ID_FIELD = "'id'";

  private PostgresClient pgClient;

  public TemplateDaoImpl(Vertx vertx, String tenantId) {
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  @Override
  public Future<List<Template>> getTemplates(String query, int offset, int limit) {
    Promise<Results<Template>> promise = Promise.promise();
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      pgClient.get(TEMPLATES_TABLE, Template.class, fieldList, cql, true, false, promise);
    } catch (Exception e) {
      promise.fail(e);
    }
    return promise.future().map(Results::getResults);
  }

  @Override
  public Future<Optional<Template>> getTemplateById(String id) {
    return getTemplates("id==" + id, 0, 1)
      .map(templates -> templates.isEmpty() ? Optional.empty() : Optional.of(templates.get(0)));
  }

  @Override
  public Future<String> addTemplate(Template template) {
    Promise<String> promise = Promise.promise();
    pgClient.save(TEMPLATES_TABLE, template.getId(), template, promise);
    return promise.future();
  }

  @Override
  public Future<Boolean> updateTemplate(Template template) {
    Promise<UpdateResult> promise = Promise.promise();
    try {
      Criteria idCrit = new Criteria();
      idCrit.addField(TEMPLATES_ID_FIELD);
      idCrit.setOperation("=");
      idCrit.setVal(template.getId());
      pgClient.update(TEMPLATES_TABLE, template, new Criterion(idCrit), true, promise);
    } catch (Exception e) {
      promise.fail(e);
    }
    return promise.future().map(updateResult -> updateResult.getUpdated() == 1);
  }

  @Override
  public Future<Boolean> deleteTemplate(String id) {
    Promise<UpdateResult> promise = Promise.promise();
    pgClient.delete(TEMPLATES_TABLE, id, promise);
    return promise.future().map(updateResult -> updateResult.getUpdated() == 1);
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
