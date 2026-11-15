package com.fondoq.api;

import com.fondoq.api.support.ApiSpecs;
import com.fondoq.api.support.BaseApiTest;
import com.fondoq.api.support.BookingData;
import com.fondoq.api.support.TestConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NON-BDD style: a RequestSpecification is built step by step, the call returns a Response object, and the checks
 * are plain JUnit assertions on that object. No given()/when()/then() chain is used in this class.
 */
@DisplayName("Authentication and negative cases (non-BDD style)")
class AuthAndNegativeNonBddTest extends BaseApiTest {

    private static Map<String, String> credentials(String password) {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("username", TestConfig.username());
        credentials.put("password", password);
        return credentials;
    }

    @Test
    @DisplayName("POST /auth with valid credentials -> 200 and a non-empty token")
    void authenticateWithValidCredentials() {
        RequestSpecification request = RestAssured.with().spec(ApiSpecs.json());
        request.body(credentials(TestConfig.password()));

        Response response = request.post("/auth");

        assertEquals(200, response.getStatusCode(), "status code");
        assertTrue(response.getContentType().contains("application/json"), "Content-Type: " + response.getContentType());
        assertTrue(response.time() < 2000, "response time " + response.time() + " ms");
        String token = response.jsonPath().getString("token");
        assertNotNull(token, "token field");
        assertFalse(token.isBlank(), "token must not be blank");
    }

    @Test
    @DisplayName("Negative 1: PUT /booking/{id} without credentials -> 403")
    void putWithoutCredentialsIsForbidden() {
        int id = createBooking(BookingData.validBooking());
        RequestSpecification request = RestAssured.with().spec(ApiSpecs.json());
        request.body(BookingData.validBooking());

        Response response = request.put("/booking/" + id);

        assertEquals(403, response.getStatusCode(), "status code");
        assertTrue(response.time() < 2000, "response time " + response.time() + " ms");
        assertNotNull(response.getContentType(), "Content-Type header present");
    }

    @Test
    @DisplayName("Negative 2: GET /booking/{id} for an unknown id -> 404")
    void getUnknownBookingReturns404() {
        RequestSpecification request = RestAssured.with().spec(ApiSpecs.json());

        Response response = request.get("/booking/0");

        assertEquals(404, response.getStatusCode(), "status code");
        assertTrue(response.time() < 2000, "response time " + response.time() + " ms");
        assertNotNull(response.getContentType(), "Content-Type header present");
    }

    @Test
    @DisplayName("Negative 3: POST /auth with a wrong password does not issue a token")
    void authenticateWithWrongPasswordIssuesNoToken() {
        RequestSpecification request = RestAssured.with().spec(ApiSpecs.json());
        request.body(credentials(TestConfig.password() + "-wrong"));

        Response response = request.post("/auth");

        assertTrue(response.time() < 2000, "response time " + response.time() + " ms");
        assertNull(response.jsonPath().getString("token"), "no token must be issued");
    }
}
