package org.folio.util.handler;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Abstract class for handling requests asynchronously.
 * Method run() is en entry point for execution.
 * Subclasses override handle() and handleException(Throwable e) callbacks.
 */
public abstract class AbstractRequestHandler {

  /**
   * Entry point for handler. Runs <code>handle</code> wrapping it with try catch
   */
  public void run() {
    catchException(this::handle);
  }

  /**
   * Callback method for starting execution
   */
  protected abstract void handle();

  /**
   * Callback method for handling exceptions
   *
   * @param e thrown exception
   */
  protected abstract void handleException(Throwable e);

  /**
   * Creates handler that calls <code>handleException</code> in case of failure
   *
   * @param successResultHandler handler for successful result
   * @param <T>                  result type
   * @return handler
   */
  protected <T> Handler<AsyncResult<T>> wrapWithFailureHandler(Handler<T> successResultHandler) {
    Future<T> future = Future.future();
    future.setHandler(asyncResult -> {
      if (asyncResult.failed()) {
        handleException(asyncResult.cause());
      } else {
        catchException(() -> successResultHandler.handle(asyncResult.result()));
      }
    });
    return future;
  }

  /**
   * Executes given command in try/catch block and calls handleException in case of exception
   *
   * @param command command
   */
  protected void catchException(ThrowingCommand command) {
    try {
      command.execute();
    } catch (Exception e) {
      handleException(e);
    }
  }

  @FunctionalInterface
  public interface ThrowingCommand<T extends Exception> {
    void execute() throws T;
  }
}
