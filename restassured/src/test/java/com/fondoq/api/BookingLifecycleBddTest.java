package com.fondoq.api;

import com.fondoq.api.support.ApiSpecs;
import com.fondoq.api.support.BaseApiTest;
import com.fondoq.api.support.BookingData;
import com.fondoq.api.support.Steps;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

/**
 * BDD style (given / when / then): create - get - update - partial update - delete - get (404).
 * Every test creates its own booking (unique names) and the base class deletes it afterwards, so the tests
 * can run in any order and never depend on data that already exists on the shared server.
 */
@DisplayName("Booking lifecycle (BDD style)")
class BookingLifecycleBddTest extends BaseApiTest {

    @Test
    @DisplayName("POST /booking -> 200, bookingid + echoed booking, JSON, schema, < 2 s")
    void createBookingReturnsIdAndEchoedBooking() {
        Map<String, Object> body = BookingData.validBooking();

        Response response = given()
                .spec(ApiSpecs.json())
                .body(body)
                .when()
                .post("/booking");
        trackIfCreated(response);

        response.then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body(matchesJsonSchemaInClasspath("schemas/create-booking.schema.json"))
                .body("booking.firstname", equalTo(body.get("firstname")))
                .body("booking.lastname", equalTo(body.get("lastname")))
                .body("booking.totalprice", equalTo(1450))
                .body("booking.depositpaid", equalTo(true))
                .body("booking.bookingdates.checkin", equalTo("2026-11-02"))
                .body("booking.bookingdates.checkout", equalTo("2026-11-05"))
                .body("booking.additionalneeds", equalTo("Breakfast"));
    }

    @Test
    @DisplayName("GET /booking/{id} -> 200 with the booking that was created")
    void getBooking() {
        Map<String, Object> body = BookingData.validBooking();
        int id = createBooking(body);

        given()
                .spec(ApiSpecs.json())
                .when()
                .get("/booking/{id}", id)
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body(matchesJsonSchemaInClasspath("schemas/booking.schema.json"))
                .body("firstname", equalTo(body.get("firstname")))
                .body("lastname", equalTo(body.get("lastname")))
                .body("totalprice", equalTo(1450))
                .body("depositpaid", equalTo(true))
                .body("bookingdates.checkin", equalTo("2026-11-02"))
                .body("bookingdates.checkout", equalTo("2026-11-05"))
                .body("additionalneeds", equalTo("Breakfast"));
    }

    @Test
    @DisplayName("PUT /booking/{id} -> 200 with the full updated booking")
    void updateBooking() {
        Map<String, Object> original = BookingData.validBooking();
        int id = createBooking(original);
        Map<String, Object> update = BookingData.booking(
                "Mona", original.get("lastname"), 1750, false, "2026-12-01", "2026-12-04", "Sea view");

        given()
                .spec(ApiSpecs.jsonWithToken())
                .body(update)
                .when()
                .put("/booking/{id}", id)
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body(matchesJsonSchemaInClasspath("schemas/booking.schema.json"))
                .body("firstname", equalTo("Mona"))
                .body("lastname", equalTo(original.get("lastname")))
                .body("totalprice", equalTo(1750))
                .body("depositpaid", equalTo(false))
                .body("bookingdates.checkin", equalTo("2026-12-01"))
                .body("bookingdates.checkout", equalTo("2026-12-04"))
                .body("additionalneeds", equalTo("Sea view"));
    }

    @Test
    @DisplayName("PATCH /booking/{id} -> 200, only the sent fields change")
    void partialUpdateBooking() {
        Map<String, Object> original = BookingData.validBooking();
        int id = createBooking(original);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("firstname", "Patched");
        patch.put("totalprice", 1999);

        given()
                .spec(ApiSpecs.jsonWithToken())
                .body(patch)
                .when()
                .patch("/booking/{id}", id)
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body(matchesJsonSchemaInClasspath("schemas/booking.schema.json"))
                .body("firstname", equalTo("Patched"))
                .body("totalprice", equalTo(1999))
                .body("lastname", equalTo(original.get("lastname")))
                .body("depositpaid", equalTo(true))
                .body("bookingdates.checkin", equalTo("2026-11-02"))
                .body("bookingdates.checkout", equalTo("2026-11-05"))
                .body("additionalneeds", equalTo("Breakfast"));
    }

    /** Known defect BUG-001: the API answers 201 Created instead of the contract's 204 No Content. */
    @Test
    @Tag("known-defect")
    @DisplayName("DELETE /booking/{id} -> 204 No Content")
    void deleteBooking() {
        int id = createBooking(BookingData.validBooking());

        given()
                .spec(ApiSpecs.jsonWithToken())
                .when()
                .delete("/booking/{id}", id)
                .then()
                .statusCode(204)
                .time(lessThan(MAX_RESPONSE_MS));
    }

    @Test
    @DisplayName("GET /booking/{id} after DELETE -> 404")
    void getBookingAfterDeleteReturns404() {
        int id = createBooking(BookingData.validBooking());

        // Setup only: any 2xx counts here, because the exact DELETE status is checked in deleteBooking().
        given()
                .spec(ApiSpecs.jsonWithToken())
                .when()
                .delete("/booking/{id}", id)
                .then()
                .statusCode(allOf(greaterThanOrEqualTo(200), lessThan(300)));

        given()
                .spec(ApiSpecs.json())
                .when()
                .get("/booking/{id}", id)
                .then()
                .statusCode(404)
                .time(lessThan(MAX_RESPONSE_MS));
    }

    /**
     * The whole flow in one test. Steps are collected with {@link Steps} so a failing step (DELETE status, BUG-001)
     * does not hide the later check that the booking is really gone.
     */
    @Test
    @Tag("known-defect")
    @DisplayName("Flow: create -> get -> update -> patch -> delete -> get 404")
    void fullFlow() {
        Map<String, Object> body = BookingData.validBooking();
        int id = createBooking(body); // asserts 200 and aborts the test if creation itself is broken
        Map<String, Object> update = BookingData.booking(
                "Mona", body.get("lastname"), 1750, false, "2026-12-01", "2026-12-04", "Sea view");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("firstname", "Patched");

        Steps steps = new Steps();
        steps.check("GET returns the created booking", () -> given()
                .spec(ApiSpecs.json()).when().get("/booking/{id}", id)
                .then().statusCode(200).body("lastname", equalTo(body.get("lastname"))));
        steps.check("PUT returns 200 with the updated booking", () -> given()
                .spec(ApiSpecs.jsonWithToken()).body(update).when().put("/booking/{id}", id)
                .then().statusCode(200).body("firstname", equalTo("Mona")).body("totalprice", equalTo(1750)));
        steps.check("PATCH returns 200 and keeps the other fields", () -> given()
                .spec(ApiSpecs.jsonWithToken()).body(patch).when().patch("/booking/{id}", id)
                .then().statusCode(200).body("firstname", equalTo("Patched")).body("totalprice", equalTo(1750)));
        steps.check("DELETE returns 204", () -> given()
                .spec(ApiSpecs.jsonWithToken()).when().delete("/booking/{id}", id)
                .then().statusCode(204));
        steps.check("GET after DELETE returns 404", () -> given()
                .spec(ApiSpecs.json()).when().get("/booking/{id}", id)
                .then().statusCode(404));
        steps.assertAllPassed();
    }
}
