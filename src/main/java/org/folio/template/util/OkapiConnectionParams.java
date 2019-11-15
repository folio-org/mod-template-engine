package org.folio.template.util;


import org.folio.rest.RestVerticle;

import java.util.Map;

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
    this.tenant = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    this.token = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TOKEN);
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
