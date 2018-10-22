package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {

  private static final String TEMPLATES_TABLE_NAME = "template";

  private Vertx vertx;
  private final Logger logger = LoggerFactory.getLogger("TemplateEngineTest");
  private static String templateUrl;
  private static String okapiUrl;

  private JsonArray outputFormat = new JsonArray()
    .add("text")
    .add("html");

  private JsonObject template1 = new JsonObject()
    .put("header", "Hello message for ${context.user.name}")
    .put("body", "Hello ${context.user.name}");

  private JsonObject template2 = new JsonObject()
    .put("header", "Hello message for ${context.user.name}")
    .put("body", "Hallo ${context.user.name}");

  private JsonObject templates = new JsonObject()
    .put("en", template1)
    .put("de", template2);

  private JsonObject templateObject = new JsonObject()
    .put("description", "template for change password")
    .put("outputFormats", outputFormat)
    .put("templateResolver", "mustache")
    .put("localizedTemplates", templates);

  private static final String NEW_TEMPLATE_DESCRIPTION = "New description";

  private JsonObject templateObject2 = templateObject
    .put("description", NEW_TEMPLATE_DESCRIPTION);

  @Before
  public void setUp(TestContext context) throws IOException {
    Async async = context.async();
    int port = NetworkUtils.nextFreePort();
    TenantClient tenantClient = new TenantClient("localhost", port, "diku", "diku");
    vertx = Vertx.vertx();
    okapiUrl = "http://localhost:" + port;
    templateUrl = okapiUrl + "/templates";

    DeploymentOptions options = new DeploymentOptions().setConfig(
      new JsonObject()
        .put("http.port", port)
    );
    try {
//      PostgresClient.setIsEmbedded(true);
//      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    } catch (Exception e) {
      e.printStackTrace();
      context.fail(e);
      return;
    }
    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      try {
        tenantClient.postTenant(null, res2 -> {
          async.complete();
        });
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    clearTemplatesTable(context);


  }
  private void clearTemplatesTable(TestContext context) {
    PostgresClient.getInstance(vertx, "diku").delete(TEMPLATES_TABLE_NAME, new Criterion(), event -> {
      if (event.failed()) {
        context.fail(event.cause());
      }
    });
  }

  @After
  public void tearDown(TestContext context) {
    PostgresClient.stopEmbeddedPostgres();
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testTemplates(TestContext context) {
    Async async = context.async();
    String[] idF = new String[1];
    Future<ApiTestHelper.WrappedResponse> chainedFuture =
      // test post request and creating of template
      doPost(context, templateObject, h -> {
        context.assertEquals(h.getCode(), 201);
        context.assertNotNull(h.getJson().getString("id"));
        // test get request and getting list of templates
      }).compose(w -> doGetAll(context, h -> {
        context.assertEquals(h.getCode(), 200);
        context.assertNotNull(h.getJson());
        JsonObject list = h.getJson();
        context.assertEquals(list.getInteger("totalRecords"), 1);
        JsonArray listObjects = list.getJsonArray("templates");
        JsonObject template = listObjects.getJsonObject(0);
        String id = template.getString("id");
        context.assertNotNull(id);
        idF[0] = id;
        // test get request with custom query
      })).compose(w -> doGet(context, "id==" + idF[0], h -> {
        context.assertEquals(h.getCode(), 200);
        context.assertNotNull(h.getJson().getJsonArray("templates").getJsonObject(0));
        // test put request and updating template ad database
      })).compose(w -> doPutById(context, idF[0], templateObject2, h -> {
        context.assertEquals(h.getCode(), 200);
        context.assertEquals(h.getJson().getString("description"), NEW_TEMPLATE_DESCRIPTION);
        // test get request and getting single template by id
      })).compose(w -> doGetById(context, idF[0], h -> {
        context.assertEquals(h.getCode(), 200);
        context.assertEquals(h.getJson().getString("description"), NEW_TEMPLATE_DESCRIPTION);
        // test delete request and deleting entity from datbase
      })).compose(w -> doDeleteById(context, idF[0], h -> {
        context.assertEquals(h.getCode(), 204);
        // check that there are no entities was found after deleting
      })).compose(w -> doGetAll(context, h -> {
        context.assertEquals(h.getCode(), 200);
        context.assertEquals(h.getJson().getInteger("totalRecords"), 0);
      }));

    chainedFuture.setHandler(chainedRes -> {
      if (chainedRes.failed()) {
        logger.error("Test failed: " + chainedRes.cause().getLocalizedMessage());
        context.fail(chainedRes.cause());
      } else {
        async.complete();
      }
    });
  }

  private Future<ApiTestHelper.WrappedResponse> doPost(TestContext context, JsonObject template, Handler<ApiTestHelper.WrappedResponse> handler) {
    return ApiTestHelper.doRequest(vertx, templateUrl, HttpMethod.POST, buildDefHeaders(), template.encode(),
      201, "POST template", handler);
  }

  private Future<ApiTestHelper.WrappedResponse> doGetById(TestContext context, String id, Handler<ApiTestHelper.WrappedResponse> handler) {
    return ApiTestHelper.doRequest(vertx, templateUrl + "/" + id, HttpMethod.GET, buildDefHeaders(), null,
      200, "GET template by id", handler);
  }

  private Future<ApiTestHelper.WrappedResponse> doGet(TestContext context, String query, Handler<ApiTestHelper.WrappedResponse> handler) {
    return ApiTestHelper.doRequest(vertx, templateUrl + "?query=" + query, HttpMethod.GET, buildDefHeaders(), null,
      200, "GET template with query", handler);
  }

  private Future<ApiTestHelper.WrappedResponse> doGetAll(TestContext context, Handler<ApiTestHelper.WrappedResponse> handler) {
    return ApiTestHelper.doRequest(vertx, templateUrl, HttpMethod.GET, buildDefHeaders(), null,
      200, "GET all templates", handler);
  }

  private Future<ApiTestHelper.WrappedResponse> doPutById(TestContext context, String id, JsonObject body, Handler<ApiTestHelper.WrappedResponse> handler) {
    return ApiTestHelper.doRequest(vertx, templateUrl + "/" + id, HttpMethod.PUT, buildDefHeaders(), body.encode(),
      200, "UPDATE template", handler);
  }

  private Future<ApiTestHelper.WrappedResponse> doDeleteById(TestContext context, String id, Handler<ApiTestHelper.WrappedResponse> handler) {
    CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
    return ApiTestHelper.doRequest(vertx, templateUrl + "/" + id, HttpMethod.DELETE, buildDefHeaders(), null,
      204, "DELETE template", handler);
  }

  private CaseInsensitiveHeaders buildDefHeaders() {
    CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
    headers.add("X-Okapi-Token", "dummytoken");
    headers.add("X-Okapi-Url", okapiUrl);
    return headers;
  }

}
