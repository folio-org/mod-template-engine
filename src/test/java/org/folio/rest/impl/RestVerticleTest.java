package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

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
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.*;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.WireMockServer;

@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {

  private static final String TEMPLATES_TABLE_NAME = "template";

  private static int mockServerPort;
  private static Vertx vertx;
  private static final Logger logger = LoggerFactory.getLogger("TemplateEngineTest");
  private static String templateUrl;
  private static String okapiUrl;
  private static WireMockServer wireMockServer;

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

  @BeforeClass
  public static void setUpClass(final TestContext context) throws Exception {
    mockServerPort = NetworkUtils.nextFreePort();
    int okapiPort = NetworkUtils.nextFreePort();

    wireMockServer = new WireMockServer(mockServerPort);
    wireMockServer.start();
    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .willReturn(okJson(new JsonObject()
          .put("patronNoticePolicies", new JsonArray())
          .put("totalRecords", 0).encode())));

    Async async = context.async();
    okapiUrl = "http://localhost:" + okapiPort;
    templateUrl = okapiUrl + "/templates";
    TenantClient tenantClient = new TenantClient(okapiUrl, "diku", null);
    vertx = Vertx.vertx();

    PostgresClient.getInstance(vertx).startEmbeddedPostgres();

    DeploymentOptions options = new DeploymentOptions().setConfig(
      new JsonObject()
        .put("http.port", okapiPort)
    );

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      try {
        TenantAttributes t = new TenantAttributes().withModuleTo("mod-template-engine-1.0.0");
        tenantClient.postTenant(t, res2 -> {
          async.complete();
        });
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    });
  }

  @Before
  public void setUp(TestContext context) {
    clearTemplatesTable(context);
  }

  private void clearTemplatesTable(TestContext context) {
    PostgresClient.getInstance(vertx, "diku").delete(TEMPLATES_TABLE_NAME, new Criterion(), event -> {
      if (event.failed()) {
        context.fail(event.cause());
      }
    });
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopEmbeddedPostgres();
      wireMockServer.stop();
      async.complete();
    }));
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
    headers.add("X-Okapi-Token", "dummytoken");
    headers.add("X-Okapi-Url", "http://localhost:" + mockServerPort);
    return ApiTestHelper.doRequest(vertx, templateUrl + "/" + id, HttpMethod.DELETE, headers, null,
      204, "DELETE template", handler);
  }

  private CaseInsensitiveHeaders buildDefHeaders() {
    CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
    headers.add("X-Okapi-Url", okapiUrl);
    return headers;
  }

}
