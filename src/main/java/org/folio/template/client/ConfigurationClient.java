package org.folio.template.client;


import static java.lang.String.format;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.template.util.OkapiModuleClientException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class ConfigurationClient extends OkapiClient {
  private static final Logger LOG = LogManager.getLogger("mod-template-engine");

  private static final String DEFAULT_LANGUAGE_TAG = "en-US";
  private static final String DEFAULT_TIMEZONE_ID = "UTC";
  private String configRequestPath;

  public ConfigurationClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
    this.configRequestPath = System.getProperty("config.client.path", "/configurations/entries");
  }

  public Future<LocaleConfiguration> lookupLocaleConfig() {
    LOG.debug("lookupLocaleConfig:: Lookup locale configuration");
    return lookupConfigByModuleAndConfigName("ORG", "localeSettings", 1, 0)
      .map(this::mapToLocaleConfiguration);
  }

  private Future<Configurations> lookupConfigByModuleAndConfigName(String moduleName,
    String configName, int limit, int offset) {

    LOG.debug("lookupConfigByModuleAndConfigName:: Lookup locale configuration by Module Name {} and Config Name {}",moduleName,configName);
    String query = format("module=%s and configName=%s", moduleName, configName);
    return lookupConfigByQuery(query, limit, offset);
  }

  private Future<Configurations> lookupConfigByQuery(String query, int limit, int offset) {
    LOG.debug("lookupConfigByQuery:: Lookup configuration by Query {}",query);
    return getMany(configRequestPath, query, limit, offset).future()
      .map(response -> {
        if (response.statusCode() != HttpStatus.HTTP_OK.toInt()) {
          LOG.warn("Error getting config by module name. Status: {}, body: {}", response.statusCode(), response.body());
          throw new OkapiModuleClientException(format("Error getting config by module name. " +
            "Status: %d, body: %s", response.statusCode(), response.body()));
        }
      return response.bodyAsJsonObject().mapTo(Configurations.class);
    });
  }

  private LocaleConfiguration mapToLocaleConfiguration(Configurations configurations) {
    LOG.debug("mapToLocaleConfiguration:: Mapping configurations {} to locale configuration", configurations);
    JsonObject localeConfig = Optional.ofNullable(configurations.getConfigs()).stream()
      .flatMap(Collection::stream)
      .findFirst()
      .map(Config::getValue)
      .map(JsonObject::new)
      .orElse(new JsonObject());

    String languageTag = localeConfig.getString("locale", DEFAULT_LANGUAGE_TAG);
    String timezoneId = localeConfig.getString("timezone", DEFAULT_TIMEZONE_ID);
    return new LocaleConfiguration(languageTag, timezoneId);
  }
}
