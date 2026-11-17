package com.fondoq.api.support;

import io.restassured.builder.RequestSpecBuilder;

import io.restassured.specification.RequestSpecification;

import java.util.Base64;

/** Shared request specifications used by both the BDD and the non-BDD tests. */
public final class ApiSpecs {

    private ApiSpecs() {
    }

    /** JSON request with the configurable base URL. */
    public static RequestSpecification json() {
        return new RequestSpecBuilder()
                .setBaseUri(TestConfig.baseUrl())
               .setContentType("application/json; charset=UTF-8")
.setAccept("application/json")
                .build();
    }

    /** JSON request authorised with the Cookie token (contract: Cookie: token=...). */
    public static RequestSpecification jsonWithToken() {
        return new RequestSpecBuilder()
                .addRequestSpecification(json())
                .addHeader("Cookie", "token=" + AuthHelper.token())
                .build();
    }

    /** JSON request authorised with HTTP Basic (contract: Authorization: Basic base64(user:password)). */
    public static RequestSpecification jsonWithBasicAuth() {
        String raw = TestConfig.username() + ":" + TestConfig.password();
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes());
        return new RequestSpecBuilder()
                .addRequestSpecification(json())
                .addHeader("Authorization", "Basic " + encoded)
                .build();
    }
}
