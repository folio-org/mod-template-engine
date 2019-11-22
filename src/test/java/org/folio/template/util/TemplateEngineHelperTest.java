package org.folio.template.util;

import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.folio.template.InUseTemplateException;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.*;

class TemplateEngineHelperTest {

  @Test
  void contextIsEnrichedWithAdditionalDateTimeTokens() {
    String inputDate = "2019-06-18T14:04:33.205Z";

    JsonObject inputJson = new JsonObject()
      .put("rootDate", inputDate)
      .put("emptyDate", "")
      .put("blankDate", "   ")
      .putNull("nullDate")
      .put("loan", new JsonObject()
        .put("dueDate", inputDate)
        .put("enrichableDate", inputDate)
        .put("existingDate", inputDate)
        .put("existingDateTime", "unchanged"));

    JsonObject expectedJson = new JsonObject()
      .put("rootDate", inputDate)
      .put("emptyDate", "")
      .put("blankDate", "   ")
      .putNull("nullDate")
      .put("loan", new JsonObject()
        .put("dueDate", inputDate)
        .put("enrichableDate", inputDate)
        .put("existingDate", inputDate)
        .put("existingDateTime", "unchanged")
        .put("dueDateTime", inputDate)
        .put("enrichableDateTime", inputDate))
      .put("rootDateTime", inputDate);

    TemplateEngineHelper.enrichContextWithDateTimes(inputJson);
    assertEquals(expectedJson, inputJson);
  }

  @Test
  void mapToExceptionTest() {
    Response response = TemplateEngineHelper.mapExceptionToResponse(new BadRequestException());
    assertEquals(response.getStatus(), HttpStatus.SC_BAD_REQUEST);
    assertEquals(response.getMediaType().toString(), MediaType.TEXT_PLAIN);

    response = TemplateEngineHelper.mapExceptionToResponse(new NotFoundException());
    assertEquals(response.getStatus(), HttpStatus.SC_NOT_FOUND);
    assertEquals(response.getMediaType().toString(), MediaType.TEXT_PLAIN);

    response = TemplateEngineHelper.mapExceptionToResponse(new InUseTemplateException());
    assertEquals(response.getStatus(), HttpStatus.SC_BAD_REQUEST);
    assertEquals(response.getMediaType(), MediaType.TEXT_PLAIN_TYPE);
    assertEquals(response.getEntity(), "Cannot delete template which is currently in use");

    response = TemplateEngineHelper.mapExceptionToResponse(new NullPointerException());
    assertEquals(response.getStatus(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

}
