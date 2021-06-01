package org.folio.rest.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

/**
 * Start embedded postgres before all test classes and stop it after all test classes.
 */
public final class Postgres {
  private static boolean shutdownHookInstalled = false;
  private static Vertx vertx = Vertx.vertx();

  public static String getTenant() {
    return "testtenant";
  }

  public static void init() {
    // PostgresClient automatically starts embedded postgres if needed
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    if (shutdownHookInstalled) {
      return;
    }
    Runtime.getRuntime().addShutdownHook(new Thread(PostgresClient::stopPostgresTester));
    shutdownHookInstalled = true;
  }

  public static RowSet<Row> runSql(String sql) {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();

    PostgresClient.getInstance(vertx).execute(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(handler.result());
    });

    try {
      return future.get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Run sql, return null on exception.
   */
  public static RowSet<Row> runSqlIgnore(String sql) {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();

    PostgresClient.getInstance(vertx).execute(sql, handler -> {
      if (handler.failed()) {
        future.complete(null);
        return;
      }
      future.complete(handler.result());
    });

    try {
      return future.get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  public static void dropSchema() {
    runSql("DROP SCHEMA IF EXISTS " + getTenant() + "_mod_template_engine CASCADE");
    runSqlIgnore("DROP OWNED BY "   + getTenant() + "_mod_template_engine CASCADE");
    runSql("DROP ROLE IF EXISTS "   + getTenant() + "_mod_template_engine");
    // Prevent "aclcheck_error" "permission denied for schema"
    // when recreating the ROLE with the same name but a different role OID.
    PostgresClient.closeAllClients();
  }

  /**
   * Truncate the template table
   */
  public static void truncate() {
    runSql("TRUNCATE " + getTenant() + "_mod_template_engine.template CASCADE");
  }
}
