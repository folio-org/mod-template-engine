package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.vertx.core.json.JsonObject.mapFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.folio.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.TemplatePreviewRequest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class TemplatePreviewIT {

  private static final String OKAPI_HEADER_URL = "x-okapi-url";
  private static final String LOCALHOST = "http://localhost";
  private static final String PREVIEW_PATH = "/template-request/preview";
  private static final String LOCALE_REQUEST_PATH = "/locale";

  private static final Logger logger = LogManager.getLogger("TemplatePreviewIT");
  private static final int POST_TENANT_TIMEOUT = 10000;

  private static Vertx vertx;
  private static String moduleUrl;

  @org.junit.Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(false)));

  private RequestSpecification spec;

  @BeforeClass
  public static void setUpClass(final TestContext context) {
    Async async = context.async();
    vertx = Vertx.vertx();
    int port = NetworkUtils.nextFreePort();
    moduleUrl = LOCALHOST + ':' + port;

    Postgres.init();
    Postgres.dropSchema();

    TenantClient tenantClient = new TenantClient(moduleUrl, Postgres.getTenant(), null);
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class.getName(), options)
      .onComplete(context.asyncAssertSuccess(res -> {
        try {
          TenantAttributes t = new TenantAttributes().withModuleTo("mod-template-engine-1.0.0");
          tenantClient.postTenant(t, postResult -> {
            if (postResult.failed()) {
              context.fail(postResult.cause());
              return;
            }
            final HttpResponse<Buffer> postResponse = postResult.result();
            assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));
            String jobId = postResponse.bodyAsJson(TenantJob.class).getId();
            tenantClient.getTenantByOperationId(jobId, POST_TENANT_TIMEOUT, getResult -> {
              if (getResult.failed()) {
                context.fail(getResult.cause());
                return;
              }
              final HttpResponse<Buffer> getResponse = getResult.result();
              assertThat(getResponse.statusCode(), is(HttpStatus.SC_OK));
              assertThat(getResponse.bodyAsJson(TenantJob.class).getComplete(), is(true));
              async.complete();
            });
          });
        } catch (Exception e) {
          context.fail(e);
        }
      }));
  }

  @Before
  public void setUp() {
    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(moduleUrl)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, Postgres.getTenant())
      .addHeader(RestVerticle.OKAPI_HEADER_TOKEN, Postgres.getTenant())
      .addHeader(OKAPI_HEADER_URL, LOCALHOST + ':' + mockServer.port())
      .addHeader(RestVerticle.OKAPI_REQUESTID_HEADER, "requestId")
      .build();
    stubFor(get(urlPathEqualTo(LOCALE_REQUEST_PATH))
      .willReturn(okJson(new JsonObject().toString())));
  }

  @Test
  public void rendersInlineTemplateWithoutStoredTemplate() {
    TemplatePreviewRequest req = new TemplatePreviewRequest()
      .withHeader("Hello message for {{user.name}}")
      .withBody("Hello {{user.name}}")
      .withContext(new Context()
        .withAdditionalProperty("user", new JsonObject().put("name", "Username")));

    RestAssured.given()
      .spec(spec)
      .body(toJson(req))
      .when()
      .post(PREVIEW_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("header", is("Hello message for Username"))
      .body("body", is("Hello Username"));
  }

  @Test
  public void formatsDateTokensAccordingToTenantLocale() {
    stubFor(get(urlPathEqualTo(LOCALE_REQUEST_PATH))
      .willReturn(okJson(new JsonObject()
        .put("locale", "de-DE")
        .put("timezone", "Europe/Berlin")
        .toString())));

    TemplatePreviewRequest req = new TemplatePreviewRequest()
      .withHeader("Request created on {{request.creationDate}}")
      .withBody("Due date is {{loan.dueDateTime}}")
      .withContext(new Context()
        .withAdditionalProperty("request", new JsonObject()
          .put("creationDate", "2019-06-10T18:32:31.000+0100"))
        .withAdditionalProperty("loan", new JsonObject()
          .put("dueDateTime", "2019-06-18T14:04:33.205Z")));

    RestAssured.given()
      .spec(spec)
      .body(toJson(req))
      .when()
      .post(PREVIEW_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("header", is("Request created on 10.06.19"))
      .body("body", is("Due date is 18.06.19, 16:04"));
  }

  @Test
  public void omittedHeaderAndBodyYield200WithEmptyStrings() {
    TemplatePreviewRequest req = new TemplatePreviewRequest();

    RestAssured.given()
      .spec(spec)
      .body(toJson(req))
      .when()
      .post(PREVIEW_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("header", is(""))
      .body("body", is(""));
  }

  @Test
  public void malformedTemplateReturns400() {
    TemplatePreviewRequest req = new TemplatePreviewRequest()
      .withHeader("Hello")
      .withBody("{{#unclosed}}");

    RestAssured.given()
      .spec(spec)
      .body(toJson(req))
      .when()
      .post(PREVIEW_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void localeServiceDownStillReturns200WithDefaults() {
    stubFor(get(urlPathEqualTo(LOCALE_REQUEST_PATH))
      .willReturn(serverError()));

    TemplatePreviewRequest req = new TemplatePreviewRequest()
      .withHeader("Hello {{user.name}}")
      .withBody("Body {{user.name}}")
      .withContext(new Context()
        .withAdditionalProperty("user", new JsonObject().put("name", "Alex")));

    RestAssured.given()
      .spec(spec)
      .body(toJson(req))
      .when()
      .post(PREVIEW_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("header", is("Hello Alex"))
      .body("body", is("Body Alex"));
  }

  private String toJson(Object object) {
    return mapFrom(object).toString();
  }
}
