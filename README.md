# mod-template-engine

Copyright (C) 2018-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

This module is responsible for storing templates and generating
text, html, xml, doc, docx etc from the template.
Generated payload is described by meta information which contains info
about format, size and date. Supports localization of templates.

| METHOD                               | DESCRIPTION                                        |
|--------------------------------------|----------------------------------------------------|
| POST /templates                      | Create new template in storage                     |
| GET /templates/{templateId}          | Get template from storage by id                    |
| PUT /templates/{templateId}          | Update template in storage                         |
| DELETE /templates/{templateId}       | Delete template from storage                       |
| GET /templates?query={custom\_query} | Get list of templates from storage by custom query |
| POST /template-request               | Process specified template using context           |
| POST /template-request/preview       | Render an inline template against a context without persisting it |

Example of template record:
```
{
     "id": "96cba796-2acc-4500-8277-26bde511dce7",
     "description": "Template for password change",
     "outputFormats": [
       "text/plain",
       "text/html"
     ],
     "templateResolver": "mustache",
     "scope": "circulation",
     "localizedTemplates": {
       "de": {
         "header": "Hallo message for {{user.name}}",
         "body": "Hallo {{user.name}}"
       },
       "en": {
         "header": "Hello message for {{user.name}}",
         "body": "Hello {{user.name}}"
       }
     }
   }
```
**POST /template-request :**
```
  {
    "templateId":"96cba796-2acc-4500-8277-26bde511dce7",
    "lang": "en",
    "outputFormat": "text/plain",
    "context": {
      "user": {
        "name": "Alex"
      },
      "item": {
        "name": "My Item"
      }
    }
  }
```
**Response :**
```
{
    "templateId": "96cba796-2acc-4500-8277-26bde511dce7",
    "result": {
        "header": "Hello message for Alex",
        "body": "Hello Alex"
    },
    "meta": {
        "size": 10,
        "dateCreate": "2018-10-22T15:26:10.560+0000",
        "lang": "en",
        "outputFormat": "text/plain"
    }
}
```

**POST /template-request/preview :**
```
{
  "header": "Welcome back, {{user.name}}!",
  "body": "Hi {{user.name}}, your {{user.pet.type}} {{user.pet.name}} has a message for you: {{{user.pet.hint}}}",
  "context": {
    "user": {
      "name": "Alex",
      "pet": {
        "type": "cat",
        "name": "Whiskers",
        "hint": "<b>Feed me NOW.</b>"
      }
    }
  }
}
```
**Response :**
```
{
  "header": "Welcome back, Alex!",
  "body": "Hi Alex, your cat Whiskers has a message for you: <b>Feed me NOW.</b>"
}
```

## Template engines

The `templateResolver` property selects the engine that renders a template. It is required on
stored templates; `POST /template-request/preview` falls back to `mustache` when it is omitted.

| templateResolver | Engine                                                              |
|------------------|---------------------------------------------------------------------|
| `mustache`       | [Mustache](https://mustache.github.io/mustache.5.html) (logic-less) |
| `handlebars`     | [Handlebars](https://handlebarsjs.com/guide/), a superset of Mustache |

Both engines render the same pre-processed context:

* Tokens ending in `Date` get a `Time` sibling, and all date/time values are formatted with the
  tenant locale and timezone (from `GET /locale`).
* Barcode tokens (`*.barcode`, `*Hrid`) are rendered as inline barcode images.
* A missing token renders as an empty string.
* `{{token}}` is HTML-escaped, `{{{token}}}` is emitted raw. Handlebars escapes `` ` `` and `=`
  in addition to Mustache's `& < > " '`.

### Handlebars

Handlebars templates are rendered with [Handlebars.java](https://github.com/jknack/handlebars.java)
4.4.0. See the [Handlebars language guide](https://handlebarsjs.com/guide/) for expressions,
block helpers and subexpressions. The following helpers are available.

The Usage columns tell how a helper can be called:

* inline: `{{helper value}}` renders the helper's result
* block: `{{#helper value}}...{{else}}...{{/helper}}` renders the block or its `{{else}}` part
* subexpression: `(helper value)` passes the result to another helper, e.g. `{{#if (helper value)}}`

In the Parameters columns, required parameters are marked with \*. Parameters written as `name=`
are named (hash) options, e.g. `size=10`; all others are positional and given in the listed order.
Leaving out a required parameter fails the render with HTTP 400, or gives a meaningless result.

#### Built-in helpers

See [Built-in helpers](https://handlebarsjs.com/guide/builtin-helpers.html).

The examples of the built-in and conditional helpers use the context
`{"status": "Open", "count": 7, "flag": true, "tags": ["a", "b", "c"], "user": {"name": "Alex"}}`.

| Helper   | Usage                 | Parameters                                    | Example                                                          | Result          |
|----------|-----------------------|-----------------------------------------------|------------------------------------------------------------------|-----------------|
| `if`     | block                 | `value`\*                                     | `{{#if flag}}yes{{else}}no{{/if}}`                               | `yes`           |
| `unless` | block                 | `value`\*                                     | `{{#unless flag}}no{{else}}yes{{/unless}}`                       | `yes`           |
| `each`   | block                 | `list`\*, `base=` (first `@index`, default 0) | `{{#each tags}}{{@index}}:{{this}}{{#unless @last}}, {{/unless}}{{/each}}` | `0:a, 1:b, 2:c` |
| `with`   | block                 | `object`\*                                    | `{{#with user}}{{name}}{{/with}}`                                | `Alex`          |
| `lookup` | inline, subexpression | `object`\*, `key`\* (index or property name)  | `{{lookup tags 1}}`                                              | `b`             |

Inside `each` (and `where`, see below) the loop variables `@index`, `@first`, `@last`, `@odd`
and `@even` are available.

#### Conditional helpers

[ConditionalHelpers](https://github.com/jknack/handlebars.java/blob/v4.4.0/handlebars/src/main/java/com/github/jknack/handlebars/helper/ConditionalHelpers.java)
([Javadoc](https://javadoc.io/doc/com.github.jknack/handlebars/4.4.0/com/github/jknack/handlebars/helper/ConditionalHelpers.html)).
All of them work both as block helper and as subexpression.

| Helper                      | Usage                | Parameters                     | Matches when                                  |
|-----------------------------|----------------------|--------------------------------|-----------------------------------------------|
| `eq` / `neq`                | block, subexpression | `a`\*, `b`\*, `yes=`, `no=`    | `a` equals / does not equal `b`               |
| `gt` / `gte` / `lt` / `lte` | block, subexpression | `a`\*, `b`\*, `yes=`, `no=`    | `a` is greater / greater or equal / less / less or equal than `b` |
| `and` / `or`                | block, subexpression | `a`\*, `b`, ..., `yes=`, `no=` | all / any of the values are not empty         |
| `not`                       | block, subexpression | `a`\*, `yes=`, `no=`           | `a` is empty                                  |

Empty means `null`, `false`, `0`, an empty string or an empty list. `yes=` and `no=` set the text that is
rendered when the helper is called inline (default `true` / `false`).

```
{{#eq status "Open"}}open{{else}}other{{/eq}}              -> open
{{eq status "Open" yes="Y" no="N"}}                       -> Y
{{#if (gt count 5)}}big{{/if}}                            -> big
{{#if (and flag (neq status "Closed"))}}ok{{/if}}         -> ok
{{#if (not flag)}}x{{else}}y{{/if}}                       -> y
```

#### Module helpers

| Helper         | Usage                 | Parameters                                                      | Example                                                                        | Result                  |
|----------------|-----------------------|-----------------------------------------------------------------|--------------------------------------------------------------------------------|-------------------------|
| `nl2br`        | inline, subexpression | `value`\*                                                       | `{{nl2br text}}`                                                               | `Main St 1<br>12345 Town` |
| `nl2sep`       | inline, subexpression | `value`\*, `separator`\*                                        | `{{nl2sep text ", "}}`                                                         | `Main St 1, 12345 Town` |
| `numberFormat` | inline, subexpression | `value`\*, `locale=`, `minDecimals=`, `maxDecimals=`            | `{{numberFormat amount locale="de-DE" minDecimals=2 maxDecimals=2}}`           | `1.234,50`              |
| `dateFormat`   | inline, subexpression | `value`\*, `locale=`, `pattern=`                                | `{{dateFormat date pattern="dd/MM/yyyy"}}`                                     | `11/09/2026`            |
| `equalsAny`    | block, subexpression  | `value`\*, `candidate`\*, ... (one or more)                     | `{{#equalsAny format "P/E Mix" "Physical Resource"}}physical{{else}}other{{/equalsAny}}` | `physical`    |
| `where`        | block                 | `list`\*, `path`\* (dot notation), `expected`\*                 | `{{#where items "type" "A"}}{{#unless @first}}, {{/unless}}{{name}}{{else}}none{{/where}}` | `x, z`      |

Examples use the context
`{"text": "Main St 1\n12345 Town", "amount": 1234.5, "date": "2026-09-11", "format": "P/E Mix", "items": [{"name": "x", "type": "A"}, {"name": "y", "type": "B"}, {"name": "z", "type": "A"}]}`.

* `nl2sep` HTML-escapes the value and replaces every line break (CRLF, CR, LF) with the separator,
  which is emitted unescaped. The separator is mandatory; without it the template fails to render
  with HTTP 400. `nl2br` is `nl2sep` with `<br>`, e.g. to keep multi-line notes in HTML emails.
* `numberFormat` formats a number; `minDecimals` and `maxDecimals` are optional.
* `dateFormat` formats a raw ISO date or date-time value, as a localized medium date or with an
  optional `pattern` ([DateTimeFormatter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeFormatter.html)
  syntax). Unparseable values are returned unchanged. Tokens ending in `Date` are already
  localized by the pre-processor and do not need it.
* `numberFormat` and `dateFormat` use the tenant locale by default. The optional `locale` parameter
  overrides it, e.g. `locale="de-DE"`. If no tenant locale is set, `en-US` is used.
* `equalsAny` matches when the value equals any of the given strings. It works as a block helper
  or as a subexpression, e.g. `{{#if (equalsAny orderLine.orderFormat "P/E Mix" "Physical Resource")}}`.
* `where` renders its block once for every list element whose value at a dot-notation path equals
  the given string, with the loop variables over the matches, and `{{else}}` when nothing matches.

**POST /template-request/preview with Handlebars :**
```
{
  "header": "{{#if user.pet}}News from {{user.pet.name}}{{else}}Welcome back{{/if}}, {{user.name}}!",
  "body": "Hi {{user.name}}, {{#equalsAny user.pet.type \"cat\" \"dog\"}}your {{user.pet.type}} {{user.pet.name}}{{else}}your pet{{/equalsAny}} has a message for you: {{{user.pet.hint}}}<br>Toys: {{#each user.pet.toys}}{{name}}{{#unless @last}}, {{/unless}}{{/each}}<br>Favourites: {{#where user.pet.toys \"favourite\" \"true\"}}{{#unless @first}}, {{/unless}}{{name}}{{else}}none{{/where}}",
  "templateResolver": "handlebars",
  "context": {
    "user": {
      "name": "Alex",
      "pet": {
        "type": "cat",
        "name": "Whiskers",
        "hint": "<b>Feed me NOW.</b>",
        "toys": [
          { "name": "Mouse", "favourite": true },
          { "name": "Ball", "favourite": false },
          { "name": "Feather", "favourite": true }
        ]
      }
    }
  }
}
```
**Response :**
```
{
  "header": "News from Whiskers, Alex!",
  "body": "Hi Alex, your cat Whiskers has a message for you: <b>Feed me NOW.</b><br>Toys: Mouse, Ball, Feather<br>Favourites: Mouse, Feather"
}
```

## Additional information

The [raml-module-builder](https://github.com/folio-org/raml-module-builder) framework.

Other [modules](https://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [MODTEMPENG](https://issues.folio.org/browse/MODTEMPENG)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-template-engine).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-template-engine).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-template-engine/).

### Dockerfile
Since version `1.8.0` the module contains functionality for barcode image generation,
which relies on system font configuration not found in all Alpine-based Docker images.
This dependency is installed if missing by adding following instructions
to Dockerfile:
```
USER root

RUN apk upgrade \
 && apk add \
      fontconfig \
      ttf-dejavu \
 && rm -rf /var/cache/apk/*

USER folio
```
Introduction of this new dependency affects this particular Docker image only and should not
cause any compatibility issues when running the module natively.
