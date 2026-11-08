package com.edvenswa.autoaccept;

import java.util.ArrayList;
import java.util.List;

/**
 * The 3-orders screen from a real phone (900 x 2000): two cards in a list, the order tabs on the left,
 * the Go To / Stay In chips on top and a banner over the lower card.
 *
 * Run: javac -encoding UTF-8 -d out app/src/com/edvenswa/autoaccept/FareParser.java app/src/com/edvenswa/autoaccept/CardLayout.java test/CardLayoutCheck.java
 *      java -ea -cp out com.edvenswa.autoaccept.CardLayoutCheck
 */
public class CardLayoutCheck {
    static final List<CardLayout.Box> all = new ArrayList<>();

    static void box(String t, int l, int top, int r, int bottom) { all.add(new CardLayout.Box(t, l, top, r, bottom, null)); }

    public static void main(String[] a) {
        // order tabs down the left, with their own fares
        box("₹30", 45, 300, 100, 330); box("+₹3", 45, 338, 100, 365);
        box("₹30", 45, 500, 100, 530); box("+₹3", 45, 538, 100, 565);
        box("₹30", 45, 700, 100, 730); box("+₹13", 40, 738, 105, 765);
        // chips on top
        box("Go To", 255, 255, 340, 290); box("Stay In", 460, 255, 555, 290);
        // card 1
        box("Bike Metro", 258, 380, 400, 415);
        box("₹30 + ₹3", 188, 445, 450, 495);
        box("1.6 km", 246, 540, 365, 580); box("Cyber Towers", 246, 585, 425, 612);
        box("Broadridge Building, Hitech City Rd, Patrika Nagar, HITEC City, 500081", 246, 620, 800, 690);
        box("2.1 km", 246, 735, 362, 770); box("(5 mins)", 374, 740, 474, 770);
        box("Jubilee Eclave Hitech City", 246, 778, 585, 806); box("RK Living, Kondapur, Gachibowli, Hyderabad", 246, 815, 800, 900);
        box("Accept", 530, 985, 632, 1015);
        // card 2
        box("Bike Metro", 258, 1130, 400, 1165);
        box("₹30 + ₹3", 188, 1195, 450, 1245);
        box("0.9 km", 246, 1290, 365, 1330); box("Madhapur Silicon Valley", 246, 1335, 560, 1362);
        box("Hitech City Metro Station, Silicon Valley, Madhapur, 500081", 246, 1370, 800, 1480);
        box("1.6 km", 246, 1525, 362, 1560); box("(5 mins)", 374, 1530, 474, 1560);
        box("Siddhi Vinayak Nagar Madhapur", 246, 1568, 668, 1596); box("Srinivasam Classic, Madhapur, Hyderabad", 246, 1605, 800, 1680);
        box("Extra from Customer", 352, 1705, 655, 1740);
        box("Accept", 530, 1735, 632, 1765);
        box("Home", 263, 1905, 325, 1935); box("Orders", 698, 1885, 770, 1910);

        List<CardLayout.Region> rs = CardLayout.split(all, 900, 2000);
        assert rs.size() == 2 : "two cards expected, got " + rs.size();

        double[] f1 = FareParser.parse(rs.get(0).lines()), f2 = FareParser.parse(rs.get(1).lines());
        assert f1[0] == 30 && f1[1] == 3 : "card 1 fare";
        assert f2[0] == 30 && f2[1] == 3 : "card 2 fare";

        assert rs.get(0).head.top == 445 && rs.get(1).head.top == 1195 : "each card is anchored on its own fare";
        assert rs.get(0).lines().contains("1.6 km") && rs.get(0).lines().contains("2.1 km") && !rs.get(0).lines().contains("0.9 km") : "card 1 distances";
        assert rs.get(1).lines().contains("0.9 km") && rs.get(1).lines().contains("1.6 km") && !rs.get(1).lines().contains("2.1 km") : "card 2 distances";
        assert rs.get(0).lines().contains("Bike Metro") && rs.get(1).lines().contains("Bike Metro") : "ride type label stays with its card";
        assert rs.get(0).lines().contains("Accept") && rs.get(1).lines().contains("Accept") : "each card has its own Accept";

        for (CardLayout.Region r : rs) {
            assert !r.lines().contains("Go To") && !r.lines().contains("Stay In") : "top chips are not part of a card";
            assert !r.lines().contains("+₹13") : "order tabs are not part of a card";
        }
        // reading order inside a card: the fare comes before the distances
        List<String> l2 = rs.get(1).lines();
        assert l2.indexOf("₹30 + ₹3") < l2.indexOf("0.9 km") && l2.indexOf("0.9 km") < l2.indexOf("1.6 km");

        // a single card (popup) still works
        all.clear();
        box("Bike", 258, 380, 400, 415); box("₹46", 188, 445, 300, 495);
        box("3.6 km", 246, 540, 365, 580); box("Hitech City", 246, 585, 425, 612); box("3.6 km", 246, 735, 362, 770); box("Accept", 530, 985, 632, 1015);
        assert CardLayout.split(all, 900, 2000).size() == 1;
        assert CardLayout.split(new ArrayList<>(), 900, 2000).isEmpty();
        System.out.println("CardLayout OK");
    }
}
