package com.fondoq.api.support;

import org.junit.jupiter.params.provider.Arguments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Reads booking_test_data.csv (the client's data file) and turns each row into a request body. */
public final class CsvRows {

    private CsvRows() {
    }

    /** Arguments: caseId, request body, expected status - only rows whose expected_status equals {@code status}. */
    public static Stream<Arguments> rowsExpecting(int status) {
        List<Arguments> rows = new ArrayList<>();
        try (InputStream in = CsvRows.class.getResourceAsStream("/booking_test_data.csv")) {
            if (in == null) {
                throw new IllegalStateException("booking_test_data.csv not found on the classpath");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] c = line.split(",", -1);
                int expected = Integer.parseInt(c[8].trim());
                if (expected != status) {
                    continue;
                }
                Map<String, Object> body = BookingData.booking(
                        c[1], c[2], number(c[3]), bool(c[4]), c[5], c[6], c[7]);
                rows.add(Arguments.of(c[0], body, expected));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return rows.stream();
    }

    private static Object number(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private static Object bool(String v) {
        String t = v.trim();
        if (t.equals("true")) {
            return Boolean.TRUE;
        }
        if (t.equals("false")) {
            return Boolean.FALSE;
        }
        return t; // e.g. "maybe" is sent as-is on purpose
    }
}
