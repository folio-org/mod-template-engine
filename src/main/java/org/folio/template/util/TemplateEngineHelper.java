package org.folio.template.util;

import com.github.wnameless.json.flattener.JsonFlattener;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.tools.parser.JsonPathParser;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.template.InUseTemplateException;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

public final class TemplateEngineHelper {

  private static final Logger LOG = LoggerFactory.getLogger("mod-template-engine");

  private static final String DATE_SUFFIX = "Date";
  private static final String TIME_SUFFIX = "Time";

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

    if (throwable instanceof InUseTemplateException) {
      return Response.status(400)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity("Cannot delete template which is currently in use")
        .build();
    }

    Promise<Response> promise = Promise.promise();
    ValidationHelper.handleError(throwable, promise);
    if (promise.future().isComplete()) {
      Response response = promise.future().result();
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

  public static void enrichContextWithDateTimes(JsonObject context) {
    Map<String, Object> contextMap = JsonFlattener.flattenAsMap(context.encode());
    JsonPathParser parser = new JsonPathParser(context);
    contextMap.keySet().stream()
      .filter(key -> objectIsNonBlankString(contextMap.get(key)))
      .filter(key -> key.endsWith(DATE_SUFFIX))
      .filter(key -> !contextMap.containsKey(key + TIME_SUFFIX))
      .forEach(key -> parser.setValueAt(key + TIME_SUFFIX, contextMap.get(key)));
  }

  private static boolean objectIsNonBlankString(Object obj) {
    return obj instanceof String
        && StringUtils.isNoneBlank((String) obj);
  }

}
