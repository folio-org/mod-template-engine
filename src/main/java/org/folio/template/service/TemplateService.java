package org.folio.template.service;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.model.TemplateProcessingResult;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Optional;

/**
 * Template service
 */
public interface TemplateService {

  /**
   * Searches for templates
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
   * Saves template with generated id
   *
   * @param template template to save
   * @return future with generated id
   */
  Future<String> addTemplate(Template template);

  /**
   * Updates template with given id
   *
   * @param template template to update
   * @return future with true is succeeded
   */
  Future<Boolean> updateTemplate(Template template);

  /**
   * Deletes template by id
   *
   * @param id template id
   * @return future with true is succeeded
   */
  Future<Boolean> deleteTemplate(String id);

  /**
   * Gets template specified by id and process it with given context
   *
   * @param templateRequest template processing request
   * @return template processing response
   */
  Future<TemplateProcessingResult> processTemplate(
    TemplateProcessingRequest templateRequest) throws UnsupportedEncodingException;
}
