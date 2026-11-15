package com.fondoq.api;

import com.fondoq.api.support.ApiSpecs;
import com.fondoq.api.support.BaseApiTest;
import com.fondoq.api.support.BookingData;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

/** BDD style: write operations need valid credentials (403 otherwise); both documented credential styles work. */
@DisplayName("Authorisation of write operations (BDD style)")
class WriteAuthorisationBddTest extends BaseApiTest {

    @ParameterizedTest(name = "{0} without credentials -> 403 and the booking is unchanged")
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    void writeWithoutCredentialsIsForbidden(String method) {
        Map<String, Object> original = BookingData.validBooking();
        int id = createBooking(original);

        RequestSpecification request = given().spec(ApiSpecs.json());
        if (!method.equals("DELETE")) {
            request = request.body(BookingData.booking("Hacker", "Hacker", 1, true, "2026-11-02", "2026-11-05", null));
        }
        request.when()
                .request(method, "/booking/{id}", id)
                .then()
                .statusCode(403)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());

        given()
                .spec(ApiSpecs.json())
                .when()
                .get("/booking/{id}", id)
                .then()
                .statusCode(200)
                .body("lastname", equalTo(original.get("lastname")))
                .body("firstname", equalTo(original.get("firstname")));
    }

    @Test
    @DisplayName("PUT with an invalid token -> 403")
    void invalidTokenIsForbidden() {
        int id = createBooking(BookingData.validBooking());

        given()
                .spec(ApiSpecs.json())
                .header("Cookie", "token=not-a-valid-token")
                .body(BookingData.validBooking())
                .when()
                .put("/booking/{id}", id)
                .then()
                .statusCode(403)
                .time(lessThan(MAX_RESPONSE_MS))
                .header("Content-Type", notNullValue());
    }

    @Test
    @DisplayName("PUT with the Basic Authorization header is accepted -> 200")
    void basicAuthIsAccepted() {
        Map<String, Object> original = BookingData.validBooking();
        int id = createBooking(original);
        Map<String, Object> update = BookingData.booking(
                "BasicAuth", original.get("lastname"), 600, false, "2026-11-02", "2026-11-05", null);

        given()
                .spec(ApiSpecs.jsonWithBasicAuth())
                .body(update)
                .when()
                .put("/booking/{id}", id)
                .then()
                .statusCode(200)
                .time(lessThan(MAX_RESPONSE_MS))
                .contentType(ContentType.JSON)
                .body("firstname", equalTo("BasicAuth"));
    }
}
