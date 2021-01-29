package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.util.Map;

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

  public static Future<WrappedResponse> doRequest(Vertx vertx, String url,
    HttpMethod method, MultiMap headers, String payload,
    Integer expectedCode, String explanation, Handler<WrappedResponse> handler) {
    Promise<WrappedResponse> promise = Promise.promise();
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

    System.out.println(String.format("Sending %s request to url '%s with payload: %s'\n", method.toString(), url, payload));

    Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler = response -> {
      String explainString = "(no explanation)";
      if (explanation != null) {
        explainString = explanation;
      }
      if (expectedCode != null && expectedCode != response.result().statusCode()) {
        promise.fail(String.format("%s to %s failed. Expected status code %s, got status code %s : %s | %s",
          method.toString(), url, expectedCode, response.result().statusCode(), response.result().bodyAsString(), explainString));
      } else {
        System.out.println(String.format("Got status code %s with payload of: %s | %s",
          response.result().statusCode(), response.result().bodyAsString(), explainString));
        WrappedResponse wr = new WrappedResponse(explanation, response.result().statusCode(), response.result().bodyAsString(), response.result());
        handler.handle(wr);
        promise.complete(wr);
      }
    };

    if (method == HttpMethod.PUT || method == HttpMethod.POST) {
      request.sendJsonObject(new JsonObject(payload), responseHandler);
    } else {
      request.send(responseHandler);
    }

    return promise.future();
  }
}
