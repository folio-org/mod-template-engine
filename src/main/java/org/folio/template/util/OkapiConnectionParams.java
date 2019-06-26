package org.folio.template.util;


import java.util.Map;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;

/**
 * Wrapper class for Okapi connection params
 */
public class OkapiConnectionParams {

  private static final String OKAPI_HEADER_URL = "x-okapi-url";

  private String okapiUrl;
  private String tenant;
  private String token;

  public OkapiConnectionParams(String okapiUrl, String tenant, String token) {
    this.okapiUrl = okapiUrl;
    this.tenant = tenant;
    this.token = token;
  }

  public OkapiConnectionParams(Map<String, String> okapiHeaders) {
    this.okapiUrl = okapiHeaders.get(OKAPI_HEADER_URL);
    this.tenant = okapiHeaders.get(OKAPI_HEADER_TENANT);
    this.token = okapiHeaders.get(OKAPI_HEADER_TOKEN);
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  public String getTenant() {
    return tenant;
  }

  public String getToken() {
    return token;
  }
}
