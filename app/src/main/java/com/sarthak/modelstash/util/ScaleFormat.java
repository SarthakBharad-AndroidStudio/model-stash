package com.sarthak.modelstash.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever the user types into the scale field into one consistent form,
 * so "72", "1/72" and "1 : 72" are all stored as "1:72".
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class ScaleFormat {

    // Optional "1:" or "1/" prefix, then the denominator (e.g. 72, 144, 12.5 or 12,5).
    private static final Pattern SCALE =
            Pattern.compile("^(?:1\\s*[:/]\\s*)?(\\d{1,4}(?:[.,]\\d{1,2})?)$");

    private ScaleFormat() {
    }

    /**
     * @return "1:N" for valid input, "" for empty input (scale is optional),
     *         or null if the input isn't a scale at all.
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        Matcher m = SCALE.matcher(trimmed);
        if (!m.matches()) {
            return null;
        }
        String denominator = m.group(1).replace(',', '.');
        if (Double.parseDouble(denominator) <= 0) {
            return null; // "1:0" makes no sense
        }
        return "1:" + denominator;
    }
    /**
     * The N in a stored "1:N" scale, for sorting (1:350 is smaller than 1:35).
     *
     * @return N, or Double.MAX_VALUE when there is no usable scale (sorts last).
     */
    public static double denominator(String scale) {
        if (scale == null || !scale.startsWith("1:")) {
            return Double.MAX_VALUE;
        }
        try {
            return Double.parseDouble(scale.substring(2));
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }
}