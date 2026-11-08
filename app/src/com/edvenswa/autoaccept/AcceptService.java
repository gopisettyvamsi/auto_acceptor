package com.edvenswa.autoaccept;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
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
        String pkg = e.getPackageName().toString();
        if (!sp.getString("pkgs", MainActivity.DEFAULT_PKGS).contains(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - lastRun < 150 || now - lastClick < 4000) return; // throttle + cooldown after a tap
        lastRun = now;

        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && pkg.contentEquals(r.getPackageName())) roots.add(r);
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) roots.add(r);
        }
        if (roots.isEmpty()) return;

        StringBuilder text = new StringBuilder(), dump = new StringBuilder();
        for (AccessibilityNodeInfo r : roots) collect(r, text, dump);
        String t = text.toString();

        Double fare = first(FARE, t), km = first(KM, t);
        String why = decide(sp, fare, km);
        AccessibilityNodeInfo target = why == null ? findAccept(roots) : null;
        if (why == null && target == null) why = "no Accept node found";
        sp.edit().putString("dump", "pkg=" + pkg + " fare=" + fare + " km=" + km + "\nresult="
                + (why == null ? "TAPPING" : "skipped: " + why) + "\n\n" + dump).apply();
        if (why != null) return;

        lastClick = now;
        final AccessibilityNodeInfo tgt = target;
        h.postDelayed(() -> tap(tgt), (long) num(sp, "delay"));
    }

    static String decide(SharedPreferences sp, Double fare, Double km) {
        if (fare == null) return "no price (Rs) on screen"; // avoids tapping "Accept terms"
        if (fare < num(sp, "fare")) return "fare " + fare + " below minimum";
        double maxKm = num(sp, "dist");
        if (maxKm > 0 && (km == null || km > maxKm)) return "pickup km " + km + " over max";
        return null;
    }

    AccessibilityNodeInfo findAccept(List<AccessibilityNodeInfo> roots) {
        for (AccessibilityNodeInfo root : roots)
            for (String label : LABELS)
                for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(label))
                    return n;
        return null;
    }

    void tap(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo c = n; c != null; c = c.getParent())
            if (c.isClickable() && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        // fallback: real touch at the button's centre (works for custom-drawn buttons)
        Rect b = new Rect(); n.getBoundsInScreen(b);
        Path p = new Path(); p.moveTo(b.exactCenterX(), b.exactCenterY());
        dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 80)).build(), null, null);
    }

    static void collect(AccessibilityNodeInfo n, StringBuilder text, StringBuilder dump) {
        if (n == null) return;
        CharSequence tx = n.getText(), d = n.getContentDescription();
        if (tx != null) text.append(tx).append('\n');
        if (d != null) text.append(d).append('\n');
        if (tx != null || d != null || n.isClickable())
            dump.append(n.getClassName()).append(" | ").append(tx).append(" | ").append(d)
                .append(n.isClickable() ? " | clickable" : "").append('\n');
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), text, dump);
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
