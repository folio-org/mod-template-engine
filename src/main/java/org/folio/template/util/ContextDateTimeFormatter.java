package org.folio.template.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.TimeZone;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import javafx.util.Pair;

public class ContextDateTimeFormatter {

  private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
    .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
    .optionalStart().appendOffset("+HH", "Z").optionalEnd()
    .toFormatter();

  private static final List<String> DATE_ONLY_TOKENS = Collections.unmodifiableList(Arrays.asList(
          "loan.dueDate",
          "loan.initialBorrowDate",
          "loan.checkedInDate",
          "request.requestExpirationDate",
          "request.holdShelfExpirationDate"
  ));

  private static final String ARRAY_SUFFIX = "[]";
  private static final String SEPARATOR = ".";

  private ContextDateTimeFormatter() {
  }

  public static void formatDatesInJson(JsonObject json, String languageTag, String zoneId) {
    mapValuesInJson(new StringBuilder(), json, getDateMapper(languageTag, zoneId), String.class);
  }

  private static <T> void mapValuesInJson(StringBuilder keyPath, JsonObject json, Function<Pair<String, T>, ?> mapper, Class<T> classToMap) {
    for (Map.Entry<String, Object> entry : json) {
      if (keyPath.length() != 0) {
        keyPath.append(SEPARATOR);
      }
      keyPath.append(entry.getKey());
      final Object value = entry.getValue();

      if (value == null) {
        continue;
      }
      if (value.getClass() == JsonObject.class) {
        mapValuesInJson(keyPath, (JsonObject) entry.getValue(), mapper, classToMap);

      } else if (value.getClass() == JsonArray.class) {
        mapValuesInJsonArray(keyPath.append(ARRAY_SUFFIX), (JsonArray) value, mapper, classToMap);

      } else if (value.getClass() == classToMap) {
        Pair<String, T> pair = new Pair<>(keyPath.toString(), classToMap.cast(value));
        Object mappedValue = mapper.apply(pair);
        json.put(entry.getKey(), mappedValue);
      }
    }
  }

  private static <T> void mapValuesInJsonArray(StringBuilder keyPath, JsonArray array, Function<Pair<String, T>, ?> mapper, Class<T> classToMap) {
    List list = array.getList();
    for (int i = 0; i < array.size(); i++) {
      Object value = array.getValue(i);
      if (value.getClass() == JsonObject.class) {
        mapValuesInJson(keyPath, (JsonObject) value, mapper, classToMap);

      } else if (value.getClass() == JsonArray.class) {
        mapValuesInJsonArray(keyPath, (JsonArray) value, mapper, classToMap);

      } else if (value.getClass() == classToMap) {
        Pair<String, T> pair = new Pair<>(keyPath.toString(), classToMap.cast(value));
        Object mappedValue = mapper.apply(pair);
        list.set(i, mappedValue);
      }
    }
  }

  private static Function<Pair<String, String>, String> getDateMapper(String languageTag, String zoneId) {
    TimeZone timeZone = TimeZone.getTimeZone(zoneId);
    Locale locale = Locale.forLanguageTag(languageTag);
    return pair -> localizeIfStringIsIsoDate(pair, timeZone, locale);
  }

  static String localizeIfStringIsIsoDate(Pair<String, String> keyAndValue, TimeZone timeZone, Locale locale) {
    String value = keyAndValue.getValue();
    try {
      ZonedDateTime parsedDateTime = ZonedDateTime.parse(value, ISO_DATE_TIME_FORMATTER);
      DateFormat i18NDateFormatter =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, getTimeFormatForToken(keyAndValue.getKey()), locale);
      i18NDateFormatter.setTimeZone(timeZone);

      return i18NDateFormatter.format(parsedDateTime.toInstant().toEpochMilli());
    } catch (DateTimeParseException e) {
      //value is not date
      return value;
    }
  }

  private static int getTimeFormatForToken(String token) {
    return DATE_ONLY_TOKENS.contains(token) ? DateFormat.NONE : DateFormat.SHORT;
  }
}
