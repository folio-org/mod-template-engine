package org.folio.template.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BarcodeImageGeneratorTest {

  @Test
  void testValidBarcode() {
    String expectedResult = "iVBORw0KGgoAAAANSUhEUgAAARsAAAB2AQAAAADQSwq8AAAACXBIWXMAABi" +
        "bAAAYmwFJdYOUAAAAEnRFWHRTb2Z0d2FyZQBCYXJjb2RlNEryjnYuAAAA/ElEQVR42u3SMUoEMRQG4ExjGiHYTTGQwmJr2WYqcwQP4QUCNgrLz" +
        "oitjML0k6sEFjblu0JgB1K6mmaHlXmyB0hEEER4r/6Kn/9/jEszr4tD6SumzzjbtbpxWMqnx5azCp/FnteMESJEiBAhQoQIESJEiBChv0T4/fm" +
        "fo7BfqaNdV4U/NGnkaxnt6rywWiaRcxewg8kdu0uTRBx62MAUollAEi3QQG8ihhwKOMj+9UO+dVl0LZatFmBvX9JobrZ3w5UDq0UafeIYcRlMV" +
        "9+k0YQjOyGjMpliHJk5oVzw8A6xe3BqyJQZbCmivRcqN0uwlYx+lio38O/9039CX9VT/GUeidExAAAAAElFTkSuQmCC";

    String generatedBase64 = BarcodeImageGenerator.generateBase64Image("123456789");
    assertEquals(expectedResult, generatedBase64);
  }

  @Test
  void testBarcodeWithSpecialCharacters() {
    String expectedResult = "iVBORw0KGgoAAAANSUhEUgAAAsoAAAB2AQAAAAD5eojKAAAACXBIWXMAABi" +
        "bAAAYmwFJdYOUAAAAEnRFWHRTb2Z0d2FyZQBCYXJjb2RlNEryjnYuAAAB9ElEQVR42u3VMWvcMBQHcJtAzEGJO5SSLhVdMh8EWgcMWjv0WxR6a" +
        "0qWFozlEGi3NpDp4Ig/Su/oEefAVGO24MOHPSVnV20jU6FX+yCZW06BDk+TJNBveHr6y3JIrEOPSsWqt5A4eVyxRGYQ13rTybLArTxPKlLX2il" +
        "V4EaOLMlRu0jaA4e1pITknw9hDCT+tG/D9kYWhptUkmxr37KQRhpppJFGGmmkkUYaaaSRRhpppJFGGmmkkUYaaaSRRhpppJFGGmmkkf5bGu5rj" +
        "P+NvjFLc4CkInMFG7A7mTe/NVmKdrvkhmg3CpQFu1EqauUur8zRJyIhvlTh4PEr8bPhR2lHx4boko5q3bdfpOWi4ctTBqUBenIekhNRMJX3Opo" +
        "vJL8wRFtj390TZ0wV9ungY8qvFbn40tF0bdpPtpO9aoup6evRYDKPvwUUKDVCV3yog45OxYhXcAyyo7U1WJ+OWtrqaj0Tx/YPtsPqoSn6wYr+z" +
        "kazm4f2r5YWs1VBCrZ+rflQ6QNB/Vnp8+fhGyZyU7SXeFN9kD+l52fv4kdBweqvpui2+XrQd6Jgar2PI1Uw2YOu+Rbrx9OHl+ET6LtzVTxr4kl" +
        "TgfKhfTKwMBiqOZDbUL1s6dIgXWl6S6/iySAtrbuLu6psozTYdzNhmr7fv/F/of8A5ufDCvPGbMAAAAAASUVORK5CYII=";

    String generatedBase64 = BarcodeImageGenerator.generateBase64Image("abcABC!@#$%^&*()_+|/");
    assertEquals(expectedResult, generatedBase64);
  }

  @Test
  void testEmptyBarcode() {
    String generatedBase64 = BarcodeImageGenerator.generateBase64Image(StringUtils.EMPTY);
    assertEquals(StringUtils.EMPTY, generatedBase64);
  }

  @Test
  void testNullBarcode() {
    String generatedBase64 = BarcodeImageGenerator.generateBase64Image(null);
    assertEquals(StringUtils.EMPTY, generatedBase64);
  }

}