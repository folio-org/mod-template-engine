package org.folio.template.util;

import static org.apache.commons.lang3.StringUtils.endsWithIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.parser.JsonPathParser;

public class ContextDateTimeFormatter {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");

  private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
    .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
    .optionalStart().appendOffset("+HH", "Z").optionalEnd()
    .toFormatter();

  private static final String DATE_SUFFIX = "Date";
  private static final String DATE_TIME_SUFFIX = "DateTime";
  private static final String DETAILED_DATE_TIME_SUFFIX = "DetailedDateTime";

  private ContextDateTimeFormatter() {
  }

  public static void formatDatesInContext(JsonObject context, String languageTag, String zoneId) {
    Map<String, Object> contextMap = JsonFlattener.flattenAsMap(context.encode());
    JsonPathParser parser = new JsonPathParser(context);

    for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
      String token = entry.getKey();
      Optional<DateFormat> dateFormat = getDateFormatForToken(token, languageTag, zoneId);
      if (dateFormat.isPresent() && objectIsNonBlankString(entry.getValue())) {
        try {
          ZonedDateTime parsedDateTime = ZonedDateTime.parse((String) entry.getValue(), ISO_DATE_TIME_FORMATTER);
          String formattedDate = dateFormat.get().format(parsedDateTime.toInstant().toEpochMilli());
          parser.setValueAt(token, formattedDate);
        } catch (DateTimeParseException e) {
          // value is not a valid date
          LOG.error(e.getMessage(), e);
        }
      }
    }
  }

  private static Optional<DateFormat> getDateFormatForToken(String token, String languageTag, String zoneId) {
    DateFormat i18NDateFormatter = null;
    if (endsWithIgnoreCase(token, DETAILED_DATE_TIME_SUFFIX)) {
      i18NDateFormatter = getDateFormat(DateFormat.LONG, DateFormat.SHORT, languageTag, zoneId);
    } else if (endsWithIgnoreCase(token, DATE_SUFFIX)) {
      i18NDateFormatter = getDateFormat(DateFormat.SHORT, DateFormat.NONE, languageTag, zoneId);
    } else if (endsWithIgnoreCase(token, DATE_TIME_SUFFIX)) {
      i18NDateFormatter = getDateFormat(DateFormat.SHORT, DateFormat.SHORT, languageTag, zoneId);
    }

    return Optional.ofNullable(i18NDateFormatter);
  }

  private static DateFormat getDateFormat(int dateStyle, int timeStyle, String languageTag, String zoneId) {
    TimeZone timeZone = TimeZone.getTimeZone(zoneId);
    Locale locale = Locale.forLanguageTag(languageTag);
    DateFormat dateFormat = DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale);
    dateFormat.setTimeZone(timeZone);
    return dateFormat;
  }

  private static boolean objectIsNonBlankString(Object obj) {
    return obj instanceof String && isNotBlank((String) obj);
  }

}
