package org.folio.template.util;

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

}
