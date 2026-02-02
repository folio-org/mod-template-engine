package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TemplateDeletionTest {

  public static final String EXPECTED_CQL_QUERY =
      "loanNotices == \"*\\\"templateId\\\": \\\"%1$s\\\"*\" " +
          "OR requestNotices == \"*\\\"templateId\\\": \\\"%1$s\\\"*\" " +
          "OR feeFineNotices == \"*\\\"templateId\\\": \\\"%1$s\\\"*\"";
  private static final Logger logger = LogManager.getLogger(TemplateDeletionTest.class);
  private static JsonObject unusedTemplate = new JsonObject()
    .put("id", "c9c7d02f-873a-4608-98e7-5e2df69a4a4f")
    .put("templateResolver", "mustache")
    .put("localizedTemplates", new JsonObject())
    .put("outputFormats", new JsonArray());

  private static JsonObject inUseTemplate = new JsonObject()
    .put("id", "3c982ef3-c1e6-45a8-a27d-d0e7355561fd")
    .put("templateResolver", "mustache")
    .put("localizedTemplates", new JsonObject())
    .put("outputFormats", new JsonArray());

  private static WireMockServer wireMockServer;
  private static RequestSpecification spec;
  private static String okapiUrl;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {

    int okapiPort = NetworkUtils.nextFreePort();
    int mockServerPort = NetworkUtils.nextFreePort();
    okapiUrl = "http://localhost:" + okapiPort;

    wireMockServer = new WireMockServer(mockServerPort);
    wireMockServer.start();

    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(okapiUrl)
      .addHeader(OKAPI_HEADER_TENANT, Postgres.getTenant())
      .addHeader("X-Okapi-Token", "test_token")
      .addHeader("X-Okapi-Url", "http://localhost:" + mockServerPort)
      .addHeader("X-Okapi-Request-Id", "requestId")
      .build();

    Postgres.init();
    Postgres.dropSchema();
    TenantClient tenantClient = new TenantClient(okapiUrl, Postgres.getTenant(), null);

    vertx.deployVerticle(RestVerticle::new,
      new DeploymentOptions().setConfig(new JsonObject().put("http.port", okapiPort)))
      .onComplete(deploy -> {
        try {
          if (deploy.failed()) {
            context.failNow(deploy.cause());
          }
          TenantAttributes t = new TenantAttributes().withModuleTo("mod-template-engine-1.0.0");
          tenantClient.postTenant(t, post -> {
            assertThat(post.result().statusCode(), is(201));
            context.completeNow();
          });
        } catch (Exception e) {
          logger.error(e.getMessage());
          context.failNow(e);
        }
      });
  }

  @AfterEach
  void tearDown() {
    Postgres.truncate();
    wireMockServer.resetAll();
  }

  @AfterAll
  static void afterAll() {
    wireMockServer.stop();
  }

  @Test
  void canDeleteTemplate() {
    var expectedQuery = format(EXPECTED_CQL_QUERY, unusedTemplate.getString("id"));
    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .withQueryParam("query", equalTo(expectedQuery))
        .withQueryParam("limit", equalTo("0"))
        .willReturn(okJson(new JsonObject()
          .put("patronNoticePolicies", new JsonArray())
          .put("totalRecords", 0).encode()))
    );

    given()
      .spec(spec)
      .body(unusedTemplate.encode())
      .when()
      .post("/templates");

    given()
      .spec(spec)
      .when()
      .delete("/templates/" + unusedTemplate.getString("id"))
      .then()
      .statusCode(204);
  }

  @Test
  void canDeleteTemplateWhenRouteIsNotFound() {
    var expectedQuery = format(EXPECTED_CQL_QUERY, inUseTemplate.getString("id"));
    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .withQueryParam("query", equalTo(expectedQuery))
        .withQueryParam("limit", equalTo("0"))
        .willReturn(jsonResponse(new JsonObject()
            .put("message", "no Route matched with those values")
            .put("request_id", "4e37864c9da5970e3dfddf604a0e172a").encode(),
          404))
    );

    given()
      .spec(spec)
      .body(inUseTemplate.encode())
      .when()
      .post("/templates");

    given()
      .spec(spec)
      .when()
      .delete("/templates/" + inUseTemplate.getString("id"))
      .then()
      .statusCode(204);
  }

  @Test
  void canDeleteTemplateWhenApplicationIsNotEnabled() {
    var expectedQuery = format(EXPECTED_CQL_QUERY, inUseTemplate.getString("id"));
    var errorMessage = "Application is not enabled for tenant: " + Postgres.getTenant();
    wireMockServer.stubFor(
        get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
            .withQueryParam("query", equalTo(expectedQuery))
            .withQueryParam("limit", equalTo("0"))
            .willReturn(jsonResponse(new JsonObject()
                    .put("total_records", 1)
                    .put("errors", new JsonArray().add(new JsonObject()
                        .put("type", "TenantNotEnabledException")
                        .put("code", "tenant_not_enabled")
                        .put("message", errorMessage)
                    ))
                    .encode(),
                400))
    );

    given()
        .spec(spec)
        .body(inUseTemplate.encode())
        .when()
        .post("/templates");

    given()
        .spec(spec)
        .when()
        .delete("/templates/" + inUseTemplate.getString("id"))
        .then()
        .statusCode(204);
  }

  @Test
  void cannotDeleteTemplateWhenInternalServerError() {
    var expectedQuery = format(EXPECTED_CQL_QUERY, unusedTemplate.getString("id"));
    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .withQueryParam("query", equalTo(expectedQuery))
        .withQueryParam("limit", equalTo("0"))
        .willReturn(jsonResponse(new JsonObject()
            .put("message", "Internal Server Error").encode(), 500))
    );

    given()
      .spec(spec)
      .body(unusedTemplate.encode())
      .when()
      .post("/templates");

    given()
      .spec(spec)
      .when()
      .delete("/templates/" + unusedTemplate.getString("id"))
      .then()
      .statusCode(500);
  }

  @Test
  void cannotDeleteWhenTemplateInUse() {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .withQueryParam("query", equalTo(format(EXPECTED_CQL_QUERY, inUseTemplate.getString("id"))))
        .withQueryParam("limit", equalTo("0"))
        .willReturn(okJson(new JsonObject()
          .put("patronNoticePolicies", new JsonArray())
          .put("totalRecords", 1).encode()))
    );

    given()
      .spec(spec)
      .body(inUseTemplate.encode())
      .when()
      .post("/templates");

    given()
      .spec(spec)
      .when()
      .delete("/templates/" + inUseTemplate.getString("id"))
      .then()
      .statusCode(400);
  }

  @Test
  void cannotDeleteWhenTemplateInUseWithInvalidResponseBody() {
    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .withQueryParam("query", equalTo(format(EXPECTED_CQL_QUERY, inUseTemplate.getString("id"))))
        .withQueryParam("limit", equalTo("0"))
        .willReturn(okJson(new JsonObject().put("test_key", "test_value").encode()))
    );

    given()
      .spec(spec)
      .body(inUseTemplate.encode())
      .when()
      .post("/templates");

    given()
      .spec(spec)
      .when()
      .delete("/templates/" + inUseTemplate.getString("id"))
      .then()
      .statusCode(500);
  }
}
