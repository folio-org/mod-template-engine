package org.folio.template.util;

import com.github.wnameless.json.flattener.JsonFlattener;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.tools.parser.JsonPathParser;
import org.folio.template.client.LocaleConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.mail.Part.INLINE;
import static org.folio.template.util.ContextDateTimeFormatter.formatDatesInContext;

public class TemplateContextPreProcessor {
  private static final String ATTACHMENT_CONTENT_ID_TEMPLATE = "<%s>";
  private static final String ATTACHMENT_NAME_TEMPLATE = "barcode_%s";
  private static final String HTML_IMG_TEMPLATE = "<img src='cid:%s' alt='%s'>";
  private static final String TOKEN_TEMPLATE_REGULAR = "{{%s}}";
  private static final String TOKEN_TEMPLATE_HTML = "{{{%s}}}";
  private static final String TOKEN_PATTERN = "\\{\\{([.a-zA-Z]+)}}";
  private static final String CONTENT_TYPE_PNG = "image/png";

  private static final String SUFFIX_DATE = "Date";
  private static final String SUFFIX_TIME = "Time";
  private static final String SUFFIX_BARCODE = ".barcode";
  private static final String SUFFIX_IMAGE = "Image";

  private final LocalizedTemplatesProperty template;
  private final JsonObject context;
  private final LocaleConfiguration config;
  private final List<Attachment> attachments;
  private final JsonPathParser jsonParser;
  private final Set<String> templateTokens;

  public TemplateContextPreProcessor(
      LocalizedTemplatesProperty template, JsonObject context, LocaleConfiguration config) {
    this.template = template;
    this.context = context;
    this.config = config;
    this.attachments = new ArrayList<>();
    this.jsonParser = new JsonPathParser(this.context);
    this.templateTokens = Collections.unmodifiableSet(getTokensFromTemplate());
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
    Map<String, Object> contextMap = getContextMap();
    Set<String> newTokens = new HashSet<>();

    contextMap.entrySet().stream()
      .filter(e -> objectIsNonBlankString(e.getValue()))
      .filter(e -> e.getKey().endsWith(SUFFIX_BARCODE))
      .map(entry -> new Token(entry.getKey(), (String) entry.getValue()))
      .filter(token -> templateTokens.contains(token.shortPath() + SUFFIX_IMAGE))
      .forEach(token -> {
        final String imgContentId =  String.format(ATTACHMENT_NAME_TEMPLATE, token.value());
        final String imageTokenKey = token.fullPath() + SUFFIX_IMAGE;
        final String imageTokenValue = String.format(HTML_IMG_TEMPLATE, imgContentId, imgContentId);

        jsonParser.setValueAt(imageTokenKey, imageTokenValue);
        attachments.add(buildBarcodeImageAttachment(token.value(), imgContentId));
        newTokens.add(token.shortPath() + SUFFIX_IMAGE);
      });

    // For HTML to be interpreted correctly by Mustache,
    // tokens must be wrapped in triple curly braces: "{{{...}}}"
    fixTokensWithHtmlValue(newTokens);
  }

  private Attachment buildBarcodeImageAttachment(String barcode, String contentId) {
    return new Attachment()
      .withData(BarcodeImageGenerator.generateBase64Image(barcode))
      .withContentType(CONTENT_TYPE_PNG)
      .withDisposition(INLINE)
      .withName(contentId)
      // ContentId of the attachment must be wrapped in "<...>", otherwise webmail
      // clients (e.g. Gmail) may not display the image within email body
      .withContentId(String.format(ATTACHMENT_CONTENT_ID_TEMPLATE, contentId));
  }

  private boolean objectIsNonBlankString(Object obj) {
    return obj instanceof String
        && StringUtils.isNoneBlank((String) obj);
  }

  private Map<String, Object> getContextMap() {
    return JsonFlattener.flattenAsMap(context.encode());
  }

  private Set<String> getTokensFromTemplate() {
    Set<String> tokens = new HashSet<>();
    Matcher matcher = Pattern.compile(TOKEN_PATTERN)
        .matcher(template.getHeader() + template.getBody());
    while (matcher.find()) {
      tokens.add(matcher.group(1));
    }
    return tokens;
  }

  private void fixTokensWithHtmlValue(Set<String> keysFromContext) {
    if (keysFromContext.isEmpty()) {
      return;
    }
    String header = template.getHeader();
    String body = template.getBody();
    for (String key : keysFromContext) {
      String existingToken = String.format(TOKEN_TEMPLATE_REGULAR, key);
      String replacementToken = String.format(TOKEN_TEMPLATE_HTML, key);

      String pattern = Pattern.quote(existingToken);
      header = header.replaceAll(pattern, replacementToken);
      body = body.replaceAll(pattern, replacementToken);
    }
    template.withHeader(header);
    template.withBody(body);
  }

  private static class Token {
    private final String fullPath;
    private final String shortPath;
    private final String value;

    Token(String token, String value) {
      this.fullPath = token;
      this.value = value;
      this.shortPath = extractShortPath(token);
    }

    private static String extractShortPath(String fullPath) {
      int arrayEndIndex = fullPath.lastIndexOf(']');
      return arrayEndIndex == -1 ? fullPath : fullPath.substring(arrayEndIndex + 2);
    }

    private String fullPath() {
      return fullPath;
    }

    private String shortPath() {
      return shortPath;
    }

    private String value() {
      return value;
    }
  }

}
