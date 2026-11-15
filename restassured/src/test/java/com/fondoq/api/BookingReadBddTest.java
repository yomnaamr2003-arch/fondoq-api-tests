package com.fondoq.api;

import com.fondoq.api.support.ApiSpecs;
import com.fondoq.api.support.BaseApiTest;
import com.fondoq.api.support.BookingData;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

/** BDD style: health check, list, filters and unknown id. Tests own the data they look for (unique lastnames). */
@DisplayName("Health, list, filters and unknown id (BDD style)")
class BookingReadBddTest extends BaseApiTest {

    /** Known defect BUG-002: GET /ping answers 201 Created instead of 200. */
    @Test
    @Tag("known-defect")
    @DisplayName("GET /ping -> 200")
    void ping() {
        given()
                .spec(ApiSpecs.json())
                .when()
                .get("/ping")
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());
    }

    @Test
    @DisplayName("GET /booking -> 200, JSON array of {bookingid:int} containing a booking created by this test")
    void listBookings() {
        int id = createBooking(BookingData.validBooking());

        given()
                .spec(ApiSpecs.json())
                .when()
                .get("/booking")
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body("bookingid", everyItem(instanceOf(Integer.class)))
                .body("bookingid", hasItem(id));
    }

    @Test
    @DisplayName("GET /booking?lastname= returns exactly the booking with that unique lastname")
    void filterByLastname() {
        Map<String, Object> body = BookingData.validBooking();
        int id = createBooking(body);

        given()
                .spec(ApiSpecs.json())
                .queryParam("lastname", body.get("lastname"))
                .when()
                .get("/booking")
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body("bookingid", contains(id));
    }

    @Test
    @DisplayName("GET /booking?firstname=&lastname= returns the booking created by this test")
    void filterByFirstname() {
        Map<String, Object> body = BookingData.validBooking();
        int id = createBooking(body);

        given()
                .spec(ApiSpecs.json())
                .queryParam("firstname", body.get("firstname"))
                .queryParam("lastname", body.get("lastname"))
                .when()
                .get("/booking")
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body("bookingid", hasItem(id));
    }

    /** The contract names the checkin/checkout filters but not their matching rule, so only shape and status are checked. */
    @Test
    @DisplayName("GET /booking?checkin=&checkout= is accepted and returns a list of ids")
    void filterByDates() {
        given()
                .spec(ApiSpecs.json())
                .queryParam("checkin", "2026-11-02")
                .queryParam("checkout", "2026-11-05")
                .when()
                .get("/booking")
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body("bookingid", everyItem(instanceOf(Integer.class)));
    }

    @Test
    @DisplayName("GET /booking/{id} for an unknown id -> 404 (booking ids start at 1, so 0 never exists)")
    void unknownIdReturns404() {
        given()
                .spec(ApiSpecs.json())
                .when()
                .get("/booking/{id}", 0)
                .then()
                .statusCode(404)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());
    }
}
