package org.folio.template.util;

/**
 * Exception indicating that okapi module responded in unexpected way
 */
public class OkapiModuleClientException extends RuntimeException {

  private final int status;

  /**
   * Constructs a new instance.
   */
  public OkapiModuleClientException() {
    super();
    this.status = 0;
  }

  /**
   * Constructs a new instance with the specified detail message and HTTP status code.
   *
   * @param message the detail message
   * @param status the HTTP status code associated with this exception
   */
  public OkapiModuleClientException(String message, int status) {
    super(message);
    this.status = status;
  }

  /**
   * Constructs a new instance  with the specified detail message and error cause.
   *
   * @param message the detail message
   * @param cause error cause
   */
  public OkapiModuleClientException(String message, Throwable cause) {
    super(message, cause);
    this.status = 0;
  }

  /**
   * Constructs a new instance with error cause.
   *
   * @param cause error cause
   */
  public OkapiModuleClientException(Throwable cause) {
    super(cause);
    this.status = 0;
  }

  /**
   * Returns the HTTP status code associated with this exception.
   *
   * @return the HTTP status code
   */
  public Integer getStatus() {
    return status;
  }
}
