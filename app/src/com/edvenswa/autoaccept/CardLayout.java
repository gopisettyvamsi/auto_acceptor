package com.edvenswa.autoaccept;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Splits a screen of ride cards into one region per card using only where the text sits on screen,
 * so it works however the driver app groups its views. (Rapido lists several cards in one scrolling list
 * and shows the other orders as small tabs down the left; neither is a card.)
 *
 * A card opens with a fare line ("₹30 + ₹3") in the same column as the distance lines ("1.6 km").
 * Everything from just above that fare down to just above the next card's fare belongs to the card.
 */
final class CardLayout {
    static final class Box {
        final String text;
        final int left, top, right, bottom;
        final Object ref; // whatever the caller wants back, for example the accessibility node

        Box(String text, int left, int top, int right, int bottom, Object ref) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.ref = ref;
        }
    }

    static final class Region {
        Box head;                                      // the fare line that opens the card
        Box dist1;                                     // its first distance line (the pickup distance)
        int start, end;                                // vertical span of the card
        final List<Box> boxes = new ArrayList<>();     // everything inside the card, in reading order

        List<String> lines() {
            List<String> out = new ArrayList<>();
            for (Box b : boxes) out.add(b.text);
            return out;
        }
    }

    static List<Region> split(List<Box> all, int w, int h) {
        List<Region> out = new ArrayList<>();
        List<Box> dists = new ArrayList<>();
        for (Box b : all) if (b.text.length() <= 24 && FareParser.DIST_LINE.matcher(b.text).find()) dists.add(b);
        if (dists.isEmpty()) return out;

        // the cards' column: the middle left edge of all distance lines
        int[] lefts = new int[dists.size()];
        for (int i = 0; i < lefts.length; i++) lefts[i] = dists.get(i).left;
        Arrays.sort(lefts);
        int colX = lefts[lefts.length / 2];

        // card openers: fare-like text in that column ("+₹3" extras and the tabs on the left are not openers)
        List<Box> cands = new ArrayList<>();
        for (Box b : all) {
            if (b.text.length() > 20 || b.text.startsWith("+") || !FareParser.FARE.matcher(b.text).find()) continue;
            if (Math.abs(b.left - colX) > 0.14 * w) continue;
            cands.add(b);
        }
        Collections.sort(cands, (x, y) -> Integer.compare(x.top, y.top));
        List<Box> heads = new ArrayList<>();
        for (Box b : cands) { // one opener per row: the leftmost
            Box last = heads.isEmpty() ? null : heads.get(heads.size() - 1);
            if (last != null && Math.abs(b.top - last.top) < 0.015 * h) { if (b.left < last.left) heads.set(heads.size() - 1, b); }
            else heads.add(b);
        }

        int pad = (int) (0.06 * h); // the ride-type label sits just above its fare
        for (int i = 0; i < heads.size(); i++) {
            Region r = new Region();
            r.head = heads.get(i);
            r.start = Math.max(0, r.head.top - pad);
            r.end = i + 1 < heads.size() ? heads.get(i + 1).top - pad : h;
            List<Box> inside = new ArrayList<>();
            for (Box b : all) if (b.top >= r.start && b.top < r.end && b.left > colX - 0.15 * w) inside.add(b);
            r.boxes.addAll(readingOrder(inside, h));
            for (Box b : r.boxes)
                if (b.text.length() <= 24 && FareParser.DIST_LINE.matcher(b.text).find()) { r.dist1 = b; break; }
            out.add(r);
        }
        return out;
    }

    /** Top to bottom, and left to right within a row. */
    static List<Box> readingOrder(List<Box> in, int h) {
        List<Box> byCentre = new ArrayList<>(in);
        Collections.sort(byCentre, (a, b) -> Integer.compare(a.top + a.bottom, b.top + b.bottom));
        List<Box> out = new ArrayList<>(), row = new ArrayList<>();
        int rowCy = 0;
        for (Box b : byCentre) {
            int cy = (b.top + b.bottom) / 2;
            if (!row.isEmpty() && cy - rowCy > 0.012 * h) flush(row, out);
            if (row.isEmpty()) rowCy = cy;
            row.add(b);
        }
        flush(row, out);
        return out;
    }

    private static void flush(List<Box> row, List<Box> out) {
        Collections.sort(row, (a, b) -> Integer.compare(a.left, b.left));
        out.addAll(row);
        row.clear();
    }
}
