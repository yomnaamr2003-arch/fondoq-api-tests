package com.fondoq.api;

import com.fondoq.api.support.ApiSpecs;
import com.fondoq.api.support.BaseApiTest;
import com.fondoq.api.support.BookingData;
import com.fondoq.api.support.CsvRows;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * BDD style validation cases. The rows come from booking_test_data.csv (the client's file, copied to
 * src/test/resources). Anything the API wrongly accepts is deleted again by the base class.
 */
@DisplayName("POST /booking validation (BDD style, data-driven)")
class BookingValidationBddTest extends BaseApiTest {

    static Stream<Arguments> validRows() {
        return CsvRows.rowsExpecting(200);
    }

    static Stream<Arguments> invalidRows() {
        return CsvRows.rowsExpecting(400);
    }

    static Stream<Arguments> bodiesWithMissingRequiredField() {
        Stream.Builder<Arguments> out = Stream.builder();
        for (String field : new String[]{"firstname", "lastname", "totalprice", "depositpaid", "bookingdates"}) {
            Map<String, Object> body = BookingData.validBooking();
            body.remove(field);
            out.add(Arguments.of("missing " + field, body));
        }
        out.add(Arguments.of("empty object", new LinkedHashMap<String, Object>()));
        return out.build();
    }

    @ParameterizedTest(name = "{0}: valid booking is created (200)")
    @MethodSource("validRows")
    void validBookingIsCreated(String caseId, Map<String, Object> body, int expectedStatus) {
        Response response = given().spec(ApiSpecs.json()).body(body).when().post("/booking");
        trackIfCreated(response);

        response.then()
                .statusCode(expectedStatus)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body(matchesJsonSchemaInClasspath("schemas/create-booking.schema.json"))
                .body("booking.firstname", equalTo(body.get("firstname")))
                .body("booking.lastname", equalTo(body.get("lastname")))
                .body("booking.totalprice", equalTo(((Number) body.get("totalprice")).intValue()))
                .body("booking.depositpaid", equalTo(body.get("depositpaid")));
    }

    /** Known defects BUG-003 .. BUG-006: the API accepts rows TC08-TC15 with 200 instead of rejecting them with 400. */
    @ParameterizedTest(name = "{0}: invalid booking is rejected (400)")
    @MethodSource("invalidRows")
    @Tag("known-defect")
    void invalidBookingIsRejected(String caseId, Map<String, Object> body, int expectedStatus) {
        Response response = given().spec(ApiSpecs.json()).body(body).when().post("/booking");
        trackIfCreated(response);

        response.then()
                .statusCode(expectedStatus)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());
    }

    /** Known defect BUG-007: a missing required field makes the API crash with 500 instead of answering 400. */
    @ParameterizedTest(name = "{0} -> 400")
    @MethodSource("bodiesWithMissingRequiredField")
    @Tag("known-defect")
    void missingRequiredFieldIsRejected(String scenario, Map<String, Object> body) {
        Response response = given().spec(ApiSpecs.json()).body(body).when().post("/booking");
        trackIfCreated(response);

        response.then()
                .statusCode(400)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());
    }

    static Stream<Arguments> bodiesWithWrongType() {
        Map<String, Object> stringPrice = BookingData.validBooking();
        stringPrice.put("totalprice", "abc");
        Map<String, Object> decimalPrice = BookingData.validBooking();
        decimalPrice.put("totalprice", 12.5);
        return Stream.of(
                Arguments.of("totalprice is a string", stringPrice),
                Arguments.of("totalprice is a decimal", decimalPrice));
    }

    /** Known defect BUG-007: wrong types are silently coerced ("abc" -> null, 12.5 -> 12) instead of answering 400. */
    @ParameterizedTest(name = "{0} -> 400")
    @MethodSource("bodiesWithWrongType")
    @Tag("known-defect")
    void wrongTypeIsRejected(String scenario, Map<String, Object> body) {
        Response response = given().spec(ApiSpecs.json()).body(body).when().post("/booking");
        trackIfCreated(response);

        response.then()
                .statusCode(400)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());
    }

    @Test
    @DisplayName("PUT /booking/{id} with an invalid body (lastname missing) -> 400")
    void putWithInvalidBodyIsRejected() {
        Map<String, Object> original = BookingData.validBooking();
        int id = createBooking(original);
        Map<String, Object> invalid = BookingData.validBooking();
        invalid.remove("lastname");

        given()
                .spec(ApiSpecs.jsonWithToken())
                .body(invalid)
                .when()
                .put("/booking/{id}", id)
                .then()
                .statusCode(400)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());
    }
}
