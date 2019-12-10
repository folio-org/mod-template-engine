package org.folio.template.util;

import com.github.wnameless.json.flattener.JsonFlattener;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.tools.parser.JsonPathParser;
import org.folio.template.client.LocaleConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static javax.mail.Part.INLINE;
import static org.folio.template.util.ContextDateTimeFormatter.formatDatesInContext;

public class TemplateContextPreProcessor {

  // ContentId must be wrapped in "<...>", otherwise webmail clients (e.g. Gmail)
  // may not display the image within email body, but in list of attachments instead
  private static final String ATTACHMENT_CID_TEMPLATE = "<barcode_%s>";
  private static final String ATTACHMENT_NAME_TEMPLATE = "barcodeImage_%s";
  private static final String HTML_IMG_TEMPLATE = "<img src=\"cid:%s\" alt=\"%s\">";
  private static final String TOKEN_TEMPLATE = "{{%s}}";
  private static final String CONTENT_TYPE_PNG = "image/png";

  private static final String SUFFIX_DATE = "Date";
  private static final String SUFFIX_TIME = "Time";
  private static final String SUFFIX_BARCODE = ".barcode";
  private static final String SUFFIX_IMAGE = "Image";

  private final LocalizedTemplatesProperty template;
  private final JsonObject context;
  private final LocaleConfiguration config;
  private final List<Attachment> attachments = new ArrayList<>();
  private final JsonPathParser jsonParser;

  public TemplateContextPreProcessor(
      LocalizedTemplatesProperty template, JsonObject context, LocaleConfiguration config) {
    this.template = template;
    this.context = context;
    this.config = config;
    this.jsonParser = new JsonPathParser(this.context);
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public void process() {
    enrichContextWithDateTimes();
    formatDatesInContext(context, config.getLanguageTag(), config.getTimeZoneId());
    handleBarcodeImageTokens();
  }

  void enrichContextWithDateTimes() {
    final Map<String, Object> contextMap = getContextMap();
    contextMap.keySet().stream()
      .filter(key -> objectIsNonBlankString(contextMap.get(key)))
      .filter(key -> key.endsWith(SUFFIX_DATE))
      .filter(key -> !contextMap.containsKey(key + SUFFIX_TIME))
      .forEach(key -> jsonParser.setValueAt(key + SUFFIX_TIME, contextMap.get(key)));
  }

  void handleBarcodeImageTokens() {
    final Map<String, Object> contextMap = getContextMap();
    contextMap.keySet()
      .stream()
      .filter(key -> objectIsNonBlankString(contextMap.get(key)))
      .filter(key -> key.endsWith(SUFFIX_BARCODE))
      .filter(key -> templateContainsToken(template, key + SUFFIX_IMAGE))
      .forEach(key -> {
        final String barcode = (String) contextMap.get(key);
        final String contentId =  String.format(ATTACHMENT_CID_TEMPLATE, barcode);
        final String imageKey = key + SUFFIX_IMAGE;
        final String imageValue = String.format(HTML_IMG_TEMPLATE, contentId, imageKey);
        jsonParser.setValueAt(imageKey, imageValue);
        attachments.add(buildBarcodeImageAttachment(barcode, contentId));
      });
  }

  private boolean templateContainsToken(LocalizedTemplatesProperty template, String token) {
    String formattedToken = String.format(TOKEN_TEMPLATE, token);
    return template.getHeader().contains(formattedToken) || template.getBody().contains(formattedToken);
  }

  private Attachment buildBarcodeImageAttachment(String barcode, String contentId) {
    return new Attachment()
      .withData(BarcodeImageGenerator.generateBase64Image(barcode))
      .withContentType(CONTENT_TYPE_PNG)
      .withDisposition(INLINE)
      .withName(String.format(ATTACHMENT_NAME_TEMPLATE, barcode))
      .withContentId(contentId);
  }

  private boolean objectIsNonBlankString(Object obj) {
    return obj instanceof String
        && StringUtils.isNoneBlank((String) obj);
  }

  private Map<String, Object> getContextMap() {
    return JsonFlattener.flattenAsMap(context.encode());
  }

}
