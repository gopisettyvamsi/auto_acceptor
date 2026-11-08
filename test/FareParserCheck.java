package com.edvenswa.autoaccept;

import java.util.Arrays;

/**
 * Run: javac -encoding UTF-8 -d out app/src/com/edvenswa/autoaccept/FareParser.java test/FareParserCheck.java
 *      java -ea -cp out com.edvenswa.autoaccept.FareParserCheck
 */
public class FareParserCheck {
    static void check(double base, double extra, String... lines) {
        double[] r = FareParser.parse(Arrays.asList(lines));
        assert r != null && r[0] == base && r[1] == extra : Arrays.toString(lines) + " -> " + Arrays.toString(r);
    }

    public static void main(String[] a) {
        check(60, 0, "Bike Boost", "₹60", "0.9 km", "Somewhere street");
        check(80, 5, "Bike Boost", "₹80", "+₹5", "0.9 km");
        check(80, 5, "₹80 + ₹5", "0.9 km");
        check(80, 5, "₹80+₹5", "0.9 km");
        check(80, 10, "₹80 + 10", "1.2 km");
        check(80, 10, "80 + 10", "1.2 km");
        check(60, 5, "₹", "60", "+5", "4.8 km");
        check(51.5, 0, "₹51.50", "400 m");
        check(42, 0, "Bike", "₹42", "4.8 km", "Keep ₹26 (+₹9 boost later)"); // text below the distances is ignored
        assert FareParser.parse(Arrays.asList("Accept", "Reject")) == null;
        System.out.println("FareParser OK");
    }
}
