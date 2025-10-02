package org.folio.template.service;

import static io.vertx.core.Future.failedFuture;
import static java.lang.String.format;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.jaxrs.model.Meta;
import org.folio.rest.jaxrs.model.Result;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.model.TemplateProcessingResult;
import org.folio.template.InUseTemplateException;
import org.folio.template.client.CirculationStorageClient;
import org.folio.template.client.LocaleSettings;
import org.folio.template.client.SettingsClient;
import org.folio.template.dao.TemplateDao;
import org.folio.template.dao.TemplateDaoImpl;
import org.folio.template.resolver.TemplateResolver;
import org.folio.template.util.OkapiModuleClientException;
import org.folio.template.util.TemplateContextPreProcessor;
import org.folio.template.util.TemplateEngineHelper;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;


public class TemplateServiceImpl implements TemplateService {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  private Vertx vertx;
  private TemplateDao templateDao;
  private Map<String, String> templateResolverAddressesMap;
  private SettingsClient settingsClient;
  private CirculationStorageClient circulationStorageClient;


  public TemplateServiceImpl(Vertx vertx, Map<String, String> okapiHeaders) {
    this.vertx = vertx;
    this.templateDao = new TemplateDaoImpl(vertx, okapiHeaders.get(TENANT));
    this.templateResolverAddressesMap = vertx.sharedData().getLocalMap(
      TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP);
    this.settingsClient = new SettingsClient(vertx, okapiHeaders);
    this.circulationStorageClient = new CirculationStorageClient(vertx, okapiHeaders);
  }

  public Future<List<Template>> getTemplates(String query, int offset, int limit) {
    LOG.debug("getTemplates:: Retrieving Templates with query {}", query);
    return templateDao.getTemplates(query, offset, limit);
  }

  @Override
  public Future<Optional<Template>> getTemplateById(String id) {
    LOG.debug("getTemplateById:: Retrieving Template by ID : {}", id);
    return templateDao.getTemplateById(id);
  }

  @Override
  public Future<String> addTemplate(Template template) {
    LOG.debug("addTemplate:: Adding Template with ID : {}", template.getId());
    validateTemplate(template);
    if (template.getId() == null) {
      template.setId(UUID.randomUUID().toString());
    }
    return templateDao.addTemplate(template);
  }

  @Override
  public Future<Boolean> updateTemplate(Template template) {
    LOG.debug("updateTemplate:: Updating Template with ID : {}", template.getId());
    validateTemplate(template);
    return getTemplateById(template.getId())
      .compose(optionalTemplate -> optionalTemplate
        .map(t -> templateDao.updateTemplate(template))
        .orElse(failedFuture(new NotFoundException(
          String.format("Template with id '%s' not found", template.getId()))))
      );
  }

  @Override
  public Future<Boolean> deleteTemplate(String id) {
    LOG.debug("deleteTemplate:: deleting Template with ID : {}", id);
    String query = format("loanNotices == \"*\\\"templateId\\\": \\\"%1$s\\\"*\" " +
      "OR requestNotices == \"*\\\"templateId\\\": \\\"%1$s\\\"*\" " +
      "OR feeFineNotices == \"*\\\"templateId\\\": \\\"%1$s\\\"*\"", id);

    return circulationStorageClient.findPatronNoticePolicies(query, 0)
      .compose(policies -> policies.getInteger("totalRecords") == 0 ?
        templateDao.deleteTemplate(id) : failedFuture(new InUseTemplateException()))
      .recover(throwable -> {
        // indicates that route is not found (returned from folio-module-sidecar/gateway)
        if (throwable instanceof OkapiModuleClientException clientException) {
          if (Objects.equals(clientException.getStatus(), 404)) {
            return templateDao.deleteTemplate(id);
          }
        }
        return failedFuture(throwable);
      });
  }

  @Override
  public Future<TemplateProcessingResult> processTemplate(TemplateProcessingRequest templateRequest) {
    LOG.debug("processTemplate:: Processing Template with ID : {}", templateRequest.getTemplateId());
    Future<Template> templateByIdFuture = getTemplateById(templateRequest.getTemplateId())
      .map(optionalTemplate -> optionalTemplate.orElseThrow(() ->
        new BadRequestException(String.format("Template with id %s does not exist", templateRequest.getTemplateId()))));

    Future<LocaleSettings> localeConfigurationFuture = settingsClient.lookupLocaleSetting();

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

        LocaleSettings config = compositeFuture.resultAt(1);

        TemplateContextPreProcessor preProcessor = new TemplateContextPreProcessor(templateContent, contextObject, config);
        preProcessor.process();

        String templateResolverAddress = templateResolverAddressesMap.get(template.getTemplateResolver());
        TemplateResolver templateResolverProxy = TemplateResolver.createProxy(vertx, templateResolverAddress);

        Promise<JsonObject> promise = Promise.promise();
        templateResolverProxy.processTemplate(
          JsonObject.mapFrom(templateContent),
          contextObject,
          templateRequest.getOutputFormat(), promise);

        return promise.future().map(processedContent -> {
          Result processedTemplate = processedContent
            .mapTo(Result.class)
            .withAttachments(preProcessor.getAttachments());
          Meta resultMetaInfo = new Meta()
            .withSize(processedTemplate.getBody().length())
            .withDateCreate(new Date())
            .withLang(templateRequest.getLang())
            .withOutputFormat(templateRequest.getOutputFormat());

          LOG.info("processTemplate:: Template processed successfully");

          return new TemplateProcessingResult()
            .withResult(processedTemplate)
            .withMeta(resultMetaInfo)
            .withTemplateId(templateRequest.getTemplateId());
        });
      });
  }

  private void validateTemplate(Template template) {
    LOG.debug("validateTemplate:: Validating Template with ID : {}", template.getId());
    boolean templateResolverIsSupported = templateResolverAddressesMap.containsKey(template.getTemplateResolver());
    if (!templateResolverIsSupported) {
      LOG.warn("Template resolver {} is not Supported", template.getTemplateResolver());
      String message = String.format("Template resolver '%s' is not supported", template.getTemplateResolver());
      throw new BadRequestException(message);
    }
  }

  private void validateTemplateProcessingRequest(TemplateProcessingRequest templateRequest, Template template) {
    LOG.debug("validateTemplateProcessingRequest:: Validating template Processing request with Template ID : {}", templateRequest.getTemplateId());
    if (!template.getOutputFormats().contains(templateRequest.getOutputFormat())) {
      LOG.warn("Requested template does not support {} output format", templateRequest.getOutputFormat());
      String message = String.format("Requested template does not support '%s' output format",
        templateRequest.getOutputFormat());
      throw new BadRequestException(message);
    }

    boolean templateSupportsGivenLanguage =
      template.getLocalizedTemplates().getAdditionalProperties()
        .containsKey(templateRequest.getLang());
    if (!templateSupportsGivenLanguage) {
      LOG.warn("Requested template does not have localized template for language {}", templateRequest.getLang());
      String message = String.format("Requested template does not have localized template for language '%s'",
        templateRequest.getLang());
      throw new BadRequestException(message);
    }
  }
}
