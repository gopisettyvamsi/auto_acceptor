package com.edvenswa.autoaccept;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcceptService extends AccessibilityService {
    static final Pattern FARE = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    static final Pattern KM = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*km", Pattern.CASE_INSENSITIVE);
    static final String[] LABELS = {"Accept", "Accept ride", "Accept order", "Accept trip"};

    final Handler h = new Handler(Looper.getMainLooper());
    long lastClick, lastRun;

    // ponytail: first km on screen is assumed to be pickup distance; adjust per app if layouts differ.
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        if (!sp.getBoolean("on", false) || e.getPackageName() == null) return;
        if (!sp.getString("pkgs", MainActivity.DEFAULT_PKGS).contains(e.getPackageName())) return;
        long now = System.currentTimeMillis();
        if (now - lastRun < 150 || now - lastClick < 4000) return; // throttle + cooldown after a tap
        lastRun = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        collect(root, sb);
        String text = sb.toString();

        Double fare = first(FARE, text), km = first(KM, text);
        if (fare == null) return; // no price on screen = not a ride offer (avoids tapping "Accept terms")
        if (fare < num(sp, "fare")) return;
        double maxKm = num(sp, "dist");
        if (maxKm > 0 && (km == null || km > maxKm)) return;

        for (String label : LABELS) {
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(label);
            for (AccessibilityNodeInfo n : hits) {
                for (AccessibilityNodeInfo c = n; c != null; c = c.getParent()) {
                    if (c.isClickable()) {
                        final AccessibilityNodeInfo target = c;
                        lastClick = now;
                        h.postDelayed(() -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK), (long) num(sp, "delay"));
                        return;
                    }
                }
            }
        }
    }

    static void collect(AccessibilityNodeInfo n, StringBuilder sb) {
        if (n == null) return;
        if (n.getText() != null) sb.append(n.getText()).append('\n');
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), sb);
    }

    static Double first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    static double num(SharedPreferences sp, String k) {
        try { return Double.parseDouble(sp.getString(k, "0")); } catch (Exception e) { return 0; }
    }

    @Override public void onInterrupt() {}
}
