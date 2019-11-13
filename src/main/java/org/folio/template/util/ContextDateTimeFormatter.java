package org.folio.template.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.TimeZone;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;

public class ContextDateTimeFormatter {

  private static final Logger LOG = LoggerFactory.getLogger("mod-template-engine");

  private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
    .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
    .optionalStart().appendOffset("+HH", "Z").optionalEnd()
    .toFormatter();

  private static final String DATE_SUFFIX = "Date";
  private static final String DATE_TIME_SUFFIX = "DateTime";

  private ContextDateTimeFormatter() {
  }

  public static void formatDatesInJson(JsonObject json, String languageTag, String zoneId) {
    mapValuesInJson(json, getDateMapper(languageTag, zoneId), String.class);
  }

  private static <T> void mapValuesInJson(JsonObject json, Function<Pair<String, T>, ?> mapper, Class<T> classToMap) {
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
        Pair<String, T> keyAndValue = Pair.of(entry.getKey(), classToMap.cast(value));
        Object mappedValue = mapper.apply(keyAndValue);
        json.put(entry.getKey(), mappedValue);
      }
    }
  }

  private static <T> void mapValuesInJsonArray(JsonArray array, Function<Pair<String, T>, ?> mapper, Class<T> classToMap) {
    List list = array.getList();
    for (int i = 0; i < array.size(); i++) {

      Object value = array.getValue(i);
      if (value.getClass() == JsonObject.class) {
        mapValuesInJson((JsonObject) value, mapper, classToMap);

      } else if (value.getClass() == JsonArray.class) {
        mapValuesInJsonArray((JsonArray) value, mapper, classToMap);

      } else if (value.getClass() == classToMap) {
        String key = (String) list.get(i);
        Pair<String, T> keyAndValue = Pair.of(key, classToMap.cast(value));
        Object mappedValue = mapper.apply(keyAndValue);
        list.set(i, mappedValue);
      }
    }
  }

  private static Function<Pair<String, String>, String> getDateMapper(String languageTag, String zoneId) {
    TimeZone timeZone = TimeZone.getTimeZone(zoneId);
    Locale locale = Locale.forLanguageTag(languageTag);
    return keyAndValue -> localizeIfStringIsIsoDate(keyAndValue, timeZone, locale);
  }

  static String localizeIfStringIsIsoDate(Pair<String, String> keyAndValue, TimeZone timeZone, Locale locale) {
    String value = keyAndValue.getValue();
    Optional<Integer> timeFormat = getTimeFormatForToken(keyAndValue.getKey());
    if (!timeFormat.isPresent()) {
      return value;
    }

    try {
      ZonedDateTime parsedDateTime = ZonedDateTime.parse(value, ISO_DATE_TIME_FORMATTER);
      DateFormat i18NDateFormatter =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, timeFormat.get(), locale);
      i18NDateFormatter.setTimeZone(timeZone);
      return i18NDateFormatter.format(parsedDateTime.toInstant().toEpochMilli());
    } catch (DateTimeParseException e) {
      //value is not a valid date
      LOG.error(e.getMessage(), e);
      return value;
    }
  }

  private static Optional<Integer> getTimeFormatForToken(String token) {
    Integer timeFormat = null;
    if (token.endsWith(DATE_SUFFIX)) {
      timeFormat = DateFormat.NONE;
    } else if (token.endsWith(DATE_TIME_SUFFIX)) {
      timeFormat = DateFormat.SHORT;
    }
    return Optional.ofNullable(timeFormat);
  }
}
