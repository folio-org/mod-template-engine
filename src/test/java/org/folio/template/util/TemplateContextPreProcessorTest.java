package org.folio.template.util;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateContextPreProcessorTest {

  @Test
  void contextIsEnrichedWithAdditionalDateTimeTokens() {
    String inputDate = "2019-06-18T14:04:33.205Z";

    JsonObject inputJson = new JsonObject()
      .put("rootDate", inputDate)
      .put("emptyDate", "")
      .put("blankDate", "   ")
      .putNull("nullDate")
      .put("loan", new JsonObject()
        .put("dueDate", inputDate)
        .put("enrichableDate", inputDate)
        .put("existingDate", inputDate)
        .put("existingDateTime", "unchanged"));

    JsonObject expectedJson = new JsonObject()
      .put("rootDate", inputDate)
      .put("emptyDate", "")
      .put("blankDate", "   ")
      .putNull("nullDate")
      .put("loan", new JsonObject()
        .put("dueDate", inputDate)
        .put("enrichableDate", inputDate)
        .put("existingDate", inputDate)
        .put("existingDateTime", "unchanged")
        .put("dueDateTime", inputDate)
        .put("enrichableDateTime", inputDate))
      .put("rootDateTime", inputDate);

    new TemplateContextPreProcessor(new LocalizedTemplatesProperty(), inputJson, null).enrichContextWithDateTimes();
    assertEquals(expectedJson, inputJson);
  }

  @Test
  void barcodeImageTokensAndAttachmentsAreCreated() {
    LocalizedTemplatesProperty template = new LocalizedTemplatesProperty()
      .withHeader("Barcode: {{item.barcode}}")
      .withBody("Barcode image: {{{item.barcodeImage}}}");

    JsonObject inputJson = new JsonObject()
      .put("barcode", "00000")
      .put("item", new JsonObject()
        .put("name", "tester")
        .put("barcode", "11111") // valid, barcodeImage is present in template
        .put("foobarcode", "22222")
        .put("barcoder", "33333"))
      .put("user", new JsonObject()
        .put("barcode", "22222")); // valid, but barcodeImage is NOT present in template

    JsonObject expectedJson = new JsonObject()
      .put("barcode", "00000")
      .put("item", new JsonObject()
        .put("name", "tester")
        .put("barcode", "11111")
        .put("foobarcode", "22222")
        .put("barcoder", "33333")
        .put("barcodeImage", "<img src='cid:barcode_11111' alt='item.barcodeImage'>"))
      .put("user", new JsonObject()
        .put("barcode", "22222"));

    Attachment expectedAttachment = new Attachment()
      .withContentId("<barcode_11111>")
      .withName("barcode_11111")
      .withDisposition("inline")
      .withContentType("image/png")
      .withData("iVBORw0KGgoAAAANSUhEUgAAAN0AAAB2AQAAAABkZKuaAAAACXBIWXMAABibAAAYmwFJdYOUAAAAEnRFW" +
        "HRTb2Z0d2FyZQBCYXJjb2RlNEryjnYuAAAAcUlEQVR42mNgl5//r47xh8UDGYYC++PHH/zv7z/Aflye+Q" +
        "/jOQaGUclRyVHJUclRyVHJUclRyVHJUcnBJ/kfN/hBtOT8z3/+z39OjqT84x//5Y9jl/wj3/zgjzw7Dsn" +
        "5k5/8ma9OdQcNI0ny43NQSAIAY75PvNLUmqwAAAAASUVORK5CYII=");

    JsonObject expectedAttachmentJson = JsonObject.mapFrom(expectedAttachment);

    TemplateContextPreProcessor processor = new TemplateContextPreProcessor(template, inputJson, null);
    processor.handleBarcodeImageTokens();

    assertEquals(expectedJson, inputJson);

    List<Attachment> attachments = processor.getAttachments();
    assertEquals(1, attachments.size());
    assertEquals(expectedAttachmentJson, JsonObject.mapFrom(attachments.get(0)));
  }

  @Test
  void duplicateTokensDoNotProduceDuplicateAttachments() {
    LocalizedTemplatesProperty template = new LocalizedTemplatesProperty()
      .withHeader("Barcode: {{item.barcode}}")
      .withBody("Barcode image: {{{item.barcodeImage}}} " +
        "Duplicate barcode: {{item.barcode}} " +
        "Duplicate barcode image: {{{item.barcodeImage}}}");

    JsonObject inputJson = new JsonObject()
      .put("item", new JsonObject()
        .put("barcode", "11111"));

    JsonObject expectedJson = new JsonObject()
      .put("item", new JsonObject()
        .put("barcode", "11111")
        .put("barcodeImage", "<img src='cid:barcode_11111' alt='item.barcodeImage'>"));

    TemplateContextPreProcessor processor = new TemplateContextPreProcessor(template, inputJson, null);
    processor.handleBarcodeImageTokens();

    assertEquals(expectedJson, inputJson);
    assertEquals(1, processor.getAttachments().size());
  }

  @Test
  void noNewTokensOrAttachmentsAreCreatedWhenImageTokenIsNotInTemplate() {
    LocalizedTemplatesProperty template = new LocalizedTemplatesProperty()
      .withHeader("User name: {{user.name}}")
      .withBody("User barcode: {{user.barcode}}");

    JsonObject inputJson = new JsonObject()
      .put("user", new JsonObject()
        .put("name", "Tester")
        .put("barcode", "11111"));

    JsonObject expectedJson = new JsonObject()
      .put("user", new JsonObject()
        .put("name", "Tester")
        .put("barcode", "11111"));

    TemplateContextPreProcessor processor = new TemplateContextPreProcessor(template, inputJson, null);
    processor.handleBarcodeImageTokens();

    assertEquals(expectedJson, inputJson);
    assertTrue(processor.getAttachments().isEmpty());
  }

}