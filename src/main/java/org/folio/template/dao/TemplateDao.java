package org.folio.template.dao;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Template;

import java.util.List;
import java.util.Optional;

/**
 * Data access object for Template
 */
public interface TemplateDao {

  /**
   * Searches for templates in database
   *
   * @param query  CQL query
   * @param offset offset
   * @param limit  limit
   * @return future with list of templates
   */
  Future<List<Template>> getTemplates(String query, int offset, int limit);

  /**
   * Searches for template by id
   *
   * @param id template id
   * @return future with optional template
   */
  Future<Optional<Template>> getTemplateById(String id);

  /**
   * Saves template to database
   *
   * @param template template to save
   * @return future with id of saved template
   */
  Future<String> addTemplate(Template template);

  /**
   * Updates template in database
   *
   * @param template template to update
   * @return future with true is succeeded
   */
  Future<Boolean> updateTemplate(Template template);

  /**
   * Deletes template from database
   *
   * @param id id of template to delete
   * @return future with true is succeeded
   */
  Future<Boolean> deleteTemplate(String id);
}
