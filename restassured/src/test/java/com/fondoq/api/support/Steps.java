package com.fondoq.api.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the steps of one end-to-end flow without stopping at the first failed assertion, then reports all failed
 * steps together. This way "DELETE returns 204" failing does not hide the check "GET after DELETE returns 404".
 */
public final class Steps {

    private final List<String> failures = new ArrayList<>();

    public void check(String name, Runnable assertion) {
        try {
            assertion.run();
        } catch (AssertionError e) {
            String message = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
            failures.add(name + " -> " + message);
        }
    }

    public void assertAllPassed() {
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " step(s) failed:\n - " + String.join("\n - ", failures));
        }
    }
}
