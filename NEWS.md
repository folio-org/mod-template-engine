## 2019-03-14 v1.2.0
 * Fix security vulnerabilities reported in jackson-databind (MODTEMPENG-10)

## 2018-10-17 v1.0.1-SNAPSHOT
 * Endpoint for processing templates **POST /template-request**  was implemented.
 * Mustache template resolver was registered with name 'mustache'.
 * Template's field 'templateResolver' is validated to match one of the registered template resolvers.
 
## 2018-08-30 v1.0.0-SNAPSHOT
The module provide storing of templates and access to them through REST API.
Templates support internationalization and store a set of templates texts for each language.
Also templates contain meta information that describes the type of source document and its scope.

CRUD API for templates was added:

| METHOD                             | DESCRIPTION                                        |
|------------------------------------|----------------------------------------------------|
| POST /template                     | Create new template in storage                     |
| GET /template/{templateId}         | Get template from storage by id                    |
| PUT /template/{templateId}         | Update template in storage                         |
| DELETE /template/{templateId}      | Delete template from storage                       |
| GET /template?query={custom_query} | Get list of templates from storage by custom query |

## 2018-08-30 v0.0.1
 * Initial module setup
