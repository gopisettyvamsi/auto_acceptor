package com.edvenswa.autoaccept;

/**
 * Which of two ride cards auto-accept should take first: a Go To match beats a Stay In match beats neither,
 * then the driver's own choice (0 nearest pickup, 1 best rupees per km, 2 top fare, 3 most kept after fuel),
 * and the nearest pickup settles any tie.
 */
final class RideOrder {
    static boolean better(int rankA, double perKmA, double fareA, double keptA, Double pickupA,
                          int rankB, double perKmB, double fareB, double keptB, Double pickupB, int sort) {
        if (rankA != rankB) return rankA > rankB;
        double x, y;
        switch (sort) {
            case 1: x = Double.isNaN(perKmA) ? -1 : perKmA; y = Double.isNaN(perKmB) ? -1 : perKmB; break;
            case 2: x = fareA; y = fareB; break;
            case 3: x = keptA; y = keptB; break;
            default: return nearer(pickupA, pickupB);
        }
        return x != y ? x > y : nearer(pickupA, pickupB);
    }

    static boolean nearer(Double a, Double b) { return (a == null ? 1e9 : a) < (b == null ? 1e9 : b); }
}
