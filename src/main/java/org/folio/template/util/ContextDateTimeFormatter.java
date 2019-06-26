package org.folio.template.util;

import io.vertx.core.json.JsonObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class ContextDateTimeFormatter {

  private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
    .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
    .optionalStart().appendOffset("+HH", "Z").optionalEnd()
    .toFormatter();

  private ContextDateTimeFormatter() {
  }

  public static void formatDatesInJson(JsonObject json, String languageTag, String zoneId) {
    mapValuesInJson(json, getDateMapper(languageTag, zoneId), String.class);
  }

  private static <T> void mapValuesInJson(JsonObject json, Function<T, ?> mapper, Class<T> classToMap) {
    for (Map.Entry<String, Object> entry : json) {
      Object value = entry.getValue();
      if (value == null) {
        continue;
      }
      if (value.getClass() == JsonObject.class) {
        mapValuesInJson((JsonObject) entry.getValue(), mapper, classToMap);
      } else if (value.getClass() == classToMap) {
        Object mappedValue = mapper.apply(classToMap.cast(value));
        json.put(entry.getKey(), mappedValue);
      }
    }
  }

  private static Function<String, String> getDateMapper(String languageTag, String zoneId) {
    return value -> localizeIfStringIsIsoDate(value, zoneId, languageTag);
  }

  private static String localizeIfStringIsIsoDate(String value, String zoneId, String languageTag) {
    try {
      ZonedDateTime parsedDateTime = ZonedDateTime.parse(value, ISO_DATE_TIME_FORMATTER)
        .withZoneSameInstant(ZoneId.of(zoneId));
      DateTimeFormatter localizedFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(Locale.forLanguageTag(languageTag));

      return parsedDateTime.format(localizedFormatter);
    } catch (DateTimeParseException e) {
      //value is not date
      return value;
    }
  }
}
