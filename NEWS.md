## 2023-10-11 v1.19.1
* Update RMB version

## 2023-10-11 v1.19.0
* Can't process templates with barcode images (MODTEMPENG-57)
* Upgrade dependencies for Poppy (MODTEMPENG-95)
* Use GitHub Workflows api-lint and api-schema-lint and api-doc (MODTEMPENG-93)
* Logging improvement (MODTEMPENG-80)

## 2022-10-18 v1.18.0
* Upgrade to RMB 35.0.0 and Vertx 4.3.3 (MODTEMPENG-89)

## 2022-06-27 v1.17.0
* Upgrade to RMB 34.0.0 (MODTEMPENG-87)

## 2022-02-22 v1.16.0
* Upgrade to RMB 33.2.4 (MODTEMPENG-79)
* Bump httpclient from 4.5.3 to 4.5.13
* Use new api-lint and api-doc CI facilities (FOLIO-3231)
* Upgrade to RMB 33.0.4 and Log4j 2.16.0 (MODTEMPENG-75)

## 2021-09-30 v1.15.0
 * Build migration script to rename deprecated template category `AutomatedFeeFine` to `AutomatedFeeFineCharge` (MODTEMPENG-72)

## 2021-06-11 v1.14.0
 * Upgrade to RMB 33.0.0 and Vertx 4.1.0 (MODTEMPENG-69)

## 2021-03-09 v1.13.0
 * Update template for password change email (MODTEMPENG-59)
 * Update to RMB 32.1.0 and Vertx 4.0.0 (MODTEMPENG-63)

## 2020-11-13 v1.12.0
 * Fix templates with barcode images (MODTEMPENG-57)
 * Upgrade to RMB 31.1.5 and Vert.x 3.9.4 (MODTEMPENG-60)

## 2020-09-10 v1.10.0
* Upgrade to JDK 11 and RMB v31 (MODTEMPENG-56)

## 2020-06-11 v1.9.0
 * Update account activation email template (MODTEMPENG-44)
 * Update RMB version to 30.0.2 and Vertx to 3.9.1 (MODTEMPENG-48)

## 2020-03-13 v1.8.0
 * Fix database error caused by RMB update (MODTEMPENG-37)

## 2019-12-03 v1.7.0
 * Fix American date format on checkout receipt (MODTEMPENG-25)
 * Entire Reset password URL is not hyperlinked (MODTEMPENG-27)
 * Fix memory leak related to WebClient objects (MODTEMPENG-29)
 * Tenant data is not purged when tenant interface is called with the DELETE (MODTEMPENG-30)
 * Fix security vulnerabilities reported in jackson-databind (MODTEMPENG-31)
 * Update tokens to only show date (MODTEMPENG-33)
 * Use JVM features to manage container memory (MODTEMPENG-35)
 * Update RMB version (MODTEMPENG-36)

## 2019-09-10 v1.6.0
 * Dates are not localized in context containing arrays (MODTEMGENG-23)
 * Fix security vulnerabilities reported in jackson-databind (MODTEMPENG-22)
 * Allow to process templates with array in context (MODTEMPENG-21)

## 2019-07-23 v1.5.0
 * Fix security vulnerabilities reported in jackson-databind
 * Localize dates from context at the time of template processing (MODTEMPENG-18)
 * Forbid in use templates deletion (MODTEMGENG-14)

## 2019-06-11 v1.4.0
 * Initial module metadata (FOLIO-2003)
 * Fix security vulnerabilities reported in jackson-databind
 * Add links to README additional info (FOLIO-473)

## 2019-05-06 v1.3.0
 * Make description field on patron notice template optional (MODTEMPENG-11)
 * Make header and body of template required  (MODTEMPENG-13)

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
