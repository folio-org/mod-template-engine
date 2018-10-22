package org.folio.template.dao;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.UpdateResult;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

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
    Future<Results<Template>> future = Future.future();
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      pgClient.get(TEMPLATES_TABLE, Template.class, fieldList, cql, true, false, future.completer());
    } catch (Exception e) {
      future.fail(e);
    }
    return future.map(Results::getResults);
  }

  @Override
  public Future<Optional<Template>> getTemplateById(String id) {
    Future<Results<Template>> future = Future.future();
    try {
      Criteria idCrit = new Criteria(TEMPLATE_SCHEMA_PATH);
      idCrit.addField(TEMPLATES_ID_FIELD);
      idCrit.setOperation("=");
      idCrit.setValue(id);
      pgClient.get(TEMPLATES_TABLE, Template.class, new Criterion(idCrit), true, future.completer());
    } catch (Exception e) {
      future.fail(e);
    }
    return future
      .map(Results::getResults)
      .map(templates -> templates.isEmpty() ? Optional.empty() : Optional.of(templates.get(0)));
  }

  @Override
  public Future<String> addTemplate(Template template) {
    Future<String> future = Future.future();
    pgClient.save(TEMPLATES_TABLE, template.getId(), template, future.completer());
    return future;
  }

  @Override
  public Future<Boolean> updateTemplate(Template template) {
    Future<UpdateResult> future = Future.future();
    try {
      Criteria idCrit = new Criteria();
      idCrit.addField(TEMPLATES_ID_FIELD);
      idCrit.setOperation("=");
      idCrit.setValue(template.getId());
      pgClient.update(TEMPLATES_TABLE, template, new Criterion(idCrit), true, future.completer());
    } catch (Exception e) {
      future.fail(e);
    }
    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  @Override
  public Future<Boolean> deleteTemplate(String id) {
    Future<UpdateResult> future = Future.future();
    pgClient.delete(TEMPLATES_TABLE, id, future.completer());
    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  /**
   * Build CQL from request URL query
   *
   * @param query - query from URL
   * @param limit - limit of records for pagination
   * @return - CQL wrapper for building postgres request to database
   * @throws org.z3950.zing.cql.cql2pgjson.FieldException field exception
   */
  private CQLWrapper getCQL(String query, int limit, int offset)
    throws org.z3950.zing.cql.cql2pgjson.FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(TEMPLATES_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
