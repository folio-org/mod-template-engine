package org.folio.migration;

import static org.folio.rest.impl.Postgres.getTenant;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.impl.Postgres;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

@RunWith(VertxUnitRunner.class)
public class TemplatesMigrationTest {
  private static final Logger LOG = LogManager.getLogger(TemplatesMigrationTest.class);

  private static final int POST_TENANT_TIMEOUT = 10000;
  private static final int MODULE_PORT = NetworkUtils.nextFreePort();
  private static final int OKAPI_PORT = NetworkUtils.nextFreePort();
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String MODULE_URL = "http://localhost:" + MODULE_PORT;
  private static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  private static final String TEMPLATES_URL = MODULE_URL + "/templates";
  private static final String CATEGORY_KEY = "category";

  private static final Vertx vertx = Vertx.vertx();
  private static final WebClient webClient = WebClient.create(vertx);
  private static final TenantClient tenantClient = new TenantClient(MODULE_URL, getTenant(), null, webClient);
  private RequestSpecification requestSpecification;

  @BeforeClass
  public static void setUpClass(TestContext context) {
    Postgres.init();
    Async async = context.async();

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MODULE_PORT));

    vertx.deployVerticle(RestVerticle.class.getName(), options,
      context.asyncAssertSuccess(res -> postTenant(context)
        .onSuccess(r -> async.complete())));
  }

  @Before
  public void setUp() {
    requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(MODULE_URL)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, Postgres.getTenant())
      .addHeader(RestVerticle.OKAPI_HEADER_TOKEN, Postgres.getTenant())
      .addHeader(OKAPI_URL_HEADER, OKAPI_URL)
      .build();
  }

  @Test
  public void automatedFeeFineCategoryShouldBeChangedToAutomatedFeeFineCharge(TestContext context) {
    String templateIdWithSupportedCategory = UUID.randomUUID().toString();
    String templateIdWithUnsupportedCategory = UUID.randomUUID().toString();

    JsonObject templateWithSupportedCategory = new JsonObject()
      .put("id", templateIdWithSupportedCategory)
      .put("templateResolver", "mustache")
      .put("outputFormats", new JsonArray().add("text/plain"))
      .put(CATEGORY_KEY, "Loan")
      .put("localizedTemplates", new JsonObject()
        .put("en", new JsonObject()
          .put("header", "Template header")
          .put("body", "Template body")));

    JsonObject templateWithUnsupportedCategory = templateWithSupportedCategory
      .copy()
      .put("id", templateIdWithUnsupportedCategory)
      .put(CATEGORY_KEY, "AutomatedFeeFine");

    postTemplate(templateWithSupportedCategory);
    postTemplate(templateWithUnsupportedCategory);

    postTenant(context)
      .onSuccess(r -> {
        // supported categories should remain intact
        getTemplate(templateIdWithSupportedCategory)
          .body(CATEGORY_KEY, is(templateWithSupportedCategory.getString(CATEGORY_KEY)));

        getTemplate(templateIdWithUnsupportedCategory)
          .body(CATEGORY_KEY, is("AutomatedFeeFineCharge"));
      });
  }

  private static Future<Void> postTenant(TestContext context) {
    Promise<Void> promise = Promise.promise();

    try {
      TenantAttributes tenantAttributes = new TenantAttributes()
        .withModuleTo("mod-template-engine-1.0.0");

      tenantClient.postTenant(tenantAttributes, postResult -> {
        if (postResult.failed()) {
          LOG.error(postResult.cause());
          context.fail(postResult.cause());
          return;
        }

        final HttpResponse<Buffer> postResponse = postResult.result();
        assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));

        String jobId = postResponse.bodyAsJson(TenantJob.class).getId();

        tenantClient.getTenantByOperationId(jobId, POST_TENANT_TIMEOUT, getResult -> {
          if (getResult.failed()) {
            LOG.error(getResult.cause());
            context.fail(getResult.cause());
            return;
          }

          final HttpResponse<Buffer> getResponse = getResult.result();
          assertThat(getResponse.statusCode(), is(HttpStatus.SC_OK));
          assertThat(getResponse.bodyAsJson(TenantJob.class).getComplete(), is(true));
          promise.complete();
        });
      });
    } catch (Exception e) {
      LOG.error(e);
      context.fail(e);
    }

    return promise.future();
  }

  private void postTemplate(JsonObject template) {
    RestAssured.given()
      .spec(requestSpecification)
      .body(template.encodePrettily())
      .when()
      .post(TEMPLATES_URL)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  private ValidatableResponse getTemplate(String templateId) {
    return RestAssured.given()
      .spec(requestSpecification)
      .when()
      .get(TEMPLATES_URL + "/" + templateId)
      .then()
      .statusCode(HttpStatus.SC_OK);
  }
}
