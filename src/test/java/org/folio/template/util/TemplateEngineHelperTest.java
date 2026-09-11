package org.folio.template.util;

import io.vertx.serviceproxy.ServiceException;
import org.folio.HttpStatus;
import org.folio.template.InUseTemplateException;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.*;

class TemplateEngineHelperTest {

  @Test
  void mapToExceptionTest() {
    Response response = TemplateEngineHelper.mapExceptionToResponse(new BadRequestException());
    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertEquals(MediaType.TEXT_PLAIN, response.getMediaType().toString());

    response = TemplateEngineHelper.mapExceptionToResponse(new NotFoundException());
    assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
    assertEquals(MediaType.TEXT_PLAIN, response.getMediaType().toString());

    response = TemplateEngineHelper.mapExceptionToResponse(new InUseTemplateException());
    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
    assertEquals("Cannot delete template which is currently in use", response.getEntity());

    response = TemplateEngineHelper.mapExceptionToResponse(new NullPointerException());
    assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
  }

  @Test
  void mapServiceExceptionToResponseTest() {
    // A resolver author/template error arrives as a ServiceException carrying HTTP 400 (H2).
    Response response = TemplateEngineHelper.mapExceptionToResponse(
      new ServiceException(HttpStatus.SC_BAD_REQUEST, "Failed to process template: bad syntax"));
    assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus());
    assertEquals(MediaType.TEXT_PLAIN, response.getMediaType().toString());
    assertEquals("Failed to process template: bad syntax", response.getEntity());

    // A generic resolver/server failure carries failure code -1 and must stay a 500 (no regression).
    response = TemplateEngineHelper.mapExceptionToResponse(new ServiceException(-1, "boom"));
    assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
  }

}
