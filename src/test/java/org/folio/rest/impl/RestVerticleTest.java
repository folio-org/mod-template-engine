package org.folio.rest.impl;

import com.jayway.restassured.response.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {

  private Vertx vertx;
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));
  private final Logger logger = LoggerFactory.getLogger("TemplateEngineTest");

  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();

    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    } catch (Exception e) {
      e.printStackTrace();
      context.fail(e);
      return;
    }

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    PostgresClient.stopEmbeddedPostgres();
    vertx.close(context.asyncAssertSuccess());
  }

  //@Test
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

//  @Test
//  public void testDeleteTemplateStub(TestContext context) {
//    //TODO Implement tests
//    final Async async = context.async();
//    String url = "http://localhost:" + port;
//    String templateTestUrl = url + "/template/test";
//
//    Handler<HttpClientResponse> handler = response -> {
//      context.assertEquals(response.statusCode(), 204);
//      async.complete();
//    };
//    sendRequest(templateTestUrl, HttpMethod.DELETE, handler);
//  }

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
