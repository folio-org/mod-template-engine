package org.folio.service.template.engine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.LocalizedTemplate;
import org.folio.rest.jaxrs.model.Meta;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.jaxrs.model.TemplateProcessingResult;
import org.folio.service.template.engine.resolver.TemplateResolver;
import org.folio.service.template.util.TemplateEngineHelper;
import org.folio.util.handler.AbstractRequestHandler;

import java.util.Date;
import java.util.Map;

public class TemplateEngineServiceImpl implements TemplateEngineService {

  private static final Logger LOG = LoggerFactory.getLogger(TemplateEngineServiceImpl.class);

  private Map<String, String> templateResolverAddressesMap;
  private Vertx vertx;

  public TemplateEngineServiceImpl(Vertx vertx) {
    this.vertx = vertx;
    templateResolverAddressesMap = vertx.sharedData().getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP);
  }

  @Override
  public void processTemplate(JsonObject templateJson,
                              JsonObject templateProcessingRequestJson,
                              Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    new ProcessTemplateHandler(templateJson, templateProcessingRequestJson, asyncResultHandler).run();
  }

  private class ProcessTemplateHandler extends AbstractRequestHandler {

    private final Template template;
    private final TemplateProcessingRequest templateRequest;
    private final Handler<AsyncResult<JsonObject>> asyncResultHandler;

    public ProcessTemplateHandler(JsonObject templateJson,
                                  JsonObject templateProcessingRequestJson,
                                  Handler<AsyncResult<JsonObject>> asyncResultHandler) {
      this.template = templateJson.mapTo(Template.class);
      this.templateRequest = templateProcessingRequestJson.mapTo(TemplateProcessingRequest.class);
      this.asyncResultHandler = asyncResultHandler;
    }

    @Override
    protected void handle() {
      LocalizedTemplate localizedTemplate =
        template.getLocalizedTemplates().stream()
          .filter(t -> t.getLang().equals(templateRequest.getLang()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(
            String.format("Localized template for lang '%s' does not exist", templateRequest.getLang())));

      String templateResolverAddress = templateResolverAddressesMap.get(template.getTemplateResolver());
      TemplateResolver templateResolverProxy = TemplateResolver.createProxy(vertx, templateResolverAddress);

      templateResolverProxy.processTemplate(
        localizedTemplate.getTemplate(),
        JsonObject.mapFrom(templateRequest.getContext()),
        templateRequest.getOutputFormat(),
        wrapWithFailureHandler(this::handleTemplateResolverResponse));
    }

    private void handleTemplateResolverResponse(String result) {
      Meta resultMetaInfo = new Meta()
        .withSize(result.length())
        .withDateCreate(new Date())
        .withLang(templateRequest.getLang())
        .withOutputFormat(templateRequest.getOutputFormat());
      TemplateProcessingResult processingResult = new TemplateProcessingResult()
        .withResult(result)
        .withMeta(resultMetaInfo)
        .withTemplateId(templateRequest.getTemplateId());
      asyncResultHandler.handle(Future.succeededFuture(JsonObject.mapFrom(processingResult)));
    }

    @Override
    protected void handleException(Throwable e) {
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.failedFuture(e));
    }
  }
}
