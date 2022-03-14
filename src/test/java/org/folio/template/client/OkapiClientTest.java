package org.folio.template.client;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import static org.folio.okapi.common.XOkapiHeaders.REQUEST_ID;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

class OkapiClientTest {

  @Test
  void ascertainHeaders() {
    Map<String, String> okapiHeaders = Map.of(
      REQUEST_ID, "test-request-id",
      TENANT, "test-tenant",
      TOKEN, "test-token",
      URL, "http://localhost"
    );
    OkapiClient okapiClient = new OkapiClient(Vertx.vertx(), okapiHeaders);
    MultiMap headers = okapiClient.getAbs("request").headers();

    assertNotNull(headers.get(URL));
    assertNotNull(headers.get(TOKEN));
    assertNotNull(headers.get(TENANT));
    assertNotNull(headers.get(REQUEST_ID));
  }
}
