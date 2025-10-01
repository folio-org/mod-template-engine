package org.folio.template.util;

/**
 * Exception indicating that okapi module responded in unexpected way
 */
public class OkapiModuleClientException extends RuntimeException {

  private final Integer status;

  public OkapiModuleClientException() {
    super();
    this.status = null;
  }

  public OkapiModuleClientException(String message, int status) {
    super(message);
    this.status = status;
  }

  public OkapiModuleClientException(String message, Throwable cause) {
    super(message, cause);
    this.status = null;
  }

  public OkapiModuleClientException(Throwable cause) {
    super(cause);
    this.status = null;
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
