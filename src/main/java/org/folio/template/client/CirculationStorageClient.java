package org.folio.template.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.template.util.OkapiModuleClientException;

import java.util.Map;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.folio.HttpStatus.HTTP_OK;

public class CirculationStorageClient extends OkapiClient {

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  private static final String PATRON_NOTICE_POLICIES_URL =
    "/patron-notice-policy-storage/patron-notice-policies";

  public CirculationStorageClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
  }

  public Future<JsonObject> findPatronNoticePolicies(String query, int limit) {
    LOG.debug("findPatronNoticePolicies:: Finding Patron Notice Policies with query {}", query);
    return getMany(PATRON_NOTICE_POLICIES_URL, query, limit).future()
      .compose(resp -> resp.statusCode() == HTTP_OK.toInt()
        ? succeededFuture(resp.bodyAsJsonObject())
        : failedFuture(new OkapiModuleClientException(format("Error getting patron notice " +
        "policies. Status: %d, body: %s", resp.statusCode(), resp.body()))));
  }
}
