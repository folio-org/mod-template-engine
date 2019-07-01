package org.folio.template.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.jaxrs.model.Meta;
import org.folio.rest.jaxrs.model.Result;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.model.TemplateProcessingResult;
import org.folio.template.client.ConfigurationClient;
import org.folio.template.client.ConfigurationClientImpl;
import org.folio.template.client.LocaleConfiguration;
import org.folio.template.dao.TemplateDao;
import org.folio.template.dao.TemplateDaoImpl;
import org.folio.template.resolver.TemplateResolver;
import org.folio.template.util.OkapiConnectionParams;
import org.folio.template.util.TemplateEngineHelper;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.folio.template.util.ContextDateTimeFormatter.formatDatesInJson;


public class TemplateServiceImpl implements TemplateService {

  private Vertx vertx;
  private TemplateDao templateDao;
  private Map<String, String> templateResolverAddressesMap;
  private ConfigurationClient configurationClient;


  public TemplateServiceImpl(Vertx vertx, OkapiConnectionParams params) {
    this.vertx = vertx;
    this.templateDao = new TemplateDaoImpl(vertx, params.getTenant());
    this.templateResolverAddressesMap = vertx.sharedData().getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP);
    this.configurationClient = new ConfigurationClientImpl(vertx);
  }

  public Future<List<Template>> getTemplates(String query, int offset, int limit) {
    return templateDao.getTemplates(query, offset, limit);
  }

  @Override
  public Future<Optional<Template>> getTemplateById(String id) {
    return templateDao.getTemplateById(id);
  }

  @Override
  public Future<String> addTemplate(Template template) {
    validateTemplate(template);
    template.setId(UUID.randomUUID().toString());
    return templateDao.addTemplate(template);
  }

  @Override
  public Future<Boolean> updateTemplate(Template template) {
    validateTemplate(template);
    return getTemplateById(template.getId())
      .compose(optionalTemplate -> optionalTemplate
        .map(t -> templateDao.updateTemplate(template))
        .orElse(Future.failedFuture(new NotFoundException(
          String.format("Template with id '%s' not found", template.getId()))))
      );
  }

  @Override
  public Future<Boolean> deleteTemplate(String id) {
    return templateDao.deleteTemplate(id);
  }

  @Override
  public Future<TemplateProcessingResult> processTemplate(
    TemplateProcessingRequest templateRequest, OkapiConnectionParams okapiConnectionParams) {

    Future<Template> templateByIdFuture = getTemplateById(templateRequest.getTemplateId())
      .map(optionalTemplate -> optionalTemplate.orElseThrow(() ->
        new BadRequestException(String.format("Template with id %s does not exist", templateRequest.getTemplateId()))));

    Future<LocaleConfiguration> localeConfigurationFuture = configurationClient.lookupLocaleConfig(okapiConnectionParams);

    return CompositeFuture.all(templateByIdFuture, localeConfigurationFuture)
      .compose(compositeFuture -> {
        Template template = compositeFuture.resultAt(0);
        validateTemplateProcessingRequest(templateRequest, template);

        LocalizedTemplatesProperty templateContent = template.getLocalizedTemplates().getAdditionalProperties()
          .get(templateRequest.getLang());
        JsonObject contextObject =
          Optional.ofNullable(templateRequest.getContext())
            .map(JsonObject::mapFrom)
            .orElse(new JsonObject());

        LocaleConfiguration config = compositeFuture.resultAt(1);
        formatDatesInJson(contextObject, config.getLanguageTag(), config.getTimeZoneId());

        String templateResolverAddress = templateResolverAddressesMap.get(template.getTemplateResolver());
        TemplateResolver templateResolverProxy = TemplateResolver.createProxy(vertx, templateResolverAddress);

        Future<JsonObject> future = Future.future();
        templateResolverProxy.processTemplate(
          JsonObject.mapFrom(templateContent),
          contextObject,
          templateRequest.getOutputFormat(), future.completer());

        return future.map(processedContent -> {
          Result processedTemplate = processedContent.mapTo(Result.class);
          Meta resultMetaInfo = new Meta()
            .withSize(processedTemplate.getBody().length())
            .withDateCreate(new Date())
            .withLang(templateRequest.getLang())
            .withOutputFormat(templateRequest.getOutputFormat());
          return new TemplateProcessingResult()
            .withResult(processedTemplate)
            .withMeta(resultMetaInfo)
            .withTemplateId(templateRequest.getTemplateId());
        });
      });
  }

  private void validateTemplate(Template template) {
    boolean templateResolverIsSupported = templateResolverAddressesMap.containsKey(template.getTemplateResolver());
    if (!templateResolverIsSupported) {
      String message = String.format("Template resolver '%s' is not supported", template.getTemplateResolver());
      throw new BadRequestException(message);
    }
  }

  private void validateTemplateProcessingRequest(TemplateProcessingRequest templateRequest, Template template) {
    if (!template.getOutputFormats().contains(templateRequest.getOutputFormat())) {
      String message = String.format("Requested template does not support '%s' output format",
        templateRequest.getOutputFormat());
      throw new BadRequestException(message);
    }

    boolean templateSupportsGivenLanguage =
      template.getLocalizedTemplates().getAdditionalProperties()
        .containsKey(templateRequest.getLang());
    if (!templateSupportsGivenLanguage) {
      String message = String.format("Requested template does not have localized template for language '%s'",
        templateRequest.getLang());
      throw new BadRequestException(message);
    }
  }

}
