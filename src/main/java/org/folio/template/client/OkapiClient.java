package org.folio.template.client;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.Map;

import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.okapi.common.WebClientFactory.getWebClient;
import static org.folio.okapi.common.XOkapiHeaders.REQUEST_ID;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;

public class OkapiClient {
  private final WebClient webClient;
  private final String okapiUrl;
  private final String tenant;
  private final String token;
  private final String requestId;

  public OkapiClient(Vertx vertx, Map<String, String> okapiHeaders) {
    CaseInsensitiveMap<String, String> headers = new CaseInsensitiveMap<>(okapiHeaders);
    this.webClient = getWebClient(vertx);
    okapiUrl = headers.get(URL);
    tenant = headers.get(TENANT);
    token = headers.get(TOKEN);
    requestId = headers.get(REQUEST_ID);
  }

  HttpRequest<Buffer> getAbs(String path) {
    return webClient.requestAbs(HttpMethod.GET, okapiUrl + path)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .putHeader(URL, okapiUrl)
      .putHeader(TENANT, tenant)
      .putHeader(TOKEN, token)
      .putHeader(REQUEST_ID, requestId);
  }

  HttpRequest<Buffer> postAbs(String path) {
    return webClient.requestAbs(HttpMethod.POST, okapiUrl + path)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .putHeader(URL, okapiUrl)
      .putHeader(TENANT, tenant)
      .putHeader(TOKEN, token)
      .putHeader(REQUEST_ID, requestId);
  }

  public Promise<HttpResponse<Buffer>> getMany(String path, String query, int limit) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    HttpRequest<Buffer> request = getAbs(path)
      .addQueryParam("query", query)
      .addQueryParam("limit", String.valueOf(limit));
    request.send(promise);

    return promise;
  }

  public Promise<HttpResponse<Buffer>> getMany(String path, String query, int limit, int offset) {
    HttpRequest<Buffer> request = getAbs(path)
      .addQueryParam("query", query)
      .addQueryParam("limit", Integer.toString(limit))
      .addQueryParam("offset", Integer.toString(offset));
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    request.send(promise);

    return promise;
  }
}
