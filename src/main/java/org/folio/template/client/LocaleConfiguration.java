package org.folio.template.client;

public class LocaleConfiguration {

  private final String languageTag;
  private final String timeZoneId;

  public LocaleConfiguration(String languageTag, String timeZoneId) {
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
