# Fondoq booking API - regression suite

Automated contract tests for the partner booking API, built against `api_contract.md` (v1). The goal: catch a silent partner change (like last month's cancellation response) **before** customers do.

Two suites test the same contract:

| Suite | For | Run |
|---|---|---|
| **Postman collection** (`postman/`) | the whole team, exploratory + scheduled runs | Postman app, Collection Runner, or Newman |
| **REST Assured / Maven** (`restassured/`) | developers, CI build | `mvn test` |

```
fondoq-api-tests/
├── postman/
│   ├── fondoq-booking.postman_collection.json   7 folders, token via collection pre-request script
│   ├── public.postman_environment.json          baseUrl = https://restful-booker.herokuapp.com
│   ├── local.postman_environment.json           baseUrl = http://localhost:3001 (Docker)
│   ├── runner-results.json                      data-driven run of booking_test_data.csv (15 iterations)
│   ├── runner-results-flow.json                 run of folders 01-06 (regression flow)
│   └── booking_test_data.csv                    the client's data file (for the Runner)
├── restassured/                                 Maven project (JUnit 5 + REST Assured)
├── docs/
│   ├── test_plan.md                             scope, approach, environments, 33 test cases, risks
│   └── bugs/BUG-001.md ... BUG-008.md           one report per contract violation
├── evidence/                                    Newman console logs + screenshots of the runs
├── test_report.pdf                              test plan + run summary + all bug reports
└── README.md
```

## Current result (2026-11-20)

The API does **not** match the contract: **8 defects**, listed in `docs/bugs/`. Highlights: `DELETE` answers **201 instead of 204** (BUG-001, the same kind of change that broke cancellations), none of the validation rules are enforced (BUG-003..007, one of them stores dates as `0NaN-aN-aN`), and a missing required field gives **500** (BUG-008).

| Run | Result |
|---|---|
| Postman folders 01-06, **public server** (Postman app 2026-11-18, Newman 2026-11-20; also Newman on a local copy 2026-11-15) | 108 assertions, 102 pass, 6 fail (BUG-001, 002, 007, 008 x3), identical in all three runs |
| Postman folder 07 with the CSV (15 iterations), **public server**, Newman 2026-11-20 | 59 assertions, 51 pass, 8 fail (TC08-TC15 accepted instead of rejected: BUG-003..007), identical to the local run |
| REST Assured, **public server**, 2026-11-17 (46 tests) | `mvn test`: 27 pass, 19 fail. With `-DexcludedGroups=known-defect`: 27 run, 0 fail, BUILD SUCCESS. So the 19 failures are exactly the tests tagged `known-defect` |

Every red test is a real contract violation; nothing fails because of ordering or data.

## Postman

1. Import `postman/fondoq-booking.postman_collection.json` and both environments.
2. Select the **public** or **local** environment. (Local API: `docker run -d -p 3001:3001 mwinteringham/restfulbooker`.)
3. Run folders **01-06** (select them in the Collection Runner, without a data file). Each folder creates its own booking, so the order of folders does not matter.
4. Run folder **07 Data-driven create (CSV)** with `postman/booking_test_data.csv` as data file; every row asserts the `expected_status` column. Export the results from the Runner.

Command line (same thing, used to produce the files in this repo):

```bash
npm install -g newman
newman run postman/fondoq-booking.postman_collection.json -e postman/local.postman_environment.json \
  --folder "01 Health" --folder "02 Auth" \
  --folder "03 Booking lifecycle (create-get-update-patch-delete-404)" \
  --folder "04 Booking read, list and filters" --folder "05 Validation (structure and types)" \
  --folder "06 Authorisation (write operations)" \
  --reporters cli,json --reporter-json-export postman/runner-results-flow.json

newman run postman/fondoq-booking.postman_collection.json -e postman/local.postman_environment.json \
  --folder "07 Data-driven create (CSV)" -d postman/booking_test_data.csv \
  --reporters cli,json --reporter-json-export postman/runner-results.json
```

How it works:

- **No hard-coded URLs or tokens.** Requests use `{{baseUrl}}` and `Cookie: token={{token}}`. The collection-level **pre-request script** calls `POST /auth` with `{{username}}`/`{{password}}`, stores `token` in the environment and refreshes it after 10 minutes; it also builds the `Basic` header value for the second credential style.
- **Chained flow (folder 03):** create -> get -> PUT -> PATCH -> DELETE (204) -> GET (404). The id travels in the collection variable `bk_id`, set by the create request's test script.
- **Every request asserts** status code, response time < 2000 ms, `Content-Type` header, and body field names/types (`pm.response.to.have.jsonSchema`, `pm.expect`). The 204 response has no body, so it has no Content-Type assertion by design.
- **Negative cases:** folder 05 (invalid bodies, PUT invalid body -> 400) and folder 06 (PUT/PATCH/DELETE without credentials and with a bad token -> 403, booking unchanged afterwards).
- **Independence:** folders 04, 05 and 06 start with a `[setup]` request and end with a `[teardown]` request; negative POSTs that the API wrongly accepts are deleted again by their own test script.

## REST Assured

Requirements: Java 17+, Maven 3.9+.

```bash
cd restassured
mvn test                                              # public server (default in config.properties)
mvn test -DbaseUrl=http://localhost:3001              # any other base URL
mvn test -DexcludedGroups=known-defect                # skip tests of the 8 open bugs (e.g. for a green dev build)
```

Configuration resolves in this order: system property -> environment variable -> `src/test/resources/config.properties`: `baseUrl` / `BASE_URL`, `username` / `API_USERNAME`, `password` / `API_PASSWORD`. For CI, supply the credentials as secrets through the environment variables.

| Class | Style | Covers |
|---|---|---|
| `BookingLifecycleBddTest` | BDD | create, get, PUT, PATCH, DELETE, get-404, and the whole flow in one test (`Steps` keeps going after a failed step so a wrong DELETE status does not hide the 404 check) |
| `BookingReadBddTest` | BDD | `/ping`, list, filters, unknown id |
| `BookingValidationBddTest` | BDD | the client's CSV (valid and invalid rows), missing fields, wrong types, PUT with invalid body |
| `WriteAuthorisationBddTest` | BDD | 403 without credentials / with a bad token, Basic header accepted |
| `AuthAndNegativeNonBddTest` | **non-BDD** | the authentication call and three negative cases |

Each test creates its own booking (unique names), and `BaseApiTest` deletes everything it created, even bookings the API wrongly accepted. Tests do not share state, so they run in any order.

### BDD or non-BDD - when to use which

**BDD (`given().when().then()`)** reads like the contract: one chained statement per request with the expectation next to it. Use it for the bulk of the suite - status, headers, body matchers, JSON-schema checks and one-request-one-rule tests - because anyone (QA, developers, product owners) can read it and the failure message names the exact failed expectation.

**Non-BDD (`RequestSpecification` + `Response` + JUnit assertions)** gives you the `Response` object to use as a normal Java value. Use it when a test needs logic between sending and checking: reading the token and using it in the next call, comparing values from two responses, looping, conditional checks, custom messages, or reusing the response in helper methods. It is also easier to step through in a debugger and to put in shared helpers. The price is more code and less readable expectations, so it is the exception, not the default.

## Defects found

| ID | Summary |
|---|---|
| BUG-001 | `DELETE /booking/{id}` -> 201 Created instead of 204 |
| BUG-002 | `GET /ping` -> 201 instead of 200 |
| BUG-003 | empty `firstname` / `lastname` accepted |
| BUG-004 | `totalprice` 0 and negative accepted (POST and PUT) |
| BUG-005 | checkout before / equal to checkin accepted |
| BUG-006 | invalid date accepted and stored as `0NaN-aN-aN` |
| BUG-007 | wrong types silently coerced (`"maybe"` -> true, `"abc"` -> null, 12.5 -> 12) |
| BUG-008 | missing required field -> 500 instead of 400 |

## Verification status

- **Postman on the public server:** folders 01-06 were run in the Postman app (108 assertions, 6 failed) and with Newman; the CSV folder 07 was run with Newman (15 iterations, 59 assertions, 8 failed). Newman was used for the CSV run and for the exported JSON because the Postman app's free plan does not allow data files or result export in the Runner; Newman is the command-line runner of the same collection. The same runs on a local copy of the upstream source (commit 619d031) gave identical numbers, and the server held its seeded bookings before and after, so the suite cleans up after itself.
- **REST Assured on the public server** (`https://restful-booker.herokuapp.com`), run on 2026-11-17 with Java 21 / Maven 3.9.9: `mvn test` -> 46 tests, 19 failures; `mvn test -DexcludedGroups=known-defect` -> 27 tests, 0 failures, BUILD SUCCESS. All 8 reported defects are therefore confirmed on the public server. Output files are in `evidence/`.
- **Lesson recorded:** the first public run failed 41/46 because REST Assured's `setAccept(ContentType.JSON)` sends a list of JSON media types (the API answers 418) and `setContentType(ContentType.JSON)` appends `charset=ISO-8859-1` (the API answers 415). `ApiSpecs` now sends plain `application/json` strings.
- **Not covered by the public run:** the PUT variant of BUG-004 (price 0 on update) was only checked manually on the local copy.
- Screenshots in `evidence/` of the Newman runs are console output; the Maven screenshots are from `mvn test` on the public server.
