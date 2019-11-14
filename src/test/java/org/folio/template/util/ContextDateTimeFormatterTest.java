package org.folio.template.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
      .put("rootDateTime", inputIsoDate)
      .put("subObject", new JsonObject().put("subObjectDateTime", inputIsoDate))
      .put("notADateString", "some string")
      .put("nullValue", (String) null)
      .put("arrayInRoot",
        new JsonArray()
          .add(inputIsoDate)
          .add(new JsonObject()
            .put("arrayDateTime", inputIsoDate))
          .add(new JsonArray().add(inputIsoDate)));

    JsonObject expectedJson = new JsonObject()
      .put("rootDateTime", expectedFormattedDate)
      .put("subObject", new JsonObject().put("subObjectDateTime", expectedFormattedDate))
      .put("notADateString", "some string")
      .put("nullValue", (String) null)
      .put("arrayInRoot",
        new JsonArray()
          .add(inputIsoDate)
          .add(new JsonObject()
            .put("arrayDateTime", expectedFormattedDate))
          .add(new JsonArray().add(inputIsoDate)));

    ContextDateTimeFormatter.formatDatesInContext(inputJson, LANGUAGE_TAG, TIMEZONE_ID);
    assertEquals(expectedJson, inputJson);
  }

  @Test
  void datesAreLocalizedCorrectlyBasedOnToken() {
    String inputDate = "2019-06-18T14:04:33.205Z";
    String expectedLongDate = "6/18/19, 2:04 PM";
    String expectedShortDate = "6/18/19";

    JsonObject inputJson = new JsonObject()
      .put("dateWithInvalidToken", inputDate)
      .put("loan", new JsonObject()
        .put("dueDate", inputDate)
        .put("dueDateTime", inputDate)
        .put("dateWithInvalidToken", inputDate));

    JsonObject expectedJson = new JsonObject()
      .put("dateWithInvalidToken", inputDate)
      .put("loan", new JsonObject()
        .put("dueDate", expectedShortDate)
        .put("dueDateTime", expectedLongDate)
        .put("dateWithInvalidToken", inputDate));

    ContextDateTimeFormatter.formatDatesInContext(inputJson, LANGUAGE_TAG, TIMEZONE_ID);
    assertEquals(expectedJson, inputJson);
  }

  @Test
  void valueRemainsUnchangedIfDateTokenContainsInvalidDate() {
    String validDate = "2019-06-18T14:04:33.205Z";
    String invalidDate = "2019-06-18T14:04:33";
    String expectedShortDate = "6/18/19";

    JsonObject inputJson = new JsonObject()
      .put("loan", new JsonObject()
        .put("dueDate", validDate)
        .put("dueDateTime", invalidDate));

    JsonObject expectedJson = new JsonObject()
      .put("loan", new JsonObject()
        .put("dueDate", expectedShortDate)
        .put("dueDateTime", invalidDate));

    ContextDateTimeFormatter.formatDatesInContext(inputJson, LANGUAGE_TAG, TIMEZONE_ID);
    assertEquals(expectedJson, inputJson);
  }

  @Test
  void datesInArraysAreFormattedCorrectly() {
    String inputDate = "2019-06-18T14:04:33.205Z";
    String expectedLongDate = "6/18/19, 2:04 PM";

    JsonObject inputJson = new JsonObject()
      .put("loan", new JsonArray()
        .add(new JsonObject()
          .put("dueDateTime", inputDate)))
      .put("request", new JsonObject()
        .put("requestExpirationDateTime", new JsonArray()
          .add(inputDate)));

    JsonObject expectedJson = new JsonObject()
      .put("loan", new JsonArray()
        .add(new JsonObject()
          .put("dueDateTime", expectedLongDate)))
      .put("request", new JsonObject()
        .put("requestExpirationDateTime", new JsonArray()
          .add(inputDate)));

    ContextDateTimeFormatter.formatDatesInContext(inputJson, LANGUAGE_TAG, TIMEZONE_ID);
    assertEquals(expectedJson, inputJson);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Asia/Hong_Kong | zh-CN | 2019/9/18 下午10:04",
    "Europe/Copenhagen | da-DK | 18.09.2019 16.04",
    "Europe/London | en-GB | 18/09/2019, 15:04",
    "Europe/Stockholm | en-SE | 2019-09-18, 16:04",
    "America/New_York | en-US | 9/18/19, 10:04 AM",
  }, delimiter = '|')
  void shouldFormatDateDependingOnLocaleAndTimeZone(String timeZoneId, String langTag, String expected) {
    String inputIsoDate = "2019-09-18T14:04:33.205Z";
    JsonObject context = new JsonObject().put("inputDateTime", inputIsoDate);
    ContextDateTimeFormatter.formatDatesInContext(context, langTag, timeZoneId);
    String formattedDateTime = context.getString("inputDateTime");
    assertThat(formattedDateTime, Matchers.is(expected));
  }
}
