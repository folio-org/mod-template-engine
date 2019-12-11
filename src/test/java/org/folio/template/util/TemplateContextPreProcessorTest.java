package org.folio.template.util;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.junit.jupiter.api.Test;

import java.util.Base64;
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
            .put("barcode", "123456789") // valid, barcodeImage is present in template
            .put("foobarcode", "22222")
            .put("barcoder", "33333"))
        .put("user", new JsonObject()
            .put("barcode", "22222")); // valid, but barcodeImage is NOT present in template

    JsonObject expectedJson = new JsonObject()
        .put("barcode", "00000")
        .put("item", new JsonObject()
            .put("name", "tester")
            .put("barcode", "123456789")
            .put("foobarcode", "22222")
            .put("barcoder", "33333")
            .put("barcodeImage", "<img src='cid:barcode_123456789' alt='item.barcodeImage'>"))
        .put("user", new JsonObject()
            .put("barcode", "22222"));

    String expectedAttachmentContentId = "<barcode_123456789>";
    String expectedAttachmentName = "barcode_123456789";
    String expectedAttachmentDisposition = "inline";
    String expectedAttachmentContentType = "image/png";

    TemplateContextPreProcessor processor = new TemplateContextPreProcessor(template, inputJson, null);
    processor.handleBarcodeImageTokens();

    assertEquals(expectedJson, inputJson);

    List<Attachment> attachments = processor.getAttachments();
    assertEquals(1, attachments.size());

    Attachment attachment = attachments.get(0);
    assertEquals(attachment.getContentId(), expectedAttachmentContentId);
    assertEquals(attachment.getContentType(), expectedAttachmentContentType);
    assertEquals(attachment.getDisposition(), expectedAttachmentDisposition);
    assertEquals(attachment.getName(), expectedAttachmentName);
    assertTrue(StringUtils.isNotBlank(attachment.getData()));
    Base64.getDecoder().decode(attachment.getData());
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