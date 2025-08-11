package org.folio.template.client;

import static java.lang.String.format;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.template.util.OkapiModuleClientException;

public class SettingsClient extends OkapiClient {
  private static final Logger LOG = LogManager.getLogger("mod-template-engine");

  private static final String DEFAULT_LANGUAGE_TAG = "en-US";
  private static final String DEFAULT_TIMEZONE_ID = "UTC";
  private static final String LOCALE_SETTINGS_SCOPE = "stripes-core.prefs.manage";
  private static final String LOCALE_SETTINGS_KEY = "tenantLocaleSettings";
  private final String settingsRequestPath;

  public SettingsClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
    this.settingsRequestPath = System.getProperty("config.client.path", "/settings/entries");
  }

  public Future<LocaleSettings> lookupLocaleSetting() {
    LOG.debug("lookupLocaleConfig:: Lookup locale setting");
    var configs = lookupSettingsByScopeAndKey(LOCALE_SETTINGS_SCOPE, LOCALE_SETTINGS_KEY, 1, 0)
      .map(this::mapToLocaleSettings);
    LOG.info("lookupLocaleConfig:: Locale setting looked up successfully");

    return configs;
  }

  private Future<JsonObject> lookupSettingsByScopeAndKey(String scope, String key, int limit, int offset) {

    LOG.debug("lookupSettingsByScopeAndKey:: Lookup locale settings by scope {} and key {}", scope, key);
    String query = format("scope==%s and key==%s", scope, key);
    return lookupSettingsByQuery(query, limit, offset);
  }

  private Future<JsonObject> lookupSettingsByQuery(String query, int limit, int offset) {
    LOG.debug("lookupSettingsByQuery:: Lookup settings by Query {}", query);
    return getMany(settingsRequestPath, query, limit, offset).future()
      .map(response -> {
        if (response.statusCode() != HttpStatus.HTTP_OK.toInt()) {
          LOG.warn("lookupSettingsByQuery:: Error getting locale settings. Status: {}, body: {}",
            response.statusCode(), response.body());
          throw new OkapiModuleClientException(
            format("Error getting locale settings. Status: %d, body: %s", response.statusCode(), response.body()));
        }
        LOG.info("lookupSettingsByQuery:: Locale settings by query looked up successfully.");
      return response.bodyAsJsonObject();
    });
  }

  private LocaleSettings mapToLocaleSettings(JsonObject localeSettings) {
    LOG.debug("mapToLocaleSettings:: Mapping {} to locale setting", localeSettings);
    JsonObject localeSetting = Optional.ofNullable(localeSettings.getJsonArray("items"))
      .map(JsonArray::stream)
      .orElse(Stream.empty())
      .filter(Objects::nonNull)
      .map(JsonObject.class::cast)
      .findFirst()
      .orElse(new JsonObject());
    LOG.debug("mapToLocaleSettings:: Found locale setting: {}", localeSetting);

    var valueObj = Optional.ofNullable(localeSetting.getJsonObject("value")).orElse(new JsonObject());
    String languageTag = valueObj.getString("locale", DEFAULT_LANGUAGE_TAG);
    String timezoneId = valueObj.getString("timezone", DEFAULT_TIMEZONE_ID);
    LOG.info("mapToLocaleSettings:: Mapped to locale setting with Language Tag: {}, Timezone ID: {}",
      languageTag, timezoneId);
    return new LocaleSettings(languageTag, timezoneId);
  }
}
