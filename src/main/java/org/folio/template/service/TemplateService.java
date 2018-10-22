package org.folio.template.service;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.model.TemplateProcessingResult;

import java.util.List;
import java.util.Optional;

/**
 * Template service
 */
public interface TemplateService {

  Future<List<Template>> getTemplates(String query, int offset, int limit);

  Future<Optional<Template>> getTemplateById(String id);

  Future<String> addTemplate(Template template);

  Future<Boolean> updateTemplate(Template template);

  Future<Boolean> deleteTemplate(String id);

  Future<TemplateProcessingResult> processTemplate(TemplateProcessingRequest templateRequest);
}
