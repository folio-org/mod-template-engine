package org.folio.template.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

class ContextDateTimeFormatterTest {

  private static final String LANGUAGE_TAG = "en-US";
  private static final String TIMEZONE_ID = "UTC";

  @Test
  void allTheDatesAreLocalizedInJsonObject() {

    String inputIsoDate = "2019-06-18T14:04:33.205Z";
    String expectedFormattedDate = "6/18/19 2:04 PM";

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
}
