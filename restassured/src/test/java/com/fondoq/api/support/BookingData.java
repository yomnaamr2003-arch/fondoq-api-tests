package com.fondoq.api.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Builders for booking request bodies. Every valid booking gets unique names, so tests never rely on existing data. */
public final class BookingData {

    private BookingData() {
    }

    public static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public static Map<String, Object> validBooking() {
        String suffix = uniqueSuffix();
        return booking("FQ" + suffix, "Tester" + suffix, 1450, true, "2026-11-02", "2026-11-05", "Breakfast");
    }

    public static Map<String, Object> booking(Object firstname, Object lastname, Object totalprice,
                                              Object depositpaid, String checkin, String checkout,
                                              String additionalneeds) {
        Map<String, Object> dates = new LinkedHashMap<>();
        dates.put("checkin", checkin);
        dates.put("checkout", checkout);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstname", firstname);
        body.put("lastname", lastname);
        body.put("totalprice", totalprice);
        body.put("depositpaid", depositpaid);
        body.put("bookingdates", dates);
        if (additionalneeds != null && !additionalneeds.isEmpty()) {
            body.put("additionalneeds", additionalneeds);
        }
        return body;
    }
}
