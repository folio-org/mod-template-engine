package org.folio.template.service;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.BadRequestException;

import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.LocalizedTemplates;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplatePreviewRequest;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.template.client.LocaleSettings;
import org.folio.template.client.SettingsClient;
import org.folio.template.dao.TemplateDao;
import org.folio.template.resolver.HandlebarsTemplateResolver;
import org.folio.template.resolver.MustacheTemplateResolver;
import org.folio.template.resolver.TemplateResolver;
import org.folio.template.util.TemplateEngineHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;

@ExtendWith(VertxExtension.class)
class TemplateServiceImplTest {

  private static final String LANG = "en";
  private static final String FORMAT = "text/html";

  private TemplateServiceImpl newServiceWithMocks(Vertx vertx, TemplateDao dao, SettingsClient settings)
    throws Exception {
    TemplateServiceImpl service = new TemplateServiceImpl(vertx, Map.of(TENANT, "test_tenant"));
    setField(service, "templateDao", dao);
    setField(service, "settingsClient", settings);
    return service;
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = TemplateServiceImpl.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private Template template(String resolver, LocalizedTemplatesProperty localized) {
    return new Template()
      .withId("11111111-1111-1111-1111-111111111111")
      .withTemplateResolver(resolver)
      .withOutputFormats(List.of(FORMAT))
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(LANG, localized));
  }

  private TemplateProcessingRequest request(String templateId) {
    return new TemplateProcessingRequest()
      .withTemplateId(templateId)
      .withLang(LANG)
      .withOutputFormat(FORMAT);
  }

  @Test
  void processTemplateWithUnregisteredResolverFailsWithBadRequest(Vertx vertx, VertxTestContext ctx)
    throws Exception {
    TemplateDao dao = mock(TemplateDao.class);
    SettingsClient settings = mock(SettingsClient.class);

    Template template = template("definitely-not-registered",
      new LocalizedTemplatesProperty().withHeader("Hi").withBody("Body"));
    when(dao.getTemplateById(template.getId())).thenReturn(Future.succeededFuture(Optional.of(template)));
    when(settings.lookupLocaleSetting()).thenReturn(Future.succeededFuture(new LocaleSettings("en-US", "UTC")));

    TemplateServiceImpl service = newServiceWithMocks(vertx, dao, settings);

    service.processTemplate(request(template.getId())).onComplete(ctx.failing(err -> ctx.verify(() -> {
      assertInstanceOf(BadRequestException.class, err);
      assertEquals("Template resolver 'definitely-not-registered' is not supported", err.getMessage());
      ctx.completeNow();
    })));
  }

  @Test
  void processTemplateWithBodylessTemplateDoesNotNpeAndReportsSizeZero(Vertx vertx, VertxTestContext ctx)
    throws Exception {
    // Register a real resolver on the EventBus so the process flow reaches the meta-size step.
    String address = "template-resolver.mustache.test";
    new ServiceBinder(vertx).setAddress(address).register(TemplateResolver.class, new MustacheTemplateResolver());
    vertx.sharedData().<String, String>getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP)
      .put("mustache", address);

    TemplateDao dao = mock(TemplateDao.class);
    SettingsClient settings = mock(SettingsClient.class);

    // Header-only template: no body -> Result.getBody() is null (H6 NPE without the guard).
    Template template = template("mustache", new LocalizedTemplatesProperty().withHeader("Hi"));
    when(dao.getTemplateById(template.getId())).thenReturn(Future.succeededFuture(Optional.of(template)));
    when(settings.lookupLocaleSetting()).thenReturn(Future.succeededFuture(new LocaleSettings("en-US", "UTC")));

    TemplateServiceImpl service = newServiceWithMocks(vertx, dao, settings);

    service.processTemplate(request(template.getId())).onComplete(ctx.succeeding(result -> ctx.verify(() -> {
      assertEquals(0, result.getMeta().getSize());
      ctx.completeNow();
    })));
  }

  @Test
  void previewTemplateWithoutResolverDefaultsToMustache(Vertx vertx, VertxTestContext ctx) throws Exception {
    // Only mustache is registered: had the blank resolver defaulted to anything else,
    // the supported-resolver check would reject the request.
    String address = "template-resolver.mustache.test";
    new ServiceBinder(vertx).setAddress(address).register(TemplateResolver.class, new MustacheTemplateResolver());
    vertx.sharedData().<String, String>getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP)
      .put("mustache", address);

    TemplateDao dao = mock(TemplateDao.class);
    SettingsClient settings = mock(SettingsClient.class);
    when(settings.lookupLocaleSetting()).thenReturn(Future.succeededFuture(new LocaleSettings("en-US", "UTC")));

    TemplateServiceImpl service = newServiceWithMocks(vertx, dao, settings);

    TemplatePreviewRequest req = new TemplatePreviewRequest()  // no templateResolver set
      .withHeader("Hi")
      .withBody("Hello {{name}}")
      .withContext(new Context().withAdditionalProperty("name", "Alex"));

    service.previewTemplate(req).onComplete(ctx.succeeding(result -> ctx.verify(() -> {
      assertEquals("Hello Alex", result.getBody());
      ctx.completeNow();
    })));
  }

  @Test
  void processTemplateWithHandlebarsResolverRendersHandlebarsSyntax(Vertx vertx, VertxTestContext ctx)
    throws Exception {
    // {{else}} is Handlebars-only, so rendering "Y" proves the Handlebars engine was used.
    String address = "template-resolver.handlebars.queue";
    new ServiceBinder(vertx).setAddress(address).register(TemplateResolver.class, new HandlebarsTemplateResolver());
    vertx.sharedData().<String, String>getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP)
      .put("handlebars", address);

    TemplateDao dao = mock(TemplateDao.class);
    SettingsClient settings = mock(SettingsClient.class);

    Template template = template("handlebars",
      new LocalizedTemplatesProperty().withHeader("Hi").withBody("{{#if flag}}Y{{else}}N{{/if}}"));
    when(dao.getTemplateById(template.getId())).thenReturn(Future.succeededFuture(Optional.of(template)));
    when(settings.lookupLocaleSetting()).thenReturn(Future.succeededFuture(new LocaleSettings("en-US", "UTC")));

    TemplateServiceImpl service = newServiceWithMocks(vertx, dao, settings);

    TemplateProcessingRequest req = request(template.getId())
      .withContext(new Context().withAdditionalProperty("flag", true));

    service.processTemplate(req).onComplete(ctx.succeeding(result -> ctx.verify(() -> {
      assertEquals("Y", result.getResult().getBody());
      ctx.completeNow();
    })));
  }
}
