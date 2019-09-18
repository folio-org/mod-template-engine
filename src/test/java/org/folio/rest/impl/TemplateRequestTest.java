package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.vertx.core.json.JsonObject.mapFrom;

import java.util.Arrays;
import java.util.Collections;

import org.apache.http.HttpStatus;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
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

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class TemplateRequestTest {

  private static final String OKAPI_HEADER_URL = "x-okapi-url";

  private static final String TENANT = "diku";
  private static final String DUMMY_TOKEN = "dummy-token";
  private static final String LOCALHOST = "http://localhost";

  private static final String TEMPLATE_PATH = "/templates";
  private static final String TEMPLATE_REQUEST_PATH = "/template-request";
  private static final String CONFIG_REQUEST_PATH = "/configurations/entries";


  private static final String TEMPLATES_TABLE_NAME = "template";

  public static final String TXT_OUTPUT_FORMAT = "txt";
  public static final String EN_LANG = "en";

  private static Vertx vertx;
  private static String moduleUrl;

  @org.junit.Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

  private RequestSpecification spec;

  @BeforeClass
  public static void setUpClass(final TestContext context) throws Exception {
    Async async = context.async();
    vertx = Vertx.vertx();
    int port = NetworkUtils.nextFreePort();
    moduleUrl = LOCALHOST + ':' + port;

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

    TenantClient tenantClient = new TenantClient(LOCALHOST + ':' + port, TENANT, DUMMY_TOKEN);
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
  }

  @Before
  public void setUp(TestContext context) {
    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(moduleUrl)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, TENANT)
      .addHeader(RestVerticle.OKAPI_HEADER_TOKEN, DUMMY_TOKEN)
      .addHeader(OKAPI_HEADER_URL, LOCALHOST + ':' + mockServer.port())
      .build();
    mockConfigModule();
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
  public void shouldReturnExpectedResponseWhenRequestIsValid() {
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

  @Test
  public void shouldReturnOkStatusWhenContextIsNotSpecified() {
    Template template = createTemplate();
    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(TXT_OUTPUT_FORMAT);

    String expectedHeaderSubstring = "Hello message for";
    String expectedBodySubstring = "Hello";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", Matchers.is(templateId))
      .body("result.header", Matchers.containsString(expectedHeaderSubstring))
      .body("result.body", Matchers.containsString(expectedBodySubstring))
      .body("meta.lang", Matchers.is(EN_LANG))
      .body("meta.outputFormat", Matchers.is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void shouldLocalizeDatesAccordingToDefaultConfiguration() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, "html"))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Request created on {{request.creationDate}}")
              .withBody("Due date is {{loan.dueDate}}")));

    String templateId = postTemplate(template);

    String requestDate = "2019-06-10T18:32:31.000+0100";
    String loanDueDate = "2019-06-18T14:04:33.205Z";

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(TXT_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("request",
            new JsonObject()
              .put("creationDate", requestDate))
          .withAdditionalProperty("loan",
            new JsonObject()
              .put("dueDate", loanDueDate)));

    String expectedHeader = "Request created on 6/10/19, 5:32 PM";
    String expectedBody = "Due date is 6/18/19, 2:04 PM";

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
      .body("meta.outputFormat", Matchers.is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void shouldLocalizeDatesAccordingToConfigurationSetup() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, "html"))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Request created on {{request.creationDate}}")
              .withBody("Due date is {{loan.dueDate}}")));

    String templateId = postTemplate(template);

    String requestDate = "2019-06-10T18:32:31.000+0100";
    String loanDueDate = "2019-06-18T14:04:33.205Z";

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(TXT_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("request",
            new JsonObject()
              .put("creationDate", requestDate))
          .withAdditionalProperty("loan",
            new JsonObject()
              .put("dueDate", loanDueDate)));

    mockLocaleSettings("de-DE", "Europe/Berlin");

    String expectedHeader = "Request created on 10.06.19, 19:32";
    String expectedBody = "Due date is 18.06.19, 16:04";

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
      .body("meta.outputFormat", Matchers.is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void canProcessTemplateWithArrayInContext() {
    String templateId = postTemplate(createTemplateWithMultipleItems());

    JsonObject object1 = new JsonObject()
      .put("item", new JsonObject()
        .put("title", "Age of TV heroes"))
      .put("loan", new JsonObject()
        .put("dueDate", "07/30/2019 10:00")
        .put("numberOfRenewalsRemaining", "3"));

    JsonObject object2 = new JsonObject()
      .put("item", new JsonObject()
        .put("title", "Interesting Times"))
      .put("loan", new JsonObject()
        .put("dueDate", "08/30/2019 23:59")
        .put("numberOfRenewalsRemaining", "unlimited"));

    Context context = new Context()
      .withAdditionalProperty("user", new JsonObject().put("name", "Username"))
      .withAdditionalProperty("loans", new JsonArray()
        .add(object1)
        .add(object2));

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(TXT_OUTPUT_FORMAT)
        .withContext(context);

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK);
  }

  @Test
  public void shouldLocalizeDatesInContextWithArray() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, "html"))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Template with dates")
              .withBody("Dates are:{{#dates}} {{dateValue}};{{/dates}}")));

    String templateId = postTemplate(template);

    String firstDate = "2019-06-10T18:32:31.000+0100";
    String secondDate = "2019-06-18T14:04:33.205Z";

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(TXT_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("dates",
            new JsonArray()
              .add(new JsonObject().put("dateValue", firstDate))
              .add(new JsonObject().put("dateValue", secondDate))
          ));

    mockLocaleSettings("de-DE", "Europe/Berlin");

    String expectedBody = "Dates are: 10.06.19, 19:32; 18.06.19, 16:04;";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", Matchers.is(templateId))
      .body("result.body", Matchers.is(expectedBody));
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
    return mapFrom(object).toString();
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

  private Template createTemplateWithMultipleItems() {
    return new Template()
      .withDescription("Check out template")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, "html"))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Check out message for {{user.name}}")
              .withBody("You have checked out the following item(s):" +
                "{{#loans}}" +
                "Title: {{item.title}}, Due date: {{loan.dueDate}}, Renewal remaining {{loan.numberOfRenewalsRemaining}};" +
                "{{/loans}}")));
  }

  private void mockConfigModule() {
    Configurations configurations =
      new Configurations().withConfigs(Collections.emptyList()).withTotalRecords(0);
    stubFor(get(urlPathEqualTo(CONFIG_REQUEST_PATH))
      .willReturn(okJson(toJson(mapFrom(configurations)))));
  }

  private void mockLocaleSettings(String languageToken, String timezoneId) {
    String localeConfigValue = new JsonObject()
      .put("locale", languageToken)
      .put("timezone", timezoneId).encode();

    Config config = new Config().withValue(localeConfigValue);
    Configurations configurations =
      new Configurations().withConfigs(Collections.singletonList(config)).withTotalRecords(1);

    stubFor(get(urlPathEqualTo(CONFIG_REQUEST_PATH))
      .willReturn(okJson(toJson(configurations))));
  }
}
