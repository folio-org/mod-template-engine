package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.vertx.core.json.JsonObject.mapFrom;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.tools.utils.NetworkUtils;
import org.hamcrest.CoreMatchers;
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

  private static final String LOCALHOST = "http://localhost";

  private static final String TEMPLATE_PATH = "/templates";
  private static final String TEMPLATE_REQUEST_PATH = "/template-request";
  private static final String CONFIG_REQUEST_PATH = "/configurations/entries";

  private static final String TXT_OUTPUT_FORMAT = "txt";
  private static final String HTML_OUTPUT_FORMAT = "html";
  private static final String EN_LANG = "en";

  private static final Logger logger = LogManager.getLogger("TemplateEngineTest");
  private static final int POST_TENANT_TIMEOUT = 10000;

  private static Vertx vertx;
  private static String moduleUrl;

  @org.junit.Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

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
    DeploymentOptions restVerticleDeploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class.getName(), restVerticleDeploymentOptions, context.asyncAssertSuccess(res -> {
      try {
        TenantAttributes t = new TenantAttributes().withModuleTo("mod-template-engine-1.0.0");
        tenantClient.postTenant(t, postResult -> {
          if (postResult.failed()) {
            Throwable cause = postResult.cause();
            logger.error(cause);
            context.fail(cause);
            return;
          }

          final HttpResponse<Buffer> postResponse = postResult.result();
          assertThat(postResponse.statusCode(), CoreMatchers.is(HttpStatus.SC_CREATED));

          String jobId = postResponse.bodyAsJson(TenantJob.class).getId();

          tenantClient.getTenantByOperationId(jobId, POST_TENANT_TIMEOUT, getResult -> {
            if (getResult.failed()) {
              Throwable cause = getResult.cause();
              logger.error(cause.getMessage());
              context.fail(cause);
              return;
            }

            final HttpResponse<Buffer> getResponse = getResult.result();
            assertThat(getResponse.statusCode(), CoreMatchers.is(HttpStatus.SC_OK));
            assertThat(getResponse.bodyAsJson(TenantJob.class).getComplete(), CoreMatchers.is(true));
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
    mockConfigModule();
    Postgres.truncate();
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
        .withTemplateId(UUID.randomUUID().toString())
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
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.size", is(expectedBody.length()))
      .body("meta.outputFormat", is(TXT_OUTPUT_FORMAT));
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
      .body("templateId", is(templateId))
      .body("result.header", Matchers.containsString(expectedHeaderSubstring))
      .body("result.body", Matchers.containsString(expectedBodySubstring))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void shouldLocalizeDatesAccordingToDefaultConfiguration() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Request created on {{request.creationDateTime}}")
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
              .put("creationDateTime", requestDate))
          .withAdditionalProperty("loan",
            new JsonObject()
              .put("dueDate", loanDueDate)));

    String expectedHeader = "Request created on 6/10/19, 5:32 PM";
    String expectedBody = "Due date is 6/18/19";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void shouldLocalizeDatesAccordingToConfigurationSetup() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Request created on {{request.creationDate}}")
              .withBody("Due date is {{loan.dueDateTime}}")));

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
              .put("dueDateTime", loanDueDate)));

    mockLocaleSettings("de-DE", "Europe/Berlin");

    String expectedHeader = "Request created on 10.06.19";
    String expectedBody = "Due date is 18.06.19, 16:04";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(TXT_OUTPUT_FORMAT));
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
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(
        new LocalizedTemplates()
          .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Template with dates")
              .withBody("Dates are:{{#dates}} {{testDateTime}};{{/dates}}")));

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
              .add(new JsonObject().put("testDateTime", firstDate))
              .add(new JsonObject().put("testDateTime", secondDate))
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
      .body("templateId", is(templateId))
      .body("result.body", is(expectedBody));
  }

  @Test
  public void shouldRemoveTimeFromDatesBasedOnToken() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates()
        .withAdditionalProperty(EN_LANG,
            new LocalizedTemplatesProperty()
              .withHeader("Request created on {{request.creationDateTime}}. Expiration date is {{request.requestExpirationDate}}")
              .withBody("Due date is {{loan.dueDate}}. Due time is {{loan.dueDateTime}}")));

    String templateId = postTemplate(template);

    String requestCreationDate = "2019-06-10T18:32:31.000+0100";
    String requestExpirationDate = "2019-06-15T18:32:31.000+0100";
    String loanDueDate = "2019-06-18T14:04:33.205Z";

    TemplateProcessingRequest templateRequest = new TemplateProcessingRequest()
      .withTemplateId(templateId)
      .withLang(EN_LANG)
      .withOutputFormat(TXT_OUTPUT_FORMAT)
      .withContext(new Context()
        .withAdditionalProperty("request", new JsonObject().put("creationDateTime", requestCreationDate)
          .put("requestExpirationDate", requestExpirationDate))
        .withAdditionalProperty("loan", new JsonObject().put("dueDate", loanDueDate)
          .put("dueDateTime", loanDueDate)));

    String expectedHeader = "Request created on 6/10/19, 5:32 PM. Expiration date is 6/15/19";
    String expectedBody = "Due date is 6/18/19. Due time is 6/18/19, 2:04 PM";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void dateRemainsUnchangedIfDateTokenContainsInvalidValue() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
          new LocalizedTemplatesProperty()
            .withHeader("Request created on {{request.creationDateTime}}. Expiration date is {{request.requestExpirationDate}}")
            .withBody("Due date is {{loan.dueDate}}. Due time is {{loan.dueDateTime}}")));

    String templateId = postTemplate(template);

    String invalidRequestCreationDate = "2019-06-10T18:32:31";
    String invalidRequestExpirationDate = "2019-06-15";
    String invalidLoanDueDate = "18.06.19";
    String validLoanDueDate = "2019-06-18T14:04:33.205Z";

    TemplateProcessingRequest templateRequest = new TemplateProcessingRequest()
      .withTemplateId(templateId)
      .withLang(EN_LANG)
      .withOutputFormat(TXT_OUTPUT_FORMAT)
      .withContext(new Context()
        .withAdditionalProperty("request", new JsonObject()
          .put("creationDateTime", invalidRequestCreationDate)
          .put("requestExpirationDate", invalidRequestExpirationDate))
        .withAdditionalProperty("loan", new JsonObject()
          .put("dueDate", invalidLoanDueDate)
          .put("dueDateTime", validLoanDueDate)));

    String expectedHeader = "Request created on 2019-06-10T18:32:31. Expiration date is 2019-06-15";
    String expectedBody = "Due date is 18.06.19. Due time is 6/18/19, 2:04 PM";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void shouldEnrichContextAndFormatDatesCorrectly() {
    Template template = new Template()
      .withDescription("Template with dates")
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates()
        .withAdditionalProperty(EN_LANG,
          new LocalizedTemplatesProperty()
            .withHeader("Request expiration date: {{request.requestExpirationDate}}. "
                + "Request expiration time: {{request.requestExpirationDateTime}}.")
            .withBody("Loan due date: {{loan.dueDate}}. Loan due time: {{loan.dueDateTime}}. "
                + "Loan checked in date: {{loan.checkedInDate}}. Loan checked in time: {{loan.checkedInDateTime}}")));

    String templateId = postTemplate(template);

    String requestDate = "2019-06-10T18:32:31.000Z";
    String loanDate1 = "2019-07-15T18:32:31.000Z";
    String loanDate2 = "2019-11-11T11:11:11.111Z";

    TemplateProcessingRequest templateRequest = new TemplateProcessingRequest()
      .withTemplateId(templateId)
      .withLang(EN_LANG)
      .withOutputFormat(TXT_OUTPUT_FORMAT)
      .withContext(new Context()
        .withAdditionalProperty("request", new JsonObject()
          .put("requestExpirationDate", requestDate))
        .withAdditionalProperty("loan", new JsonObject()
          .put("dueDate", loanDate1)
          .put("checkedInDate", loanDate1)
          .put("checkedInDateTime", loanDate2)));

    String expectedHeader = "Request expiration date: 6/10/19. Request expiration time: 6/10/19, 6:32 PM.";
    String expectedBody = "Loan due date: 7/15/19. Loan due time: 7/15/19, 6:32 PM. " +
        "Loan checked in date: 7/15/19. Loan checked in time: 11/11/19, 11:11 AM";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(TXT_OUTPUT_FORMAT));
  }

  @Test
  public void templateWithBarcodeImageProducesHtmlAndAttachment() {
    Template template = new Template()
      .withDescription("Template with barcodes")
      .withOutputFormats(Collections.singletonList(HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
        new LocalizedTemplatesProperty()
          .withHeader("Item barcode: {{item.barcode}}")
          .withBody("Item barcode image: {{item.barcodeImage}}")));

    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(HTML_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("item",
            new JsonObject()
              .put("barcode", "1234567890")));

    String expectedHeader = "Item barcode: 1234567890";
    String expectedBody = "Item barcode image: <img src='cid:barcode_1234567890' alt='barcode_1234567890'>";
    String expectedAttachmentName = "barcode_1234567890";
    String expectedAttachmentDisposition = "inline";
    String expectedAttachmentContentType = "image/png";
    String expectedAttachmentContentId = "<barcode_1234567890>";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(HTML_OUTPUT_FORMAT))
      .body("result.attachments.size()", is(1))
      .body("result.attachments[0].disposition", is(expectedAttachmentDisposition))
      .body("result.attachments[0].contentType", is(expectedAttachmentContentType))
      .body("result.attachments[0].name", is(expectedAttachmentName))
      .body("result.attachments[0].contentId", is(expectedAttachmentContentId))
      .body("result.attachments[0].data", Matchers.not((Matchers.isEmptyOrNullString())));
  }

  @Test
  public void noAttachmentsAreCreatedWhenImageTokenIsNotInTemplate() {
    Template template = new Template()
      .withDescription("Template with barcodes")
      .withOutputFormats(Collections.singletonList(HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
        new LocalizedTemplatesProperty()
          .withHeader("User name: {{user.name}}")
          .withBody("User barcode: {{user.barcode}}")));

    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(HTML_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("user",
            new JsonObject()
              // {{user.barcodeImage}} is not in the template, so no image attachments should be created
              .put("barcode", "1111111111")
              .put("name", "Tester")));

    String expectedHeader = "User name: Tester";
    String expectedBody = "User barcode: 1111111111";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(HTML_OUTPUT_FORMAT))
      .body("result.attachments.size()", is(0));
  }

  @Test
  public void duplicateImageTokensDoNotCreateDuplicateAttachments() {
    Template template = new Template()
      .withDescription("Template with barcodes")
      .withOutputFormats(Collections.singletonList(HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
        new LocalizedTemplatesProperty()
          .withHeader("User barcode: {{user.barcode}} " +
            "User barcode image: {{user.barcodeImage}}")
          .withBody("User barcode: {{user.barcode}} " +
            "User barcode image: {{user.barcodeImage}}")));

    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(HTML_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("user",
            new JsonObject()
              // {{user.barcodeImage}} is not in the template, so no token or image should be created
              .put("barcode", "1234567890")));

    String expectedHeader = "User barcode: 1234567890 " +
      "User barcode image: <img src='cid:barcode_1234567890' alt='barcode_1234567890'>";
    String expectedBody = "User barcode: 1234567890 " +
      "User barcode image: <img src='cid:barcode_1234567890' alt='barcode_1234567890'>";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(HTML_OUTPUT_FORMAT))
      .body("result.attachments.size()", is(1));
  }

  @Test
  public void attachmentsAreCreatedOnlyForBarcodeImageTokens() {
    Template template = new Template()
      .withDescription("Template with barcodes")
      .withOutputFormats(Collections.singletonList(HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
        new LocalizedTemplatesProperty()
          .withHeader("User barcode: {{user.barcode}}")
          .withBody("{{user.image}}" +
            "{{user.Image}}" +
            "{{user.barcodeimage}}" +
            "{{user.barCodeImage}}" +
            "{{user.faceImage}}" +
            "{{item.barcodeImage}}")));

    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(HTML_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("user",
            new JsonObject()
              // {{user.barcodeImage}} is not in the template, so no token or image should be created
              .put("barcode", "1234567890")));

    String expectedHeader = "User barcode: 1234567890";
    String expectedBody = "";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(HTML_OUTPUT_FORMAT))
      .body("result.attachments.size()", is(0));
  }

  @Test
  public void barcodeImagesAreCreatedForAllItemInArray() {
    Template template = new Template()
      .withDescription("Template with barcodes")
      .withOutputFormats(Collections.singletonList(HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
        new LocalizedTemplatesProperty()
          .withHeader("{{user.barcode}}{{user.barcodeImage}}")
          .withBody("{{#loans}}{{item.barcode}}{{item.barcodeImage}} {{/loans}}")));

    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(HTML_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("loans", new JsonArray()
            .add(new JsonObject()
              .put("item", new JsonObject()
                .put("barcode", "item1")))
            .add(new JsonObject()
              .put("item", new JsonObject()
                .put("barcode", "item2"))))
          .withAdditionalProperty("user", new JsonObject()
                .put("barcode", "user1")));

    String expectedHeader = "user1<img src='cid:barcode_user1' alt='barcode_user1'>";
    String expectedBody = "item1<img src='cid:barcode_item1' alt='barcode_item1'> " +
      "item2<img src='cid:barcode_item2' alt='barcode_item2'> ";

    String expectedCid1 = "<barcode_item1>";
    String expectedCid2 = "<barcode_item2>";
    String expectedCid3 = "<barcode_user1>";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(HTML_OUTPUT_FORMAT))
      .body("result.attachments.size()", is(3))
      .body("result.attachments[0].contentId", is(expectedCid1))
      .body("result.attachments[1].contentId", is(expectedCid2))
      .body("result.attachments[2].contentId", is(expectedCid3));
  }

  @Test
  public void duplicateItemsInArrayDoNotProduceDuplicateAttachments() {
    Template template = new Template()
      .withDescription("Template with barcodes")
      .withOutputFormats(Collections.singletonList(HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
        new LocalizedTemplatesProperty()
          .withHeader("{{user.barcode}}{{user.barcodeImage}}")
          .withBody("{{#loans}}{{item.barcode}}{{item.barcodeImage}} {{/loans}}")));

    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(HTML_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("loans", new JsonArray()
            .add(new JsonObject()
              .put("item", new JsonObject()
                .put("barcode", "item1")))
            .add(new JsonObject()
              .put("item", new JsonObject()
                .put("barcode", "item1"))))
          .withAdditionalProperty("user", new JsonObject()
            .put("barcode", "user1")));

    String expectedHeader = "user1<img src='cid:barcode_user1' alt='barcode_user1'>";
    String expectedBody = "item1<img src='cid:barcode_item1' alt='barcode_item1'> " +
      "item1<img src='cid:barcode_item1' alt='barcode_item1'> ";

    String expectedItemCid = "<barcode_item1>";
    String expectedUserCid = "<barcode_user1>";

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("result.header", is(expectedHeader))
      .body("result.body", is(expectedBody))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(HTML_OUTPUT_FORMAT))
      .body("result.attachments.size()", is(2))
      .body("result.attachments[0].contentId", is(expectedItemCid))
      .body("result.attachments[1].contentId", is(expectedUserCid));
  }

  @Test
  public void dateTokensWithNonStringValuesDoNotBreakProcessing() {
    Template template = new Template()
      .withDescription("Template with barcodes")
      .withOutputFormats(Collections.singletonList(HTML_OUTPUT_FORMAT))
      .withTemplateResolver("mustache")
      .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty(EN_LANG,
        new LocalizedTemplatesProperty()
          .withHeader("Reset your Folio account")
          .withBody("Hi {{user.username}}!")));

    String templateId = postTemplate(template);

    TemplateProcessingRequest templateRequest =
      new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang(EN_LANG)
        .withOutputFormat(HTML_OUTPUT_FORMAT)
        .withContext(new Context()
          .withAdditionalProperty("user", new JsonObject()
            .put("username", "Reader")
            .put("createdDate", 1576581085686L)
            .put("updatedDate", 1576581085686L)
            .put("metadata", new JsonObject()
              .put("createdDate", 1576243494376L)
              .put("updatedDate", 1576581085675L))));

    RestAssured.given()
      .spec(spec)
      .body(toJson(templateRequest))
      .when()
      .post(TEMPLATE_REQUEST_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("templateId", is(templateId))
      .body("meta.lang", is(EN_LANG))
      .body("meta.outputFormat", is(HTML_OUTPUT_FORMAT));
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
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
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
      .withOutputFormats(Arrays.asList(TXT_OUTPUT_FORMAT, HTML_OUTPUT_FORMAT))
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
