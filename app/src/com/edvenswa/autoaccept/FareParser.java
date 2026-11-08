package com.edvenswa.autoaccept;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a ride card's price from its text lines: the base fare plus any "+ extra"
 * (a tip the customer added, or a boost), e.g. "₹80 + ₹5", "₹80" then "+₹5", or "80 + 10".
 * Only the card's header (everything above the first distance line) is read, so other numbers are never mistaken for the fare.
 */
final class FareParser {
    static final Pattern FARE = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    static final Pattern PLUS = Pattern.compile("\\+\\s*(?:₹|Rs\\.?|INR)?\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    static final Pattern BARE_SUM = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\+\\s*(?:₹|Rs\\.?|INR)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$", Pattern.CASE_INSENSITIVE);
    /** A distance on its own short line: "0.9 km" or "400 m". */
    static final Pattern DIST_LINE = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(km|m)\\b", Pattern.CASE_INSENSITIVE);

    /** Returns {base, extra}, or null when the card shows no price. */
    static double[] parse(List<String> lines) {
        StringBuilder header = new StringBuilder();
        for (String s : lines) {
            if (s.length() <= 24 && DIST_LINE.matcher(s).find()) break; // distances start below the price
            header.append(s).append('\n');
        }
        String h = header.toString();
        Matcher m = FARE.matcher(h);
        if (m.find()) {
            double base = Double.parseDouble(m.group(1)), extra = 0;
            Matcher p = PLUS.matcher(h.substring(m.end()));
            while (p.find()) extra += Double.parseDouble(p.group(1));
            return new double[]{base, extra};
        }
        for (String s : lines) { // no currency sign at all: "80 + 10"
            Matcher b = BARE_SUM.matcher(s);
            if (b.find()) return new double[]{Double.parseDouble(b.group(1)), Double.parseDouble(b.group(2))};
        }
        return null;
    }
}
