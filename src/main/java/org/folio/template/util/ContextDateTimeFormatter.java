package org.folio.template.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.github.wnameless.json.flattener.JsonFlattener;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.TimeZone;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.tools.parser.JsonPathParser;

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

  public static void formatDatesInContext(JsonObject context, String languageTag, String zoneId) {
    TimeZone timeZone = TimeZone.getTimeZone(zoneId);
    Locale locale = Locale.forLanguageTag(languageTag);

    Map<String, Object> contextMap = JsonFlattener.flattenAsMap(context.encode());
    JsonPathParser parser = new JsonPathParser(context);

    for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
      String token = entry.getKey();
      Optional<Integer> timeFormat = getTimeFormatForToken(token);
      if (timeFormat.isPresent()) {
        try {
          ZonedDateTime parsedDateTime = ZonedDateTime.parse((String) entry.getValue(), ISO_DATE_TIME_FORMATTER);
          DateFormat i18NDateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, timeFormat.get(), locale);
          i18NDateFormatter.setTimeZone(timeZone);
          String formattedDate = i18NDateFormatter.format(parsedDateTime.toInstant().toEpochMilli());
          parser.setValueAt(token, formattedDate);
        } catch (DateTimeParseException e) {
          // value is not a valid date
          LOG.error(e.getMessage(), e);
        }
      }
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
