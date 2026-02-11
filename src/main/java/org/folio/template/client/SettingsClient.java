package org.folio.template.client;

import static java.lang.String.format;

import java.util.Map;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.template.util.OkapiModuleClientException;

public class SettingsClient extends OkapiClient {
  private static final Logger LOG = LogManager.getLogger("mod-template-engine");

  private static final String DEFAULT_LANGUAGE_TAG = "en-US";
  private static final String DEFAULT_TIMEZONE_ID = "UTC";
  private static final String LOCALE_REQUEST_PATH = "/locale";

  public SettingsClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<LocaleSettings> lookupLocaleSetting() {
    LOG.debug("lookupLocaleConfig:: Lookup locale setting");
    var configs = getLocale()
      .map(this::mapToLocaleSettings);
    LOG.info("lookupLocaleConfig:: Locale setting looked up successfully");

    return configs;
  }

  private Future<JsonObject> getLocale() {
    LOG.debug("getLocale:: Getting locale settings from /locale endpoint");
    return getAbs(LOCALE_REQUEST_PATH).send()
      .map(response -> {
        if (response.statusCode() != HttpStatus.HTTP_OK.toInt()) {
          LOG.warn("getLocale:: Error getting locale settings. Status: {}, body: {}",
            response::statusCode, response::body);
          throw new OkapiModuleClientException(
            format("Error getting locale settings. Status: %d, body: %s",
              response.statusCode(), response.body()), response.statusCode());
        }
        LOG.info("getLocale:: Locale settings retrieved successfully.");
        return response.bodyAsJsonObject();
      });
  }

  private LocaleSettings mapToLocaleSettings(JsonObject localeSettings) {
    LOG.debug("mapToLocaleSettings:: Mapping {} to locale setting", localeSettings);
    String languageTag = localeSettings.getString("locale", DEFAULT_LANGUAGE_TAG);
    String timezoneId = localeSettings.getString("timezone", DEFAULT_TIMEZONE_ID);
    LOG.info("mapToLocaleSettings:: Mapped to locale setting with Language Tag: {}, Timezone ID: {}",
      languageTag, timezoneId);
    return new LocaleSettings(languageTag, timezoneId);
  }
}
