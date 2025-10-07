package org.folio.template.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OkapiModuleClientExceptionTest {

  @Test
  void create_positive_emptyConstructor() {
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException();
    });

    assertNull(actual.getStatus());
  }

  @Test
  void create_positive_cause() {
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException(new RuntimeException("test"));
    });

    assertNull(actual.getStatus());
  }

  @Test
  void create_positive_messageAndCause() {
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException("test", new RuntimeException("test cause"));
    });

    assertNull(actual.getStatus());
  }

  @Test
  void create_positive_messageAndStatus() {
    var actual = assertThrows(OkapiModuleClientException.class, () -> {
      throw new OkapiModuleClientException("test", 404);
    });

    assertEquals(404, actual.getStatus());
  }
}
