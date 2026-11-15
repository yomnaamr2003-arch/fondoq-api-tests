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

## Current result (2026-11-15)

The API does **not** match the contract: **8 defects**, listed in `docs/bugs/`. Highlights: `DELETE` answers **201 instead of 204** (BUG-001, the same kind of change that broke cancellations), none of the validation rules are enforced (BUG-003..007, one of them stores dates as `0NaN-aN-aN`), and a missing required field gives **500** (BUG-008).

| Run | Result |
|---|---|
| Postman folders 01-06 (Newman, local upstream server) | 108 assertions, 102 pass, 6 fail (BUG-001, 002, 007, 008 x3) |
| Postman folder 07 with the CSV (15 iterations) | 59 assertions, 51 pass, 8 fail (TC08-TC15 accepted instead of rejected: BUG-003..007) |
| REST Assured (46 tests) | **not executed yet** - see "Verification status". Expected: 27 pass, 19 fail on exactly the `known-defect` tests |

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

## Verification status (read before sending the bug reports)

- **Verified:** the Postman collection was executed with Newman against the real upstream Restful-Booker source (`github.com/mwinteringham/restful-booker`, commit 619d031) running on `localhost:3001`. The exported results and logs are in `postman/` and `evidence/`. The server held the same 10 seeded bookings before and after both runs, so the suite cleans up after itself.
- **Not verified:** (1) the public server `https://restful-booker.herokuapp.com` could not be reached from the environment where the suite was built, so the defects must be re-confirmed there by running the `public` environment once; (2) the REST Assured project could not be compiled or run there either (Maven Central was blocked): it was written against the REST Assured 5.5 / JUnit 5.11 APIs and only syntax-checked. Run `mvn test` once and fix any compile error before relying on it. The expected result is described above.
- Screenshots in `evidence/` are of the Newman console output. Add screenshots of the Postman Runner and of `mvn test` from your own machine for the submission.
