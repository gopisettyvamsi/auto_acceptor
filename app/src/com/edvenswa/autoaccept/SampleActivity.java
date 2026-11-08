package com.edvenswa.autoaccept;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A test screen that looks like a driver app's "3 Orders" list: order tabs on the left, several ride cards,
 * each with its own Accept. The service treats it like a watched driver app, so drivers can see the chips,
 * the priority rules and auto-accept working without waiting for a real request.
 */
public class SampleActivity extends Activity {
    static volatile boolean visible;

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return t;
    }

    GradientDrawable box(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    /** One ride card: type, fare "base + extra", pickup distance and place, drop distance, time and place, Accept. */
    LinearLayout card(String type, String fare, String pickupKm, String pickupPlace, String dropKm, String mins, String dropPlace) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(box(Color.WHITE, 16, 0xFFD0D5DD));
        c.addView(tv(type, 14, 0xFF333333, false));
        TextView f = tv(fare, 28, 0xFF111111, true);
        c.addView(f);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = dp(10);
        c.addView(tv(pickupKm, 22, 0xFF111111, true), pl);
        c.addView(tv(pickupPlace, 14, 0xFF444444, false));
        LinearLayout drop = new LinearLayout(this);
        drop.setGravity(Gravity.CENTER_VERTICAL);
        drop.addView(tv(dropKm, 22, 0xFF111111, true));
        TextView m = tv(" " + mins, 14, 0xFF666666, false);
        m.setPadding(dp(8), 0, 0, 0);
        drop.addView(m);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(12);
        c.addView(drop, dl);
        c.addView(tv(dropPlace, 14, 0xFF444444, false));
        TextView accept = tv("Accept", 18, 0xFF111111, true);
        accept.setGravity(Gravity.CENTER);
        accept.setBackground(box(0xFFFFC61A, 28, 0));
        accept.setClickable(true);
        accept.setOnClickListener(v -> Toast.makeText(this, "Sample ride accepted", Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(52));
        al.topMargin = dp(14);
        c.addView(accept, al);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.bottomMargin = dp(14);
        c.setLayoutParams(cl);
        return c;
    }

    @Override protected void onCreate(Bundle b) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F2);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackgroundColor(Color.WHITE);
        head.setPadding(dp(20), dp(16), dp(20), dp(12));
        head.addView(tv("3 Orders", 22, 0xFF111111, true));
        head.addView(tv("Sample screen to test Vamsify. Not a real ride.", 12, 0xFF666666, false));
        root.addView(head);

        LinearLayout body = new LinearLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        // order tabs down the left, each with its own fare
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.VERTICAL);
        tabs.setBackgroundColor(Color.WHITE);
        tabs.setPadding(dp(8), dp(16), dp(8), dp(8));
        String[][] t = {{"₹30", "+₹3"}, {"₹30", "+₹3"}, {"₹30", "+₹13"}};
        for (String[] x : t) {
            TextView a = tv(x[0], 15, 0xFF111111, true), e = tv(x[1], 13, 0xFF1E8E4E, true);
            a.setGravity(Gravity.CENTER); e.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            tabs.addView(a, lp); tabs.addView(e, lp);
        }
        body.addView(tabs, new LinearLayout.LayoutParams(dp(60), -1));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(12), dp(12), dp(24));
        LinearLayout chips = new LinearLayout(this);
        TextView go = tv("Go To", 16, 0xFF111111, true), stay = tv("Stay In", 16, 0xFF333333, false);
        go.setPadding(dp(16), dp(8), dp(16), dp(8)); stay.setPadding(dp(16), dp(8), dp(16), dp(8));
        go.setBackground(box(0xFFFFC7CE, 20, 0)); stay.setBackground(box(Color.WHITE, 20, 0));
        chips.addView(go);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = dp(10);
        chips.addView(stay, sl);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(12);
        list.addView(chips, cp);
        list.addView(card("Bike Metro", "₹30 + ₹3", "1.6 km", "Cyber Towers - Broadridge Building, Hitech City Rd, Patrika Nagar, HITEC City, 500081",
            "2.1 km", "(5 mins)", "Jubilee Eclave Hitech City - RK Living, opposite AT Home Apartment, Kondapur, Whitefields, Gachibowli, Hyderabad, Telangana, India"));
        list.addView(card("Bike Metro", "₹30 + ₹3", "0.9 km", "Madhapur Silicon Valley - Gate A-Bus Stop (Towards Madhapur), Hitech City Metro Station, Madhapur, 500081",
            "1.6 km", "(5 mins)", "Siddhi Vinayak Nagar Madhapur - Srinivasam Classic, Siddhi Vinayak Nagar, Madhapur, Hyderabad, Telangana, India"));
        list.addView(card("Bike Metro", "₹30 + ₹13", "400 m", "Gachibowli Stadium - Road No 2, Gachibowli, Hyderabad, 500032",
            "3 km", "(9 mins)", "Kukatpally - LIG-556, Kukatpally Housing Board Colony, K P H B Phase 1, Hyderabad, Telangana 500072"));
        sv.addView(list);
        body.addView(sv, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); visible = true; }
    @Override protected void onPause() { super.onPause(); visible = false; }
}
