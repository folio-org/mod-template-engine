package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.client.WebClient;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.template.dao.TemplateDaoImpl;
import org.folio.template.resolver.MustacheTemplateResolver;
import org.folio.template.resolver.TemplateResolver;
import org.folio.template.util.TemplateEngineHelper;

import java.net.URL;
import java.util.MissingResourceException;


public class InitAPIs implements InitAPI {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    LOG.debug("init:: Started Initialising");
    context.put("webClient", WebClient.create(vertx));
    URL u = InitAPIs.class.getClassLoader().getResource(TemplateDaoImpl.TEMPLATE_SCHEMA_PATH);
    if (u == null) {
      LOG.warn("Failed to find resource: {}" , TemplateDaoImpl.TEMPLATE_SCHEMA_PATH);
      resultHandler.handle(Future.failedFuture(new MissingResourceException(TemplateDaoImpl.TEMPLATE_SCHEMA_PATH,
        InitAPIs.class.getName(), TemplateDaoImpl.TEMPLATE_SCHEMA_PATH)));
    } else {
      LOG.info("init:: Registering Template resolver");
      registerTemplateResolver("mustache",
        "template-resolver.mustache.queue", new MustacheTemplateResolver(), vertx);

      resultHandler.handle(Future.succeededFuture(true));
    }
  }

  private <T extends TemplateResolver> void registerTemplateResolver(String name, String address,
                                                                     T resolverInstance, Vertx vertx) {
    LOG.debug("registerTemplateResolver:: Registering Template resolver with name {} and address {}", name, address);
    LocalMap<String, String> templateResolverAddressesMap = vertx.sharedData()
      .getLocalMap(TemplateEngineHelper.TEMPLATE_RESOLVERS_LOCAL_MAP);

    registerService(vertx, address, TemplateResolver.class, resolverInstance);
    templateResolverAddressesMap.put(name, address);
  }

  private <T> void registerService(Vertx vertx, String address, Class<T> clazz, T service) {
    LOG.debug("registerService:: Registering the service with address {}", address);
    new ServiceBinder(vertx).setAddress(address).register(clazz, service);
  }

}
