package org.folio.template.util;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.vertx.core.Promise;
import io.vertx.serviceproxy.ServiceException;
import org.folio.HttpStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.template.InUseTemplateException;

public final class TemplateEngineHelper {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");

  public static final String TEMPLATE_RESOLVERS_LOCAL_MAP = "template-resolvers.map";

  // Reserved context key used to carry the tenant's configured locale (BCP-47 language tag)
  // to template resolvers, so locale-aware helpers (e.g. Handlebars numberFormat) can default
  // to it. The underscore prefix avoids collision with author-supplied tokens.
  public static final String TENANT_LOCALE_CONTEXT_KEY = "_tenantLocale";

  private TemplateEngineHelper() {
  }

  public static Response mapExceptionToResponse(Throwable throwable) {
    LOG.debug("mapExceptionToResponse:: Mapping Exception to Response");
    if (throwable instanceof BadRequestException) {
      LOG.warn("Bad request exception occurred", throwable);
      return Response.status(HttpStatus.SC_BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    }

    if (throwable instanceof NotFoundException) {
      LOG.warn("Not found exception occurred", throwable);
      return Response.status(HttpStatus.SC_NOT_FOUND)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    }

    if (throwable instanceof InUseTemplateException) {
      LOG.warn("In use template exception occurred", throwable);
      return Response.status(HttpStatus.SC_BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity("Cannot delete template which is currently in use")
        .build();
    }

    // A resolver running across the EventBus service proxy reports an author/template
    // error (e.g. malformed Handlebars) as a ServiceException carrying HTTP 400 as its
    // failure code; the original exception type is erased in transit.
    if (throwable instanceof ServiceException serviceException
        && serviceException.failureCode() == HttpStatus.SC_BAD_REQUEST) {
      LOG.warn("Template resolver reported a client error", throwable);
      return Response.status(HttpStatus.SC_BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    }

    Promise<Response> promise = Promise.promise();
    ValidationHelper.handleError(throwable, promise::handle);
    if (promise.future().isComplete()) {
      Response response = promise.future().result();
      if (response.getStatus() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        LOG.warn("Internal server error occurred", throwable);
      }
      LOG.info("mapExceptionToResponse:: Mapped Exception to response with status code {}", response.getStatus());
      return response;
    }
    LOG.warn("Internal server error occurred : {}", throwable.getMessage(), throwable);
    return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .type(MediaType.TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }

}
