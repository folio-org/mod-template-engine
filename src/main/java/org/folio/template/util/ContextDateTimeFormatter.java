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
import org.apache.commons.lang3.tuple.Pair;
import org.drools.core.util.StringUtils;

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

  private static final String ARRAY_SUFFIX_FORMAT = "%s[%d]";
  private static final String TOKEN_SEPARATOR = ".";

  private ContextDateTimeFormatter() {
  }

  public static void formatDatesInJson(JsonObject json, String languageTag, String zoneId) {
    mapValuesInJson(StringUtils.EMPTY, json, getDateMapper(languageTag, zoneId), String.class);
  }

  private static <T> void mapValuesInJson(String parentToken,
                                          JsonObject json,
                                          Function<Pair<String, T>, ?> mapper,
                                          Class<T> classToMap) {

    for (Map.Entry<String, Object> entry : json) {
      final Object value = entry.getValue();

      if (value == null) {
        continue;
      }

      final String token = buildTokenForKey(parentToken, entry.getKey());

      if (value.getClass() == JsonObject.class) {
        mapValuesInJson(token, (JsonObject) entry.getValue(), mapper, classToMap);

      } else if (value.getClass() == JsonArray.class) {
        mapValuesInJsonArray(token, (JsonArray) value, mapper, classToMap);

      } else if (value.getClass() == classToMap) {
        Pair<String, T> tokenAndValue = Pair.of(token, classToMap.cast(value));
        Object mappedValue = mapper.apply(tokenAndValue);
        json.put(entry.getKey(), mappedValue);
      }
    }
  }

  private static <T> void mapValuesInJsonArray(String parentToken,
                                               JsonArray array,
                                               Function<Pair<String, T>, ?> mapper,
                                               Class<T> classToMap) {
    List list = array.getList();
    for (int i = 0; i < array.size(); i++) {
      final String token = String.format(ARRAY_SUFFIX_FORMAT, parentToken, i);
      final Object value = array.getValue(i);

      if (value.getClass() == JsonObject.class) {
        mapValuesInJson(token, (JsonObject) value, mapper, classToMap);

      } else if (value.getClass() == JsonArray.class) {
        mapValuesInJsonArray(token, (JsonArray) value, mapper, classToMap);

      } else if (value.getClass() == classToMap) {
        Pair<String, T> tokenAndValue = Pair.of(token, classToMap.cast(value));
        Object mappedValue = mapper.apply(tokenAndValue);
        list.set(i, mappedValue);
      }
    }
  }

  private static Function<Pair<String, String>, String> getDateMapper(String languageTag, String zoneId) {
    TimeZone timeZone = TimeZone.getTimeZone(zoneId);
    Locale locale = Locale.forLanguageTag(languageTag);
    return tokenAndValue -> localizeIfStringIsIsoDate(tokenAndValue, timeZone, locale);
  }

  static String localizeIfStringIsIsoDate(Pair<String, String> tokenAndValue, TimeZone timeZone, Locale locale) {
    final String value = tokenAndValue.getValue();
    try {
      ZonedDateTime parsedDateTime = ZonedDateTime.parse(value, ISO_DATE_TIME_FORMATTER);
      int timeFormat = DATE_ONLY_TOKENS.contains(tokenAndValue.getKey()) ? DateFormat.NONE : DateFormat.SHORT;
      DateFormat i18NDateFormatter =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, timeFormat, locale);
      i18NDateFormatter.setTimeZone(timeZone);

      return i18NDateFormatter.format(parsedDateTime.toInstant().toEpochMilli());
    } catch (DateTimeParseException e) {
      //value is not date
      return value;
    }
  }

  private static String buildTokenForKey(String parentToken, String key) {
    StringBuilder builder = new StringBuilder(parentToken);
    if (!parentToken.isEmpty()) {
      builder.append(TOKEN_SEPARATOR);
    }
    return builder.append(key).toString();
  }

}
