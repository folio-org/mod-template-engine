package org.folio.template.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.template.util.OkapiModuleClientException;

import java.util.Map;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;

public class CirculationStorageClient extends OkapiClient {
  private String circulationStoragePath;

  public CirculationStorageClient(Vertx vertx, Map<String, String> okapiHeaders) {
    super(vertx, okapiHeaders);
    circulationStoragePath = "/patron-notice-policy-storage/patron-notice-policies";
  }

  public Future<JsonObject> findPatronNoticePolicies(String query, int limit) {
    return getMany(circulationStoragePath, query, limit).future()
      .compose(resp -> resp.statusCode() == 200 ?
        succeededFuture(resp.bodyAsJsonObject()) :
        failedFuture(new OkapiModuleClientException(
          format("Error getting patron notice policies. Status: %d, body: %s",
            resp.statusCode(), resp.body()))));
  }
}
