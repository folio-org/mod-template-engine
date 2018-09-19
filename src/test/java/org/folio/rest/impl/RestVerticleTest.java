package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {

  private Vertx vertx;

  private int port;

  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();
    port = NetworkUtils.nextFreePort();
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testGetTemplateStub(TestContext context) {
    //TODO Replace testing stub
    final Async async = context.async();
    String url = "http://localhost:" + port;
    String templateTestUrl = url + "/template/test";

    Handler<HttpClientResponse> handler = response -> {
      context.assertEquals(response.statusCode(), 200);
      context.assertEquals(response.headers().get("content-type"), "application/json");
      response.handler(body -> {
        async.complete();
      });
    };
    sendRequest(templateTestUrl, HttpMethod.GET, handler);
  }

  @Test
  public void testDeleteTemplateStub(TestContext context) {
    //TODO Implement tests
//    final Async async = context.async();
//    String url = "http://localhost:" + port;
//    String templateTestUrl = url + "/template/test";
//
//    Handler<HttpClientResponse> handler = response -> {
//      context.assertEquals(response.statusCode(), 204);
//      async.complete();
//    };
//    sendRequest(templateTestUrl, HttpMethod.DELETE, handler);
  }

  private void sendRequest(String url, HttpMethod method, Handler<HttpClientResponse> handler) {
    sendRequest(url, method, handler, "");
  }

  private void sendRequest(String url, HttpMethod method, Handler<HttpClientResponse> handler, String content) {
    Buffer buffer = Buffer.buffer(content);
    vertx.createHttpClient()
      .requestAbs(method, url, handler)
      .putHeader("x-okapi-tenant", "diku")
      .putHeader("Accept", "application/json,text/plain")
      .end(buffer);
  }
}
