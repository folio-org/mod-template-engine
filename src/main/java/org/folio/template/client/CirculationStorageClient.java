package org.folio.template.client;


import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.folio.rest.RestVerticle;
import org.folio.template.util.OkapiConnectionParams;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class CirculationStorageClient {

  private WebClient webClient;

  public CirculationStorageClient(Vertx vertx) {
    webClient = WebClient.create(vertx);
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

    return future.map(resp -> resp.body().toJsonObject());
  }
}
