# mod-template-engine

Copyright (C) 2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Introduction

This module is responsible for storing templates and generating
text, html, xml, doc, docx etc from the template.
Generated payload is described by meta information which contains info
about format, size and date. Supports localization of templates.

| METHOD                             | DESCRIPTION                                        |
|------------------------------------|----------------------------------------------------|
| POST /templates                     | Create new template in storage                     |
| GET /templates/{templateId}         | Get template from storage by id                    |
| PUT /templates/{templateId}         | Update template in storage                         |
| DELETE /templates/{templateId}      | Delete template from storage                       |
| GET /templates?query={custom_query} | Get list of templates from storage by custom query |
| POST /template-request              | Process specified template using context           |


# Additional information

The [raml-module-builder](https://github.com/folio-org/raml-module-builder) framework.

Other [modules](https://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)
