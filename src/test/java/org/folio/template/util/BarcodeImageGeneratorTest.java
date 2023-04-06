package org.folio.template.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class BarcodeImageGeneratorTest {

  @Test
  void testFontsExists() {
    assertDoesNotThrow(() -> BarcodeImageGenerator.checkFonts());
  }

  @Test
  void testNoFontLibrary() {
    var e = assertThrows(IllegalStateException.class, () -> {
      BarcodeImageGenerator.checkFonts(() -> {
        throw new UnsatisfiedLinkError("Error loading shared library libfreetype.so.6");
      });
    });
    assertThat(e.getMessage(), containsString("No font found."));
  }

  @Test
  void testZeroFonts() {
    var e = assertThrows(IllegalStateException.class, () -> BarcodeImageGenerator.checkFonts(() -> 0));
    assertThat(e.getMessage(), containsString("No font found."));
  }

  @Test
  void testValidBarcode() {
    String generatedBase64 = BarcodeImageGenerator.generateBase64Image("123456789");
    assertTrue(StringUtils.isNotBlank(generatedBase64));
    Base64.getDecoder().decode(generatedBase64);
  }

  @Test
  void testBarcodeWithSpecialCharacters() {
    String generatedBase64 = BarcodeImageGenerator.generateBase64Image("abcABC!@#$%^&*()_+|/");
    assertTrue(StringUtils.isNotBlank(generatedBase64));
    Base64.getDecoder().decode(generatedBase64);
  }

  @Test
  void testEmptyBarcode() {
    String generatedBase64 = BarcodeImageGenerator.generateBase64Image(StringUtils.EMPTY);
    assertTrue(generatedBase64.isEmpty());
  }

  @Test
  void testNullBarcode() {
    String generatedBase64 = BarcodeImageGenerator.generateBase64Image(null);
    assertTrue(generatedBase64.isEmpty());
  }

}