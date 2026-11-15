package com.fondoq.api.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.lessThan;

/**
 * Base class: every test creates the data it needs and everything it created is deleted afterwards,
 * so tests are independent of each other and of whatever already exists on the shared server.
 */
public abstract class BaseApiTest {

    protected static final long MAX_RESPONSE_MS = 2000L;

    private final List<Integer> createdIds = new ArrayList<>();

    /** Creates a valid booking (asserting the contract's 200) and registers it for cleanup. */
    protected int createBooking(Map<String, Object> body) {
        int id = given()
                .spec(ApiSpecs.json())
                .body(body)
                .when()
                .post("/booking")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .time(lessThan(MAX_RESPONSE_MS))
                .extract()
                .path("bookingid");
        createdIds.add(id);
        return id;
    }

    /** Registers a booking id when the API created one (e.g. it wrongly accepted invalid input). */
    protected void trackIfCreated(Response response) {
        if (response.statusCode() == 200) {
            try {
                Integer id = response.path("bookingid");
                if (id != null) {
                    createdIds.add(id);
                }
            } catch (RuntimeException ignored) {
                // body is not JSON - nothing was created
            }
        }
    }

    @AfterEach
    protected void cleanUp() {
        for (Integer id : createdIds) {
            try {
                RestAssured.given()
                        .spec(ApiSpecs.jsonWithToken())
                        .delete("/booking/" + id);
            } catch (RuntimeException ignored) {
                // cleanup is best effort; an already deleted booking answers with an error status
            }
        }
        createdIds.clear();
    }
}
