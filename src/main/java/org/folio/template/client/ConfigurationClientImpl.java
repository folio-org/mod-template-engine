package org.folio.template.client;


import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.folio.HttpStatus;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.template.util.OkapiConnectionParams;
import org.folio.template.util.OkapiModuleClientException;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

public class ConfigurationClientImpl implements ConfigurationClient {

  private static final String DEFAULT_LANGUAGE_TAG = "en-US";
  private static final String DEFAULT_TIMEZONE_ID = "UTC";

  private WebClient webClient;
  private String configRequestPath;

  public ConfigurationClientImpl(Vertx vertx) {
    this.webClient = vertx.getOrCreateContext().get("httpClient");
    this.configRequestPath = System.getProperty("config.client.path", "/configurations/entries");
  }

  @Override
  public Future<LocaleConfiguration> lookupLocaleConfig(OkapiConnectionParams okapiConnectionParams) {
    return lookupConfigByModuleAndConfigName("ORG", "localeSettings", 0, 1, okapiConnectionParams)
      .map(this::mapToLocaleConfiguration);
  }

  private Future<Configurations> lookupConfigByModuleAndConfigName(String moduleName, String configName,
                                                                   int offset, int limit, OkapiConnectionParams params) {
    String query = String.format("module=%s and configName=%s", moduleName, configName);
    return lookupConfigByQuery(query, offset, limit, params);
  }

  private Future<Configurations> lookupConfigByQuery(String query, int offset, int limit, OkapiConnectionParams params) {

    Future<HttpResponse<Buffer>> future = Future.future();
    String requestUrl = params.getOkapiUrl() + configRequestPath;
    webClient.getAbs(requestUrl)
      .addQueryParam("query", query)
      .addQueryParam("offset", Integer.toString(offset))
      .addQueryParam("limit", Integer.toString(limit))
      .putHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
      .putHeader(RestVerticle.OKAPI_HEADER_TENANT, params.getTenant())
      .putHeader(RestVerticle.OKAPI_HEADER_TOKEN, params.getToken())
      .send(future.completer());

    return future.map(response -> {
      if (response.statusCode() != HttpStatus.HTTP_OK.toInt()) {
        String logMessage =
          String.format("Error getting config by module name. Status: %d, body: %s", response.statusCode(), response.body());
        throw new OkapiModuleClientException(logMessage);
      }
      return response.bodyAsJsonObject().mapTo(Configurations.class);
    });
  }

  private LocaleConfiguration mapToLocaleConfiguration(Configurations configurations) {
    JsonObject localeConfig = Optional.ofNullable(configurations.getConfigs())
      .map(Collection::stream)
      .orElse(Stream.empty())
      .findFirst()
      .map(Config::getValue)
      .map(JsonObject::new)
      .orElse(new JsonObject());

    String languageTag = localeConfig.getString("locale", DEFAULT_LANGUAGE_TAG);
    String timezoneId = localeConfig.getString("timezone", DEFAULT_TIMEZONE_ID);
    return new LocaleConfiguration(languageTag, timezoneId);
  }
}
