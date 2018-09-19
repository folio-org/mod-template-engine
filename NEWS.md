## 2018-08-30 v1.0.0-SNAPSHOT
The module provide storing of templates and access to them through REST API. 
Templates support internationalization and store a set of templates texts for each language. 
Also templates contains meta information that describes the type of source document and its scope.

CRUD API for templates was added: 

| METHOD                             | DESCRIPTION                                        |
|------------------------------------|----------------------------------------------------|
| POST /template                     | Create new template in storage                     |
| GET /template/id                   | Get template from storage by id                    |
| PUT /template/id                   | Update template in storage                         |
| DELETE /template/id                | Delete template from storage                       |
| GET /template?query={custom_query} | Get list of templates from storage by custom query |
## 2018-08-30 v0.0.1
 * Initial module setup
