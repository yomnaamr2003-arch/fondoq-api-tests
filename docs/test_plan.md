# Test plan - Fondoq Booking API

Contract under test: `api_contract.md` (v1). Author: QA automation. Date: 2026-11-15.

## Scope

**In scope** (every endpoint in the contract):

| Endpoint | What is checked |
|---|---|
| `GET /ping` | 200 |
| `POST /auth` | token for valid credentials, no token for invalid ones |
| `GET /booking` | 200, JSON list of `{"bookingid": n}`, filters `firstname`, `lastname`, `checkin`, `checkout` |
| `GET /booking/{id}` | 200 + booking fields/types, 404 for an unknown id, 404 after delete |
| `POST /booking` | 200 + `{"bookingid", "booking"}`; 400 for every broken field rule (empty names, price <= 0, checkout not after checkin, bad date, wrong types, missing fields) |
| `PUT /booking/{id}` | 200 full update, 400 invalid body, 403 without / with invalid credentials, both credential styles (cookie token, Basic) |
| `PATCH /booking/{id}` | 200 partial update (only sent fields change), 403 without credentials |
| `DELETE /booking/{id}` | 204, 403 without credentials, then GET -> 404 |
| All endpoints | response time < 2 s, `Content-Type` header, body field names and types |

**Out of scope:** load/performance testing beyond the 2 s rule, security testing beyond the documented 403 rule, XML/form-encoded bodies, the semantic matching rule of the `checkin`/`checkout` filters (the contract does not define it - raised as a contract gap below), the partner's UI.

## Approach

1. **Manual exploratory testing** - every endpoint was called by hand (curl) against the contract before any automation: happy paths, each validation rule, both credential styles, missing/invalid credentials, unknown ids. This produced the candidate defects and the contract gaps listed below.
2. **Postman automation** (`postman/`) - one folder per endpoint group, a collection-level pre-request script that obtains the token, `pm.test` / `pm.expect` / JSON-schema assertions on status, time, `Content-Type` and body, a chained create -> get -> update -> patch -> delete -> get-404 flow, negative authorisation requests, and a data-driven run of `booking_test_data.csv` in the Collection Runner.
3. **REST Assured automation** (`restassured/`) - the same flows and validation cases as a Maven/JUnit 5 suite: BDD style (`given/when/then`) for the lifecycle, read, validation and authorisation tests, and non-BDD style (RequestSpecification + Response + JUnit assertions) for authentication and negative cases.
4. **Design rules (acceptance criteria):** each test creates the data it needs with a unique name and removes it afterwards; no credentials or tokens in requests (variables/scripts in Postman, `config.properties` / system properties in Java); each assertion checks exactly one contract rule so a red test always points at one real violation; known defects are tagged `known-defect` in Java so they can be excluded from a build with `-DexcludedGroups=known-defect` while the partner fixes them.

## Environments

| Name | Base URL | How it is selected |
|---|---|---|
| public | `https://restful-booker.herokuapp.com` | Postman environment `public.postman_environment.json`; Java default in `config.properties` |
| local (Docker) | `http://localhost:3001` (`docker run -d -p 3001:3001 mwinteringham/restfulbooker`) | Postman environment `local.postman_environment.json`; Java: `mvn test -DbaseUrl=http://localhost:3001` or env var `BASE_URL` |

Variables: `baseUrl`, `username`, `password` (secret) and `token` live in the Postman environment; the token is fetched by the collection pre-request script and refreshed after 10 minutes. Runtime ids (`bk_id`, `rl_id`, ...) are collection variables. In Java the base URL and credentials resolve from system property -> environment variable -> `config.properties`.

## Test cases

Status column = result of the runs: Postman/Newman against the public server (folders 01-06: 108 assertions, 6 failed; CSV folder 07: 59 assertions, 8 failed; identical to a local run on 2026-11-15) and REST Assured against the public server (2026-11-17: 46 tests, 19 failures = exactly the tests tagged `known-defect`; 27 tests green without them). PM = Postman folder, RA = REST Assured class.

| ID | Endpoint | Scenario | Expected | Automated in |
|---|---|---|---|---|
| TP-01 | GET /ping | API is up | 200, Content-Type present, < 2 s | PM 01; RA `BookingReadBddTest.ping` - **FAIL (BUG-002: 201)** |
| TP-02 | POST /auth | Valid credentials | 200, JSON, non-empty string `token` | PM 02; RA `AuthAndNegativeNonBddTest.authenticateWithValidCredentials` - pass |
| TP-03 | POST /auth | Wrong password | No `token` issued | PM 02; RA `AuthAndNegativeNonBddTest.authenticateWithWrongPasswordIssuesNoToken` - pass |
| TP-04 | GET /booking | List all | 200, JSON array of `{bookingid:int}`, contains the booking this test created | PM 04; RA `BookingReadBddTest.listBookings` - pass |
| TP-05 | GET /booking?lastname= | Filter by unique lastname | Exactly the booking created by the test | PM 04; RA `filterByLastname` - pass |
| TP-06 | GET /booking?firstname=&lastname= | Filter by firstname | Created booking is in the result | PM 04; RA `filterByFirstname` - pass |
| TP-07 | GET /booking?checkin=&checkout= | Date filters accepted | 200, list of `{bookingid:int}` | PM 04; RA `filterByDates` - pass |
| TP-08 | GET /booking/{id} | Existing booking | 200, schema-valid, fields equal what was created | PM 03 step 2, PM 04; RA `BookingLifecycleBddTest.getBooking` - pass |
| TP-09 | GET /booking/{id} | Unknown id (0) | 404 | PM 04; RA `BookingReadBddTest.unknownIdReturns404`, `AuthAndNegativeNonBddTest.getUnknownBookingReturns404` - pass |
| TP-10 | POST /booking | Valid booking | 200, `bookingid` int, `booking` echoes input, schema-valid | PM 03 step 1; RA `BookingLifecycleBddTest.createBookingReturnsIdAndEchoedBooking` - pass |
| TP-11 | POST /booking | CSV rows TC01-TC07 (valid) | 200 + echoed booking each | PM 07; RA `BookingValidationBddTest.validBookingIsCreated` - pass (7/7) |
| TP-12 | POST /booking | Empty firstname / lastname (TC08, TC09) | 400 | PM 07; RA `invalidBookingIsRejected` - **FAIL (BUG-003: 200)** |
| TP-13 | POST /booking | totalprice 0 / negative (TC10, TC11) | 400 | PM 07; RA `invalidBookingIsRejected` - **FAIL (BUG-004: 200)** |
| TP-14 | POST /booking | checkout before / equal to checkin (TC12, TC13) | 400 | PM 07; RA `invalidBookingIsRejected` - **FAIL (BUG-005: 200)** |
| TP-15 | POST /booking | Invalid date, month 13 (TC14) | 400 | PM 07; RA `invalidBookingIsRejected` - **FAIL (BUG-006: 200, dates stored as `0NaN-aN-aN`)** |
| TP-16 | POST /booking | depositpaid "maybe" (TC15) | 400 | PM 07; RA `invalidBookingIsRejected` - **FAIL (BUG-007: 200, coerced to true)** |
| TP-17 | POST /booking | totalprice as a string | 400 | PM 05; RA `BookingValidationBddTest.wrongTypeIsRejected` (string and decimal price) - **FAIL (BUG-007: 200, stored null / 12)** |
| TP-18 | POST /booking | Missing required field (each of 5) and `{}` | 400 | PM 05 (3 requests); RA `missingRequiredFieldIsRejected` (6 scenarios) - **FAIL (BUG-008: 500)** |
| TP-19 | PUT /booking/{id} | Full update, cookie token | 200, full updated booking, schema-valid | PM 03 step 3; RA `BookingLifecycleBddTest.updateBooking` - pass |
| TP-20 | PATCH /booking/{id} | Partial update, cookie token | 200, only sent fields change | PM 03 step 4; RA `partialUpdateBooking` - pass |
| TP-21 | PUT /booking/{id} | Invalid body (lastname missing) | 400 | PM 05; RA `BookingValidationBddTest.putWithInvalidBodyIsRejected` - pass |
| TP-22 | PUT /booking/{id} | Basic Authorization header | 200 | PM 06; RA `WriteAuthorisationBddTest.basicAuthIsAccepted` - pass |
| TP-23 | DELETE /booking/{id} | Delete with token | 204 | PM 03 step 5; RA `BookingLifecycleBddTest.deleteBooking` - **FAIL (BUG-001: 201)** |
| TP-24 | GET /booking/{id} | After delete | 404 | PM 03 step 6; RA `getBookingAfterDeleteReturns404` - pass |
| TP-25 | all lifecycle endpoints | Full flow create-get-update-patch-delete-404 | every step as above | PM folder 03; RA `fullFlow` - **FAIL (BUG-001 step only)** |
| TP-26 | PUT /booking/{id} | No credentials | 403, booking unchanged | PM 06; RA `WriteAuthorisationBddTest.writeWithoutCredentialsIsForbidden[PUT]`, `AuthAndNegativeNonBddTest.putWithoutCredentialsIsForbidden` - pass |
| TP-27 | PATCH /booking/{id} | No credentials | 403, booking unchanged | PM 06; RA `writeWithoutCredentialsIsForbidden[PATCH]` - pass |
| TP-28 | DELETE /booking/{id} | No credentials | 403, booking unchanged | PM 06; RA `writeWithoutCredentialsIsForbidden[DELETE]` - pass |
| TP-29 | PUT /booking/{id} | Invalid token | 403 | PM 06; RA `WriteAuthorisationBddTest.invalidTokenIsForbidden` - pass |
| TP-30 | GET /booking/{id} | Booking unchanged after rejected writes | Original values | PM 06; RA inside TP-26..28 - pass |
| TP-31 | every request | Response time | < 2000 ms | every PM request; every RA test (max measured 45 ms) - pass |
| TP-32 | every response | Content-Type header | `application/json` on JSON bodies, header present on error responses | every PM request; RA `contentType` / header checks - pass |
| TP-33 | whole suite | Independence and clean-up | Own data, unique names, deleted afterwards; server has the same bookings before and after | verified: 10 seeded bookings before and after both Newman runs |

33 cases, covering success (TP-01..11, 19, 20, 22, 24), validation (TP-12..18, 21) and authorisation (TP-26..30).

## Entry and exit criteria

**Entry:** contract `api_contract.md` agreed and frozen for the version under test; target environment reachable (`GET /ping` answers); credentials supplied by the partner; Node/Newman or Postman and Java 17+/Maven available.

**Exit (a run is "done" when):** all folders 01-07 and the Maven suite were executed against the target environment; every failed assertion is either fixed or has a bug report (`docs/bugs/BUG-nnn.md`) with steps, quoted contract text, actual result; no test left data behind; the run summary is in `test_report.pdf`. The API version is **accepted** when every test is green (or the `known-defect` tests are the only red ones and the partner has acknowledged the bug reports).

## Risks

- **Shared public server:** other people's data and resets can change the list size or availability. Mitigation: unique names, own data, filters by unique lastname, nothing depends on pre-existing bookings, clean-up after every test.
- **Run location:** both suites were executed on the public server (REST Assured 2026-11-17, Postman app and Newman 2026-11-18/20) and every result matched a run on a local copy of the upstream source (commit 619d031). The Postman app's free plan does not allow data files or result export in the Runner, so the CSV run and the exported JSON come from Newman.
- **Server behaviour may differ per version:** the public instance matched the local build for every defect; re-run after each partner release.
- **Client header pitfalls:** the API answers 418 to an Accept header that is not exactly `application/json` and 415 to a JSON Content-Type with `charset=ISO-8859-1`; REST Assured sends both by default for `ContentType.JSON`, so the specs use plain strings.
- **Token lifetime:** the token is cached; Postman refreshes it after 10 minutes. If the partner shortens token lifetime, adjust `TTL_MS`.
- **Failures hide data leaks:** when the API wrongly accepts invalid input it creates a booking; the tests delete such bookings (best effort). A crash mid-run could leave one behind - searchable by the `Qa`/`FQ` + `Tester` name prefixes.
- **Contract gaps** (not defects, to clarify with the partner):
  1. `POST /auth` with wrong credentials answers 200 `{"reason":"Bad credentials"}` - the contract does not say what it should return (a 401 would be conventional).
  2. `PUT`/`PATCH` on an unknown id, and `DELETE` on an already deleted id, answer **405 Method Not Allowed**; the contract only specifies 404 for GET.
  3. The matching rule of the `checkin` / `checkout` filters (equal? on or after?) is not defined; `?checkin=2026-11-01` returned bookings with later check-in dates.
  4. Error responses are `text/plain` (`Forbidden`, `Not Found`, `Bad Request`); the contract does not define an error body format.
