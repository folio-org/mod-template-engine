package org.folio.rest.impl;

import java.util.Map;
import java.util.Optional;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for API tests
 */
public class ApiTestHelper {
  static class WrappedResponse {
    private String explanation;
    private int code;
    private String body;
    private JsonObject json;
    private HttpResponse<Buffer> response;

    public WrappedResponse(String explanation, int code, String body, HttpResponse<Buffer> response) {
      this.explanation = explanation;
      this.code = code;
      this.body = body;
      this.response = response;
      try {
        json = new JsonObject(body);
      } catch (Exception e) {
        json = null;
      }
    }

    public String getExplanation() {
      return explanation;
    }

    public int getCode() {
      return code;
    }

    public String getBody() {
      return body;
    }

    public HttpResponse<Buffer> getResponse() {
      return response;
    }

    public JsonObject getJson() {
      return json;
    }
  }

  private static final Logger logger = LogManager.getLogger("ApiTestHelper");

  public static Future<WrappedResponse> doRequest(Vertx vertx, String url,
    HttpMethod method, MultiMap headers, String payload,
    Integer expectedCode, String explanation, Handler<WrappedResponse> handler) {
    WebClient client = WebClient.create(vertx);
    HttpRequest<Buffer> request = client.requestAbs(method, url);
    //Add standard headers
    request.putHeader("X-Okapi-Tenant", Postgres.getTenant())
      .putHeader("content-type", "application/json")
      .putHeader("accept", method.equals(HttpMethod.DELETE) ? "text/plain" : "application/json");
    if (headers != null) {
      for (Map.Entry entry : headers.entries()) {
        request.putHeader((String) entry.getKey(), (String) entry.getValue());
        System.out.println(String.format("Adding header '%s' with value '%s'",
          entry.getKey(), entry.getValue()));
      }
    }

    logger.info("Sending {} request to url '{} with payload: {}'\n", method, url, payload);

    Future<HttpResponse<Buffer>> futureResponse;
    if (method == HttpMethod.PUT || method == HttpMethod.POST) {
      futureResponse = request.sendJsonObject(new JsonObject(payload));
    } else {
      futureResponse = request.send();
    }

    return futureResponse.compose(response -> {
      var explainString = Optional.ofNullable(explanation).orElse("(no explanation)");
      var statusCode = response.statusCode();
      var body = response.bodyAsString();
      if (expectedCode != null && expectedCode != statusCode) {
        return Future.failedFuture(String.format("%s to %s failed. Expected status code %s, got status code %s : %s | %s",
          method, url, expectedCode, statusCode, body, explainString));
      } else {
        logger.info("Got status code {} with payload of: {} | {}", statusCode, body, explainString);
        WrappedResponse wr = new WrappedResponse(explanation, statusCode, body, response);
        handler.handle(wr);
        return Future.succeededFuture(wr);
      }
    });
  }
}
