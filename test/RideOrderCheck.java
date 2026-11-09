package com.edvenswa.autoaccept;

/**
 * Run: javac -encoding UTF-8 -d out app/src/com/edvenswa/autoaccept/RideOrder.java test/RideOrderCheck.java
 *      java -ea -cp out com.edvenswa.autoaccept.RideOrderCheck
 */
public class RideOrderCheck {
    // A is near with a low fare; B is far with a high fare and the best rate and the most kept
    static boolean aFirst(int rankA, int rankB, int sort) {
        return RideOrder.better(rankA, 5.0, 30, 22, 0.4, rankB, 9.0, 60, 48, 3.0, sort);
    }

    public static void main(String[] args) {
        assert aFirst(0, 0, 0) : "nearest pickup wins by default";
        assert !aFirst(0, 0, 1) : "best rupees per km wins";
        assert !aFirst(0, 0, 2) : "top fare wins";
        assert !aFirst(0, 0, 3) : "most kept wins";
        assert !aFirst(0, 1, 0) && !aFirst(0, 2, 0) : "a Stay In or Go To match beats a nearer ride";
        assert aFirst(2, 1, 3) && aFirst(1, 0, 3) : "Go To beats Stay In beats neither, whatever the sort";
        assert aFirst(2, 0, 2) : "rank is decided before the sort";
        // ties fall back to the nearest pickup
        assert RideOrder.better(0, 5, 30, 20, 0.5, 0, 5, 30, 20, 2.0, 2);
        assert !RideOrder.better(0, 5, 30, 20, 2.0, 0, 5, 30, 20, 0.5, 2);
        // unknown rate (no distance) counts as worst
        assert !RideOrder.better(0, Double.NaN, 30, 20, 0.4, 0, 3.0, 30, 20, 3.0, 1);
        // unknown pickup is farthest
        assert RideOrder.better(0, 5, 30, 20, 0.4, 0, 5, 30, 20, null, 0);
        System.out.println("RideOrder OK");
    }
}
