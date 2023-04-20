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
