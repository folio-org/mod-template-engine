package org.folio.template.util;

import com.github.wnameless.json.flattener.JsonFlattener;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.LocalizedTemplatesProperty;
import org.folio.rest.tools.parser.JsonPathParser;
import org.folio.template.client.LocaleConfiguration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.mail.Part.INLINE;
import static org.folio.template.util.ContextDateTimeFormatter.formatDatesInContext;

public class TemplateContextPreProcessor {
  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  private static final String ATTACHMENT_CONTENT_ID_TEMPLATE = "<%s>";
  private static final String ATTACHMENT_NAME_TEMPLATE = "barcode_%s";
  private static final String HTML_IMG_TEMPLATE = "<img src='cid:%s' alt='%s'>";
  private static final String TOKEN_TEMPLATE_REGULAR = "{{%s}}";
  private static final String TOKEN_TEMPLATE_HTML = "{{{%s}}}";
  private static final String TOKEN_PATTERN = "\\{\\{([.a-zA-Z]+)}}";
  private static final String CONTENT_TYPE_PNG = "image/png";

  private static final String SUFFIX_DATE = "Date";
  private static final String SUFFIX_TIME = "Time";
  private static final List<String> BARCODE_IMAGE_SUFFIXES = Arrays.asList(".barcode", "Hrid");
  private static final String SUFFIX_IMAGE = "Image";

  private final LocalizedTemplatesProperty template;
  private final JsonObject context;
  private final LocaleConfiguration config;
  private final Map<String, Attachment> attachments;
  private final JsonPathParser jsonParser;
  private final Set<String> templateTokens;

  public TemplateContextPreProcessor(
      LocalizedTemplatesProperty template, JsonObject context, LocaleConfiguration config) {
    this.template = template;
    this.context = context;
    this.config = config;
    this.attachments = new LinkedHashMap<>();
    this.jsonParser = new JsonPathParser(this.context);
    this.templateTokens = Collections.unmodifiableSet(getTokensFromTemplate());
  }

  public List<Attachment> getAttachments() {
    LOG.debug("getAttachments:: Retrieving attachments");
    LOG.info("getAttachments:: Retrieved attachments successfully");
    return new ArrayList<>(attachments.values());
  }

  public void process() {
    LOG.debug("process:: Started processing");
    enrichContextWithDateTimes();
    formatDatesInContext(context, config.getLanguageTag(), config.getTimeZoneId());
    handleBarcodeImageTokens();
  }

  void enrichContextWithDateTimes() {
    LOG.debug("enrichContextWithDateTimes:: Enriching context with date and time");
    final Map<String, Object> contextMap = getContextMap();
    contextMap.keySet().stream()
      .filter(key -> key.endsWith(SUFFIX_DATE))
      .filter(key -> objectIsNonBlankString(contextMap.get(key)))
      .filter(key -> !contextMap.containsKey(key + SUFFIX_TIME))
      .forEach(key -> jsonParser.setValueAt(key + SUFFIX_TIME, contextMap.get(key)));
  }

  void handleBarcodeImageTokens() {
    LOG.debug("handleBarcodeImageTokens:: Handling barcode image tokens");
    Map<String, Object> contextMap = getContextMap();
    Set<String> newTokens = new HashSet<>();

    contextMap.entrySet().stream()
      .filter(e -> isBarcodeImageSource(e.getKey()))
      .filter(e -> objectIsNonBlankString(e.getValue()))
      .map(e -> new Token(e.getKey(), (String) e.getValue()))
      .filter(token -> templateTokens.contains(token.shortPath() + SUFFIX_IMAGE))
      .forEach(token -> {
        final String imgContentId =  String.format(ATTACHMENT_NAME_TEMPLATE, token.value());
        final String imageTokenKey = token.fullPath() + SUFFIX_IMAGE;
        final String imageTokenValue = String.format(HTML_IMG_TEMPLATE, imgContentId, imgContentId);

        jsonParser.setValueAt(imageTokenKey, imageTokenValue);
        createAttachment(token.value(), imgContentId);
        newTokens.add(token.shortPath() + SUFFIX_IMAGE);
      });

    // For HTML to be interpreted correctly by Mustache,
    // tokens must be wrapped in triple curly braces: "{{{...}}}"
    fixTokensWithHtmlValue(newTokens);
  }

  private boolean isBarcodeImageSource(String tokenKey) {
    return BARCODE_IMAGE_SUFFIXES
      .stream()
      .anyMatch(tokenKey::endsWith);
  }

  private boolean objectIsNonBlankString(Object obj) {
    return obj instanceof String
        && StringUtils.isNoneBlank((String) obj);
  }

  private Map<String, Object> getContextMap() {
    return JsonFlattener.flattenAsMap(context.encode());
  }

  private Set<String> getTokensFromTemplate() {
    LOG.debug("getTokensFromTemplate:: Retrieving tokens from template");
    Set<String> tokens = new HashSet<>();
    Matcher matcher = Pattern.compile(TOKEN_PATTERN)
        .matcher(template.getHeader() + template.getBody());
    while (matcher.find()) {
      tokens.add(matcher.group(1));
    }
    LOG.info("getTokensFromTemplate:: Retrieved tokens from template");
    return tokens;
  }

  private void fixTokensWithHtmlValue(Set<String> keysFromContext) {
    LOG.debug("fixTokensWithHtmlValue:: Fixing tokens with HTML value");
    if (keysFromContext.isEmpty()) {
      LOG.warn("Keys from Context are empty");
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

  private void createAttachment(String barcode, String contentId) {
    LOG.debug("createAttachment:: Creating attachment for content ID: {}", contentId);
    if (attachments.containsKey(contentId)) {
      LOG.warn("Attachment with content ID : {} already exists", contentId);
      return;
    }
    // ContentId of the attachment must be wrapped in "<...>", otherwise webmail
    // clients (e.g. Gmail) may not display the image within email body
    String formattedContentId = String.format(ATTACHMENT_CONTENT_ID_TEMPLATE, contentId);

    Attachment attachment = new Attachment()
      .withData(BarcodeImageGenerator.generateBase64Image(barcode))
      .withContentType(CONTENT_TYPE_PNG)
      .withDisposition(INLINE)
      .withName(contentId)
      .withContentId(formattedContentId);

    attachments.put(contentId, attachment);
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

    private String extractShortPath(String fullPath) {
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
