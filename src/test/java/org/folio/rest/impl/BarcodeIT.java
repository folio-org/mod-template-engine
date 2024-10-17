package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.LocalizedTemplates;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.jaxrs.model.Template;
import org.folio.rest.jaxrs.model.TemplateProcessingRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class BarcodeIT {
  private static final boolean IS_LOG_ENABLED = false;
  private static final Logger log = LoggerFactory.getLogger(BarcodeIT.class);
  private static final Network network = Network.newNetwork();

  private static final DockerImageName POSTGRES_IMAGE_NAME = DockerImageName.parse(
    Objects.toString(System.getenv("TESTCONTAINERS_POSTGRES_IMAGE"), "postgres:16-alpine"));

  @Container
  public static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>(POSTGRES_IMAGE_NAME)
    .withNetwork(network)
    .withNetworkAliases("mypostgres")
    .withExposedPorts(5432)
    .withUsername("username")
    .withPassword("password")
    .withDatabaseName("postgres");

  @Container
  public static final GenericContainer<?> okapi =
    new GenericContainer<>(DockerImageName.parse("busybox:1.35.0-uclibc"))
    .withCommand("busybox httpd -f -v -p 9130")
    .withCopyToContainer(Transferable.of("{}"), "/configurations/entries")
    .withNetwork(network)
    .withNetworkAliases("okapi")
    .withExposedPorts(9130);

  @Container
  public static final GenericContainer<?> mod =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-template-engine").withFileFromPath(".", Path.of(".")))
    .withNetwork(network)
    .withExposedPorts(8081)
    .withEnv("DB_HOST", "mypostgres")
    .withEnv("DB_PORT", "5432")
    .withEnv("DB_USERNAME", "username")
    .withEnv("DB_PASSWORD", "password")
    .withEnv("DB_DATABASE", "postgres");

  @BeforeAll
  static void beforeAll() {
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + mod.getHost() + ":" + mod.getFirstMappedPort();
    if (IS_LOG_ENABLED) {
      postgres.followOutput(new Slf4jLogConsumer(log).withSeparateOutputStreams().withPrefix("postgres"));
      okapi.followOutput(new Slf4jLogConsumer(log).withSeparateOutputStreams().withPrefix("okapi"));
      mod.followOutput(new Slf4jLogConsumer(log).withSeparateOutputStreams().withPrefix("mod"));
    }
    enableTenant();
  }

  static void setRequestSpecification() {
    RestAssured.requestSpecification = new RequestSpecBuilder()
        .addHeader("X-Okapi-Url", "http://okapi:9130")
        .addHeader("X-Okapi-Tenant", "diku")
        .setContentType(ContentType.JSON)
        .build();
  }

  static void enableTenant() {
    setRequestSpecification();

    String location =
        given()
          .body(new JsonObject().put("module_to", "999999.0.0").encode())
        .when()
          .post("/_/tenant")
        .then()
          .statusCode(201)
        .extract()
          .header("Location");

    when()
      .get(location + "?wait=60000")
    .then()
      .statusCode(200)
      .body("complete", is(true), "errors", is(nullValue()));
  }

  @BeforeEach
  void beforeEach() {
    setRequestSpecification();
  }

  @Test
  void health() {
    // request without X-Okapi-Tenant
    RestAssured.requestSpecification = null;

    when()
      .get("/admin/health")
    .then()
      .statusCode(200)
      .body(is("\"OK\""));
  }

  /**
   * Does Dockerfile provide packages needed for barcode PNG generation?
   * See <a href="https://issues.folio.org/browse/MODTEMPENG-57">MODTEMPENG-57</a>.
   */
  @Test
  void barcodePng() {
    Template template = new Template()
        .withDescription("Template with barcodes")
        .withOutputFormats(List.of("html"))
        .withTemplateResolver("mustache")
        .withLocalizedTemplates(new LocalizedTemplates().withAdditionalProperty("en",
            new LocalizedTemplatesProperty()
            .withHeader("Item barcode: {{item.barcode}}")
            .withBody("Item barcode image: {{item.barcodeImage}}")));

    String templateId =
        given()
          .body(Json.encode(template))
        .when()
          .post("/templates")
        .then()
          .statusCode(201)
        .extract()
          .path("id");

    TemplateProcessingRequest templateRequest =
        new TemplateProcessingRequest()
        .withTemplateId(templateId)
        .withLang("en")
        .withOutputFormat("html")
        .withContext(new Context()
            .withAdditionalProperty("item", new JsonObject().put("barcode", "4539876054382")));

    given()
      .body(Json.encode(templateRequest))
    .when()
      .post("/template-request")
    .then()
      .statusCode(200)
      .body("result.attachments[0].contentId", is("<barcode_4539876054382>"),
            "result.attachments[0].contentType", is("image/png"),
            // first 6 bytes of PNG-Header (89 50 4E 47 0D 0A) in Base64
            "result.attachments[0].data", startsWith("iVBORw0K"));
  }
}
