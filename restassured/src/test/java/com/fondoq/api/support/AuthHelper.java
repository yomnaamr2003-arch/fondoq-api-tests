package com.fondoq.api.support;

import io.restassured.RestAssured;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fetches (and caches) the auth token used by the write operations. */
public final class AuthHelper {

    private static String cachedToken;

    private AuthHelper() {
    }

    public static synchronized String token() {
        if (cachedToken == null) {
            Map<String, String> credentials = new LinkedHashMap<>();
            credentials.put("username", TestConfig.username());
            credentials.put("password", TestConfig.password());

            cachedToken = RestAssured.given()
                    .spec(ApiSpecs.json())
                    .body(credentials)
                    .post("/auth")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("token");

            if (cachedToken == null || cachedToken.isBlank()) {
                throw new IllegalStateException("POST /auth returned no token - check username/password");
            }
        }
        return cachedToken;
    }
}
