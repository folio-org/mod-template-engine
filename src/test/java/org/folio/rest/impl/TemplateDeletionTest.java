package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;

import java.io.IOException;

import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterAll;
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

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) throws IOException {

    int okapiPort = NetworkUtils.nextFreePort();
    int mockServerPort = NetworkUtils.nextFreePort();

    wireMockServer = new WireMockServer(mockServerPort);
    wireMockServer.start();
    setupStub();

    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri("http://localhost:" + okapiPort)
      .addHeader(OKAPI_HEADER_TENANT, "test_tenant")
      .addHeader(OKAPI_HEADER_TOKEN, "dummy-token")
      .addHeader("X-Okapi-Url", "http://localhost:" + mockServerPort)
      .build();

    PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    TenantClient tenantClient = new TenantClient("http://localhost:" + okapiPort, "test_tenant", "dummy-token", false);

    vertx.deployVerticle(RestVerticle::new,
      new DeploymentOptions().setConfig(new JsonObject().put("http.port", okapiPort)), deploy -> {
      try {
        tenantClient.postTenant(null, post -> context.completeNow());
      } catch (Exception e) {
        context.failNow(e);
      }
    });
  }

  @AfterAll
  static void afterAll() {
    wireMockServer.stop();
    PostgresClient.stopEmbeddedPostgres();
  }

  @Test
  void canDeleteTemplate() {
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
  void cannotDeleteWhenTemplateInUse() {
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

  private static void setupStub() {

    String query = "loanNotices=/@templateId %1$s " +
      "OR requestNotices=/@templateId %1$s " +
      "OR feeFineNotices=/@templateId %1$s";

    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .withQueryParam("query", equalTo(format(query, inUseTemplate.getString("id"))))
        .withQueryParam("limit", equalTo("0"))
        .willReturn(okJson(new JsonObject()
          .put("patronNoticePolicies", new JsonArray())
          .put("totalRecords", 1).encode()))
    );

    wireMockServer.stubFor(
      get(urlPathEqualTo("/patron-notice-policy-storage/patron-notice-policies"))
        .withQueryParam("query", equalTo(format(query, unusedTemplate.getString("id"))))
        .withQueryParam("limit", equalTo("0"))
        .willReturn(okJson(new JsonObject()
          .put("patronNoticePolicies", new JsonArray())
          .put("totalRecords", 0).encode()))
    );

  }
}
