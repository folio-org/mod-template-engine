package org.folio.template.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.TimeZone;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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

      } else if (value.getClass() == JsonArray.class) {
        mapValuesInJsonArray((JsonArray) value, mapper, classToMap);

      } else if (value.getClass() == classToMap) {
        Object mappedValue = mapper.apply(classToMap.cast(value));
        json.put(entry.getKey(), mappedValue);
      }
    }
  }

  private static <T> void mapValuesInJsonArray(JsonArray array, Function<T, ?> mapper, Class<T> classToMap) {
    List list = array.getList();
    for (int i = 0; i < array.size(); i++) {

      Object value = array.getValue(i);
      if (value.getClass() == JsonObject.class) {
        mapValuesInJson((JsonObject) value, mapper, classToMap);

      } else if (value.getClass() == JsonArray.class) {
        mapValuesInJsonArray((JsonArray) value, mapper, classToMap);

      } else if (value.getClass() == classToMap) {
        Object mappedValue = mapper.apply(classToMap.cast(value));
        list.set(i, mappedValue);
      }
    }
  }

  private static Function<String, String> getDateMapper(String languageTag, String zoneId) {
    TimeZone timeZone = TimeZone.getTimeZone(zoneId);
    Locale locale = Locale.forLanguageTag(languageTag);
    return value -> localizeIfStringIsIsoDate(value, timeZone, locale);
  }

  static String localizeIfStringIsIsoDate(String value, TimeZone timeZone, Locale locale) {
    try {
      ZonedDateTime parsedDateTime = ZonedDateTime.parse(value, ISO_DATE_TIME_FORMATTER);
      DateFormat i18NDateFormatter =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
      i18NDateFormatter.setTimeZone(timeZone);

      return i18NDateFormatter.format(parsedDateTime.toInstant().toEpochMilli());
    } catch (DateTimeParseException e) {
      //value is not date
      return value;
    }
  }
}
