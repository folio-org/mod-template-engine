package org.folio.template.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OkapiModuleClientExceptionTest {

  @Test
  void create_positive_emptyConstructor() {
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException();
    });

    assertEquals(0, actual.getStatus());
  }

  @Test
  void create_positive_cause() {
    var cause = new RuntimeException("test");
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException(cause);
    });

    assertEquals(0, actual.getStatus());
  }

  @Test
  void create_positive_messageAndCause() {
    var cause = new RuntimeException("test cause");
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException("test", cause);
    });

    assertEquals(0, actual.getStatus());
  }

  @Test
  void create_positive_messageAndStatus() {
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException("test", 404);
    });

    assertEquals(404, actual.getStatus());
  }
}
