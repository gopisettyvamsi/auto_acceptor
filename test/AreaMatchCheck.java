package com.edvenswa.autoaccept;

/**
 * Run: javac -encoding UTF-8 -d out app/src/com/edvenswa/autoaccept/AreaMatch.java test/AreaMatchCheck.java
 *      java -ea -cp out com.edvenswa.autoaccept.AreaMatchCheck
 */
public class AreaMatchCheck {
    public static void main(String[] a) {
        String drop = "Kukatpally - LIG-556, Kukatpally Housing Board Colony, K P H B Phase 1, Hyderabad";
        assert AreaMatch.hasAny(drop, "Kukatpally");
        assert AreaMatch.hasAny(drop, "madhapur, KUKATPALLY ,  gachibowli");  // any name, any case, spaces ignored
        assert AreaMatch.hasAny(drop, "K P H B");
        assert !AreaMatch.hasAny(drop, "Madhapur, Gachibowli");
        assert !AreaMatch.hasAny(drop, "");                                    // an empty list never matches
        assert !AreaMatch.hasAny(drop, " , ,");
        assert !AreaMatch.hasAny("", "Kukatpally");
        assert !AreaMatch.hasAny(null, "Kukatpally");
        System.out.println("AreaMatch OK");
    }
}
