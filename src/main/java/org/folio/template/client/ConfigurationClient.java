package org.folio.template.client;


import io.vertx.core.Future;
import org.folio.template.util.OkapiConnectionParams;


public interface ConfigurationClient {

  Future<LocaleConfiguration> lookupLocaleConfig(OkapiConnectionParams okapiConnectionParams);
}
