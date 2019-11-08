package org.folio.template.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Locale;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ibm.icu.util.TimeZone;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ContextDateTimeFormatterTest {

  private static final String LANGUAGE_TAG = "en-US";
  private static final String TIMEZONE_ID = "UTC";

  @Test
  void allTheDatesAreLocalizedInJsonObject() {
    String inputIsoDate = "2019-06-18T14:04:33.205Z";
    String expectedFormattedDate = "6/18/19, 2:04 PM";

    JsonObject inputJson = new JsonObject()
      .put("dateInRoot", inputIsoDate)
      .put("subObject", new JsonObject().put("dateInSubObject", inputIsoDate))
      .put("notADateString", "some string")
      .put("nullValue", (String) null)
      .put("arrayInRoot",
        new JsonArray()
          .add(inputIsoDate)
          .add(new JsonObject()
            .put("dateInObjectInArray", inputIsoDate))
          .add(new JsonArray().add(inputIsoDate)));

    JsonObject expectedJson = new JsonObject()
      .put("dateInRoot", expectedFormattedDate)
      .put("subObject", new JsonObject().put("dateInSubObject", expectedFormattedDate))
      .put("notADateString", "some string")
      .put("nullValue", (String) null)
      .put("arrayInRoot",
        new JsonArray()
          .add(expectedFormattedDate)
          .add(new JsonObject()
            .put("dateInObjectInArray", expectedFormattedDate))
          .add(new JsonArray().add(expectedFormattedDate)));

    ContextDateTimeFormatter.formatDatesInJson(inputJson, LANGUAGE_TAG, TIMEZONE_ID);

    assertEquals(expectedJson, inputJson);
  }

//  @ParameterizedTest
//  @CsvSource(value = {
//    "Asia/Hong_Kong | zh-CN | 2019/9/18 下午10:04",
//    "Europe/Copenhagen | da-DK | 18.09.2019 16.04",
//    "Europe/London | en-GB | 18/09/2019, 15:04",
//    "Europe/Stockholm | en-SE | 2019-09-18, 16:04",
//    "America/New_York | en-US | 9/18/19, 10:04 AM",
//  }, delimiter = '|')
//  void shouldFormatDateDependingOnLocaleAndTimeZone(String timeZoneId, String langTag, String expected) {
//    String inputIsoDate = "2019-09-18T14:04:33.205Z";
//    String formattedDate = ContextDateTimeFormatter.localizeIfStringIsIsoDate(inputIsoDate,
//      TimeZone.getTimeZone(timeZoneId), Locale.forLanguageTag(langTag));
//
//    assertThat(formattedDate, Matchers.is(expected));
//  }
}
