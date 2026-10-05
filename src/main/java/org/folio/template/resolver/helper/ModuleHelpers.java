package org.folio.template.resolver.helper;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.template.util.TemplateEngineHelper;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.HelperRegistry;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.TagType;
import com.github.jknack.handlebars.Template;

/**
 * Module specific Handlebars helpers: line break handling, locale-aware number and date
 * formatting, string membership and list filtering. Like {@code ConditionalHelpers} each constant
 * is a helper; unlike there, the constant name follows Java naming, so the helper name used in
 * templates is carried separately. Register all helpers with {@link #register(HelperRegistry)},
 * not with {@code registerHelpers(ModuleHelpers.class)}, which would use the constant names.
 */
public enum ModuleHelpers implements Helper<Object> {

  /**
   * HTML-escapes the value and replaces each CRLF/CR/LF line break with {@code <br>}, to preserve
   * multi-line text in HTML output. Usage:
   *
   * <pre>{@code
   *   {{nl2br order.notes}}
   * }</pre>
   */
  NL2BR("nl2br") {
    @Override
    public Object apply(final Object value, final Options options) {
      return replaceLineBreaks(value, LINE_BREAK);
    }
  },

  /**
   * HTML-escapes the value and replaces each CRLF/CR/LF line break with the mandatory separator
   * given as first parameter. The separator is taken verbatim from the template, so markup such
   * as {@code <br>} is not escaped. A missing separator fails the render, which the caller reports
   * as a client error. Usage:
   *
   * <pre>{@code
   *   {{nl2sep order.notes ", "}}
   * }</pre>
   */
  NL2SEP("nl2sep") {
    @Override
    public Object apply(final Object value, final Options options) {
      Object separator = options.params.length > 0 ? options.params[0] : null;
      if (separator == null) {
        throw new IllegalArgumentException("nl2sep requires a separator, e.g. {{nl2sep value \", \"}}");
      }
      return replaceLineBreaks(value, separator.toString());
    }
  },

  /**
   * Locale-aware number formatting, the Java equivalent of Intl.NumberFormat. locale defaults to
   * the tenant locale, then en-US; minDecimals and maxDecimals are optional. Usage:
   *
   * <pre>{@code
   *   {{numberFormat amount locale="de-DE" minDecimals=2 maxDecimals=2}}
   * }</pre>
   */
  NUMBER_FORMAT("numberFormat") {
    @Override
    public Object apply(final Object value, final Options options) {
      if (value == null) {
        return "";
      }
      double number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
      NumberFormat formatter = NumberFormat.getNumberInstance(resolveLocale(options));
      Number minDecimals = options.hash("minDecimals");
      if (minDecimals != null) {
        formatter.setMinimumFractionDigits(minDecimals.intValue());
      }
      Number maxDecimals = options.hash("maxDecimals");
      if (maxDecimals != null) {
        formatter.setMaximumFractionDigits(maxDecimals.intValue());
      }
      return formatter.format(number);
    }
  },

  /**
   * Locale-aware date formatting for raw ISO date values. Tokens ending in
   * Date/DateTime/DetailedDateTime are already localized by ContextDateTimeFormatter before
   * rendering, so this is meant for other ISO date values. locale precedence matches numberFormat.
   * An optional pattern overrides the localized style; otherwise a localized MEDIUM date is used.
   * Unparseable input is returned unchanged so a bad value never fails the whole render. Usage:
   *
   * <pre>{@code
   *   {{dateFormat someIsoDate locale="de-DE" pattern="yyyy-MM-dd"}}
   * }</pre>
   */
  DATE_FORMAT("dateFormat") {
    @Override
    public Object apply(final Object value, final Options options) {
      if (value == null) {
        return "";
      }
      String raw = value.toString();
      if (StringUtils.isBlank(raw)) {
        return "";
      }
      TemporalAccessor temporal = parseIsoDateTime(raw);
      if (temporal == null) {
        LOG.debug("dateFormat:: value is not an ISO date, returning unchanged: {}", raw);
        return raw;
      }
      Locale locale = resolveLocale(options);
      String pattern = options.hash("pattern");
      DateTimeFormatter formatter = pattern != null
        ? DateTimeFormatter.ofPattern(pattern, locale)
        : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
      try {
        return formatter.format(temporal);
      } catch (DateTimeException e) {
        // e.g. a time-based pattern applied to a date-only value
        LOG.debug("dateFormat:: cannot format {} with pattern {}: {}", raw, pattern, e.getMessage());
        return raw;
      }
    }
  },

  /**
   * String-equality membership test. Matches when the value's string form equals any of the
   * parameters' string forms; a null/absent value yields no match and every parameter is coerced
   * to its string form. As a block helper it renders the fn/inverse branch, inline and as a
   * subexpression it returns the boolean. Usage:
   *
   * <pre>{@code
   *   {{#equalsAny orderLine.orderFormat "P/E Mix" "Physical Resource"}}...{{else}}...{{/equalsAny}}
   *   {{#if (equalsAny orderLine.orderFormat "P/E Mix" "Physical Resource")}}...{{/if}}
   * }</pre>
   */
  EQUALS_ANY("equalsAny") {
    @Override
    public Object apply(final Object value, final Options options) throws IOException {
      boolean matched = false;
      if (value != null) {
        String target = value.toString();
        for (Object candidate : options.params) {
          if (candidate != null && target.equals(candidate.toString())) {
            matched = true;
            break;
          }
        }
      }
      if (options.tagType == TagType.SECTION) {
        return matched ? options.fn() : options.inverse();
      }
      return matched;
    }
  },

  /**
   * Filters a list to the elements whose value at a dot-notation path equals an expected string,
   * and renders the block once per match. First arg is the list, param0 the path (resolved per
   * element, Maps or beans), param1 the expected value; comparison is by string form, and a
   * null/absent value matches only a null expected. Exposes the usual loop vars over the
   * <em>filtered</em> matches -- @index, @first, @last, @odd, @even, @index_1 -- plus block params
   * (as |item idx|), and renders {{else}} when nothing matches. Usage:
   *
   * <pre>{@code
   *   {{#where orderLine.contributors "contributorNameType.name" "Personal name"}}{{#unless @first}}; {{/unless}}{{contributor}}{{/where}}
   * }</pre>
   */
  WHERE("where") {
    @Override
    public Object apply(final Object value, final Options options) throws IOException {
      Options.Buffer buffer = options.buffer();

      // Not a list → render the {{else}} block (if any) and stop.
      if (!(value instanceof Iterable)) {
        buffer.append(options.inverse());
        return buffer;
      }

      String path = options.param(0);                 // e.g. "contributorNameType.name"
      Object expected = options.param(1);             // e.g. "Personal name"
      String expectedStr = expected == null ? null : expected.toString();

      Context parent = options.context;
      Template fn = options.fn;

      // Pass 1: keep only matching elements (so @last is correct).
      List<Object> matches = new ArrayList<>();
      for (Object element : (Iterable<Object>) value) {
        Object actual = Context.newContext(element).get(path);   // resolve path on element only
        String actualStr = actual == null ? null : actual.toString();
        boolean hit = expectedStr == null ? actualStr == null : expectedStr.equals(actualStr);
        if (hit) {
          matches.add(element);
        }
      }

      // Pass 2: render each match with loop variables available.
      int size = matches.size();
      for (int i = 0; i < size; i++) {
        Object it = matches.get(i);
        Context itCtx = Context.newContext(parent, it)
          .combine("@index", i)
          .combine("@first", i == 0 ? "first" : "")
          .combine("@last", i == size - 1 ? "last" : "")
          .combine("@odd", i % 2 == 0 ? "" : "odd")
          .combine("@even", i % 2 == 0 ? "even" : "")
          .combine("@index_1", i + 1);
        buffer.append(options.apply(fn, itCtx, Arrays.asList(it, i)));
      }

      if (size == 0) {
        buffer.append(options.inverse());
      }
      return buffer;
    }
  };

  private static final Logger LOG = LogManager.getLogger("mod-template-engine");
  private static final String LINE_BREAK = "<br>";
  private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\r\\n|\\r|\\n");
  private static final String DEFAULT_LOCALE = "en-US";

  private final String helperName;

  ModuleHelpers(final String helperName) {
    this.helperName = helperName;
  }

  /**
   * The name under which the helper is called in templates, e.g. {@code nl2br}.
   *
   * @return The helper name.
   */
  public String helperName() {
    return helperName;
  }

  /**
   * Registers every module helper under its helper name.
   *
   * @param registry The Handlebars instance or another helper registry.
   */
  public static void register(final HelperRegistry registry) {
    for (ModuleHelpers helper : values()) {
      registry.registerHelper(helper.helperName, helper);
    }
  }

  /**
   * HTML-escapes the value and replaces every CRLF/CR/LF line break with the separator, taken
   * literally (no regex replacement semantics).
   *
   * @param value The value, may be null.
   * @param separator The separator emitted unescaped.
   * @return The escaped value as SafeString, or an empty string for a null value.
   */
  private static Object replaceLineBreaks(final Object value, final String separator) {
    if (value == null) {
      return "";
    }
    String escaped = Handlebars.Utils.escapeExpression(value.toString()).toString();
    String joined = LINE_BREAK_PATTERN.matcher(escaped).replaceAll(Matcher.quoteReplacement(separator));
    return new Handlebars.SafeString(joined);
  }

  /**
   * Resolves the locale for formatting helpers: explicit locale hash, then the tenant locale
   * carried in the context, then en-US.
   *
   * @param options The helper options.
   * @return The locale.
   */
  private static Locale resolveLocale(final Options options) {
    String localeTag = options.hash("locale");
    if (localeTag == null) {
      localeTag = options.get(TemplateEngineHelper.TENANT_LOCALE_CONTEXT_KEY);
    }
    return Locale.forLanguageTag(StringUtils.defaultIfBlank(localeTag, DEFAULT_LOCALE));
  }

  /**
   * Parses an ISO-8601 date or date-time, tolerating an optional zone offset and date-only values.
   *
   * @param raw The raw value.
   * @return The temporal, or null when the input is not a recognizable ISO temporal.
   */
  private static TemporalAccessor parseIsoDateTime(final String raw) {
    try {
      return OffsetDateTime.parse(raw);
    } catch (DateTimeParseException ignored) {
      // not an offset date-time, fall through
    }
    try {
      return LocalDateTime.parse(raw);
    } catch (DateTimeParseException ignored) {
      // not a local date-time, fall through
    }
    try {
      return LocalDate.parse(raw);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }
}
