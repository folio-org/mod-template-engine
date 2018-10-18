package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.LocalizedTemplates;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(VertxUnitRunner.class)
public class TemplateRequestTest {

  private static final String TENANT = "diku";
  private static final String TEMPLATE_PATH = "/templates";
  private static final String TEMPLATE_REQUEST_PATH = "/template-request";

  private static final String TEMPLATES_TABLE_NAME = "template";

  public static final String TXT_OUTPUT_FORMAT = "txt";
  public static final String EN_LANG = "en";

  private static Vertx vertx;
  private static int port;
  private static RequestSpecification spec;

  @BeforeClass
  public static void setUpClass(final TestContext context) throws Exception {
    Async async = context.async();
    vertx = Vertx.vertx();
    port = NetworkUtils.nextFreePort();

    String useExternalDatabase = System.getProperty(
      "org.folio.password.validator.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment":
        System.out.println("Using environment settings");
        break;
      case "external":
        String postgresConfigPath = System.getProperty(
          "org.folio.password.validator.test.config",
          "/postgres-conf-local.json");
        PostgresClient.setConfigFilePath(postgresConfigPath);
        break;
      case "embedded":
        PostgresClient.setIsEmbedded(true);
        PostgresClient.getInstance(vertx).startEmbeddedPostgres();
        break;
      default:
        String message = "No understood database choice made." +
          "Please set org.folio.password.validator.test.database" +
          "to 'external', 'environment' or 'embedded'";
        throw new Exception(message);
    }

    TenantClient tenantClient = new TenantClient("localhost", port, "diku", "dummy-token");
    DeploymentOptions restVerticleDeploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class.getName(), restVerticleDeploymentOptions, res -> {
      try {
        tenantClient.postTenant(null, res2 -> {
          async.complete();
        });
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri("http://localhost:" + port)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, TENANT)
      .build();
  }

  @Before
  public void setUp(TestContext context) {
    clearTemplatesTable(context);
  }

  private void clearTemplatesTable(TestContext context) {
    PostgresClient.getInstance(vertx, TENANT).delete(TEMPLATES_TABLE_NAME, new Criterion(), event -> {
      if (event.failed()) {
        context.fail(event.cause());
      }
    });
  }

  @Test
  public void shouldReturnUnprocessableEntityWhenEmptyJson() {
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnBadRequestWhenSpecifiedTemplateNotFound() {
    Template template = createTemplate();
    postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId("not-existing-id")
        .withLang(EN_LANG)
        .withOutputFormat(TXT_OUTPUT_FORMAT);

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestDidNotMatchOutputFormats() {
    Template template = createTemplate();
    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat("not-a-format");

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenLangIsNotSupported() {
    Template template = createTemplate();
    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang("not-a-language")
        .withOutputFormat(TXT_OUTPUT_FORMAT);

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldRetunExpectedResponseWhenRequestIsValid() {
    Template template = createTemplate();
    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(TXT_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("user",
            new JsonObject()
              .put("name", "Username")));

    String expectedHeader = "Hello message for Username";
    String expectedBody = "Hello Username";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", Matchers.is(templateId))
      .body("result.header", Matchers.is(expectedHeader))
      .body("result.body", Matchers.is(expectedBody))
      .body("meta.lang", Matchers.is(EN_LANG))
      .body("meta.size", Matchers.is(expectedBody.length()))
      .body("meta.outputFormat", Matchers.is(TXT_OUTPUT_FORMAT));
  }

  private String postTemplate(Template template) {
    return RestAssured.given()
      .spec(spec)
      .body(toJson(template))
      .when()
      .post(TEMPLATE_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract()
      .body().jsonPath().get("id");
  }

  private String toJson(Object object) {
    return JsonObject.mapFrom(object).toString();
  }

  private Template createTemplate() {
    return new Template()
      .withDescription("Template for password change")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, "html"))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Hello message for {{user.name}}")
              .withBody("Hello {{user.name}}"))
          .withAdditionalProperty("de",
            new LocalizedTemplatesProperty()
              .withHeader("Hallo message for {{user.name}}")
              .withBody("Hallo {{user.name}}")));
  }
}
