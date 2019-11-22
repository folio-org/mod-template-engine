package org.folio.rest.impl;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Template;
import org.folio.template.dao.TemplateDaoImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

class TemplateDaoImplTest {

  @Test
  void testGetTemplatesFailure() {
    TemplateDaoImpl dao = new TemplateDaoImpl(null);
    Future<List<Template>> templates = dao.getTemplates("", 0, 0);
    assert templates.failed();
  }

}
