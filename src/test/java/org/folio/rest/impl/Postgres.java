package org.folio.rest.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Vertx;
import io.vertx.ext.sql.UpdateResult;

/**
 * Start embedded postgres before all test classes and stop it after all test classes.
 */
public final class Postgres {
  private static boolean shutdownHookInstalled = false;

  public static String getTenant() {
    return "testtenant";
  }

  public static void init() {
    // PostgresClient automatically starts embedded postgres if needed

    if (shutdownHookInstalled) {
      return;
    }
    Runtime.getRuntime().addShutdownHook(new Thread(PostgresClient::stopEmbeddedPostgres));
    shutdownHookInstalled = true;
  }

  /**
   * Truncate the template table
   */
  public static void truncate() {
    CompletableFuture<UpdateResult> future = new CompletableFuture<>();

    PostgresClient.getInstance(Vertx.vertx()).execute(
        "TRUNCATE " + getTenant() + "_mod_template_engine.template CASCADE", handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(handler.result());
    });

    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
