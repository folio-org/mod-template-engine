package org.folio.template.util;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.http.HttpStatus;
import org.folio.rest.tools.utils.ValidationHelper;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public final class TemplateEngineHelper {

  private static final Logger LOG = LoggerFactory.getLogger("mod-template-engine");

  public static final String TEMPLATE_RESOLVERS_LOCAL_MAP = "template-resolvers.map";

  private TemplateEngineHelper() {
  }

  public static Response mapExceptionToResponse(Throwable throwable) {
    if (throwable instanceof BadRequestException) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    }
    if (throwable instanceof NotFoundException) {
      return Response.status(HttpStatus.SC_NOT_FOUND)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    }
    Future<Response> validationFuture = Future.future();
    ValidationHelper.handleError(throwable, validationFuture.completer());
    if (validationFuture.isComplete()) {
      Response response = validationFuture.result();
      if (response.getStatus() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        LOG.error(throwable.getMessage(), throwable);
      }
      return response;
    }
    LOG.error(throwable.getMessage(), throwable);
    return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .type(MediaType.TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }

}
