package org.folio.template.client;


import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.folio.rest.RestVerticle;
import org.folio.template.util.OkapiConnectionParams;
import org.folio.template.util.OkapiModuleClientException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class CirculationStorageClient {

  private WebClient webClient;

  public CirculationStorageClient(Vertx vertx) {
    webClient = vertx.getOrCreateContext().get("httpClient");
  }

  public Future<JsonObject> findPatronNoticePolicies(String query, int limit, OkapiConnectionParams params) {
    Future<HttpResponse<Buffer>> future = Future.future();

    webClient.getAbs(params.getOkapiUrl() + "/patron-notice-policy-storage/patron-notice-policies")
      .addQueryParam("query", query)
      .addQueryParam("limit", String.valueOf(limit))
      .putHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
      .putHeader(RestVerticle.OKAPI_HEADER_TENANT, params.getTenant())
      .putHeader(RestVerticle.OKAPI_HEADER_TOKEN, params.getToken())
      .send(future.completer());

    return future.compose(resp -> resp.statusCode() == 200 ?
      succeededFuture(resp.body().toJsonObject()) :
      failedFuture(new OkapiModuleClientException(format(
        "Error getting patron notice policies. Status: %d, body: %s", resp.statusCode(), resp.body()))));
  }
}
