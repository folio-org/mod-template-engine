package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.serviceproxy.ServiceBinder;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.service.template.engine.TemplateEngineService;
import org.folio.service.template.engine.resolver.MustacheTemplateResolver;
import org.folio.service.template.engine.resolver.TemplateResolver;
import org.folio.service.template.storage.TemplateStorageService;
import org.folio.service.template.util.TemplateEngineHelper;

import java.net.URL;
import java.util.MissingResourceException;


public class InitAPIs implements InitAPI {

  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    URL u = InitAPIs.class.getClassLoader().getResource(TemplateResourceImpl.TEMPLATE_SCHEMA_PATH);
    if (u == null) {
      resultHandler.handle(Future.failedFuture(new MissingResourceException(TemplateResourceImpl.TEMPLATE_SCHEMA_PATH,
        InitAPIs.class.getName(), TemplateResourceImpl.TEMPLATE_SCHEMA_PATH)));
    } else {
      registerService(vertx,
        TemplateEngineHelper.TEMPLATE_STORAGE_SERVICE_ADDRESS,
        TemplateStorageService.class, TemplateStorageService.create(vertx));
      registerService(vertx,
        TemplateEngineHelper.TEMPLATE_ENGINE_SERVICE_ADDRESS,
        TemplateEngineService.class, TemplateEngineService.create(vertx));

      registerTemplateResolver("mustache",
        "template-resolver.mustache.queue", new MustacheTemplateResolver(), vertx);

      resultHandler.handle(Future.succeededFuture(true));
    }
  }

  private <T> void registerService(Vertx vertx, String address, Class<T> clazz, T service) {
    new ServiceBinder(vertx).setAddress(address).register(clazz, service);
  }

  private <T extends TemplateResolver> void registerTemplateResolver(String name, String address,
                                                                     T resolverInstance, Vertx vertx) {
    LocalMap<String, String> templateResolverAddressesMap = vertx.sharedData()
      .getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP);

    registerService(vertx, address, TemplateResolver.class, resolverInstance);
    templateResolverAddressesMap.put(name, address);
  }

}
