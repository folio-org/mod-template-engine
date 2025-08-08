package org.folio.template.client;

public class LocaleSettings {

  private final String languageTag;
  private final String timeZoneId;

  public LocaleSettings(String languageTag, String timeZoneId) {
    this.languageTag = languageTag;
    this.timeZoneId = timeZoneId;
  }

  public String getLanguageTag() {
    return languageTag;
  }

  public String getTimeZoneId() {
    return timeZoneId;
  }
}
