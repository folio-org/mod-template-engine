package org.folio.template.client;

import static com.github.tomakehurst.wiremock.common.ContentTypes.APPLICATION_JSON;
import static org.apache.hc.core5.http.HttpHeaders.ACCEPT;
import static org.folio.okapi.common.XOkapiHeaders.REQUEST_ID;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import org.folio.template.util.OkapiModuleClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({ VertxExtension.class, MockitoExtension.class })
class SettingsClientTest {

  public static final String OKAPI_URL = "http://okapi:9130";
  @Mock private WebClient webClient;
  @Mock private HttpRequest<Buffer> requestMock;
  @Mock private HttpResponse<Buffer> responseMock;

  @Test
  void lookupLocaleSetting_positive(Vertx vertx, VertxTestContext testContext) {
    var settingsClient = new SettingsClient(vertx, okapiHeaders());

    setMockWebClient(settingsClient);
    mockWebClientMethodForHttpRequest();
    when(responseMock.statusCode()).thenReturn(200);
    when(responseMock.bodyAsJsonObject()).thenReturn(settingsResponse());

    var localeSettingsFuture = settingsClient.lookupLocaleSetting();
    localeSettingsFuture.onComplete(testContext.succeeding(result -> {
      assertThat(result.getLanguageTag(), is("en-GB"));
      assertThat(result.getTimeZoneId(), is("Europe/London"));
      testContext.completeNow();
    }));
  }

  @Test
  void lookupLocaleSetting_positive_emptyResponse(Vertx vertx, VertxTestContext testContext) {
    var settingsClient = new SettingsClient(vertx, okapiHeaders());

    setMockWebClient(settingsClient);
    mockWebClientMethodForHttpRequest();
    when(responseMock.statusCode()).thenReturn(200);
    when(responseMock.bodyAsJsonObject()).thenReturn(new JsonObject());

    var localeSettingsFuture = settingsClient.lookupLocaleSetting();
    localeSettingsFuture.onComplete(testContext.succeeding(result -> {
      assertThat(result.getLanguageTag(), is("en-US"));
      assertThat(result.getTimeZoneId(), is("UTC"));
      testContext.completeNow();
    }));
  }

  @Test
  void lookupLocaleSetting_negative_internalServerError(Vertx vertx, VertxTestContext testContext) {
    var settingsClient = new SettingsClient(vertx, okapiHeaders());

    setMockWebClient(settingsClient);
    mockWebClientMethodForHttpRequest();
    when(responseMock.statusCode()).thenReturn(500);
    when(responseMock.body()).thenReturn(errorResponse());

    var localeSettingsFuture = settingsClient.lookupLocaleSetting();
    localeSettingsFuture.onComplete(testContext.failing(error -> {
      var expectedMessage = "Error getting locale settings. "
        + "Status: 500, body: {\"status\":\"500\",\"message\":\"error\"}";
      assertThat(error.getMessage(), is(expectedMessage));
      assertThat(error, instanceOf(OkapiModuleClientException.class));
      testContext.completeNow();
    }));
  }

  private void mockWebClientMethodForHttpRequest() {
    var expectedUri = OKAPI_URL + "/settings/entries";
    var expectedQuery = "scope==stripes-core.prefs.manage and key==tenantLocaleSettings";

    when(webClient.requestAbs(HttpMethod.GET, expectedUri)).thenReturn(requestMock);
    when(requestMock.addQueryParam("query", expectedQuery)).thenReturn(requestMock);
    when(requestMock.putHeader(ACCEPT, APPLICATION_JSON)).thenReturn(requestMock);
    when(requestMock.putHeader(URL, "http://okapi:9130")).thenReturn(requestMock);
    when(requestMock.putHeader(TENANT, "diku")).thenReturn(requestMock);
    when(requestMock.putHeader(TOKEN, "test-token")).thenReturn(requestMock);
    when(requestMock.putHeader(REQUEST_ID, "test-request-id")).thenReturn(requestMock);
    when(requestMock.addQueryParam("limit", "1")).thenReturn(requestMock);

    doAnswer(inv -> {
      inv.<Promise<HttpResponse<Buffer>>>getArgument(0).complete(responseMock);
      return Future.succeededFuture(responseMock);
    }).when(requestMock).send(any());
  }

  private void setMockWebClient(SettingsClient settingsClient) {
    try {
      var field = ReflectionUtils.findFields(
        settingsClient.getClass(), f -> f.getName().equals("webClient"), TOP_DOWN).get(0);
      field.setAccessible(true);
      field.set(settingsClient, webClient);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to set webClient field", e);
    }
  }

  private static Map<String, String> okapiHeaders() {
    return Map.of(
      URL, OKAPI_URL,
      TENANT, "diku",
      TOKEN, "test-token",
      REQUEST_ID, "test-request-id"
    );
  }

  private static JsonObject settingsResponse() {
    var configValue = new JsonObject()
      .put("locale", "en-GB")
      .put("timezone", "Europe/London");

    return new JsonObject()
      .put("totalRecords", 1)
      .put("items", new JsonArray()
        .add(new JsonObject().put("value", configValue)));
  }

  private static Buffer errorResponse() {
    var configValue = new JsonObject()
      .put("status", "500")
      .put("message", "error");

    return Buffer.buffer(configValue.encode(), "utf-8");
  }
}
