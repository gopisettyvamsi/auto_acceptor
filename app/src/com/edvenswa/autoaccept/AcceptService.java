package com.edvenswa.autoaccept;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcceptService extends AccessibilityService {
    static final Pattern FARE = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    static final Pattern KM = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*km", Pattern.CASE_INSENSITIVE);
    static final String[] LABELS = {"Accept", "Accept ride", "Accept order", "Accept trip"};
    static final String[] REJECT = {"Reject", "Decline", "Skip"};
    static final String ACTION_TOGGLE = "com.edvenswa.autoaccept.TOGGLE";
    static final int[] CHIP_BG = {0xFFB3261E, 0xFFFFC107, 0xFF1E8E4E}; // low, average, worth it
    static final int[] CHIP_FG = {Color.WHITE, 0xFF1B1B1B, Color.WHITE};
    static final String[] CHIP_NAME = {"Low", "Average", "Worth it"};

    static volatile AcceptService instance;
    static volatile int events;       // same process as the UI, so statics are visible to it
    static volatile String lastPkg = "none";
    static volatile boolean connected;

    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable hideChip = this::removeChip;
    long lastClick, lastRun;
    String lastKey = "", lastSkipKey = "", chipPos = "";
    TextView chipView;
    ImageView bubble;
    WindowManager.LayoutParams bubbleLp;

    // pause / resume from the notification action
    final BroadcastReceiver toggleRcv = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            SharedPreferences s = prefs();
            s.edit().putBoolean("on", !s.getBoolean("on", false)).apply();
        }
    };
    final SharedPreferences.OnSharedPreferenceChangeListener prefL = (s, k) -> {
        if ("on".equals(k) || "notif".equals(k) || "lang".equals(k)) refreshNote();
        if ("on".equals(k)) updateBubbleRing();
        if ("bubble".equals(k) && !s.getBoolean("bubble", true)) removeBubble();
    };

    SharedPreferences prefs() { return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE); }

    @Override protected void onServiceConnected() {
        connected = true; instance = this;
        IntentFilter f = new IntentFilter(ACTION_TOGGLE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(toggleRcv, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(toggleRcv, f);
        prefs().registerOnSharedPreferenceChangeListener(prefL);
        refreshNote();
        MainActivity.scheduleSummary(this);
    }

    @Override public boolean onUnbind(Intent i) {
        connected = false; instance = null;
        removeChip(); removeBubble();
        try { unregisterReceiver(toggleRcv); } catch (RuntimeException ex) { /* not registered */ }
        prefs().unregisterOnSharedPreferenceChangeListener(prefL);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1);
        return super.onUnbind(i);
    }

    @Override public void onInterrupt() { removeChip(); }

    void refreshNote() {
        SharedPreferences sp = prefs();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!sp.getBoolean("notif", true)) { nm.cancel(1); return; }
        boolean on = sp.getBoolean("on", false);
        PendingIntent toggle = PendingIntent.getBroadcast(this, 1,
            new Intent(ACTION_TOGGLE).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = MainActivity.noteBuilder(this).setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("Vamsify")
            .setContentText(I18n.t(sp, on ? "Auto-accept is on" : "Auto-accept is paused"))
            .setOngoing(true).setContentIntent(open)
            .addAction(R.drawable.ic_bolt, I18n.t(sp, on ? "Pause" : "Resume"), toggle).build();
        nm.notify(1, n);
    }

    // ponytail: first km on screen is assumed to be pickup, second is drop; adjust per app if layouts differ.
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        SharedPreferences sp = prefs();
        if (e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        events++; lastPkg = pkg; // diagnostics, shown via long-press on the app name

        // floating round bubble: visible over the driver app, hidden when another app comes to the front
        if (sp.getBoolean("bubble", true)) {
            if (sp.getString("pkgs", MainActivity.DEFAULT_PKGS).contains(pkg)) showBubble(sp);
            else if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !pkg.startsWith("com.android.systemui") && !pkg.contains("inputmethod") && !pkg.contains("keyboard")) removeBubble();
        }
        boolean auto = sp.getBoolean("on", false), showChip = sp.getBoolean("chip", true), reject = sp.getBoolean("reject", false);
        if (!auto && !showChip && !reject) { removeChip(); return; }
        if (!sp.getString("pkgs", MainActivity.DEFAULT_PKGS).contains(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - lastRun < 150) return; // throttle
        lastRun = now;

        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && pkg.contentEquals(r.getPackageName())) roots.add(r);
        }
        AccessibilityNodeInfo src = e.getSource(); // overlays: read the window that fired the event
        if (src != null) {
            AccessibilityNodeInfo top = src;
            for (AccessibilityNodeInfo p = src.getParent(); p != null; p = p.getParent()) top = p;
            roots.add(top);
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) roots.add(r);
        }
        if (roots.isEmpty()) return;

        StringBuilder text = new StringBuilder(), dump = new StringBuilder();
        for (AccessibilityNodeInfo r : roots) collect(r, text, dump);
        String t = text.toString();

        Double fare = first(FARE, t);
        List<Double> kms = all(KM, t);
        Double km = kms.size() > 0 ? kms.get(0) : null, drop = kms.size() > 1 ? kms.get(1) : null; // pickup, then drop
        AccessibilityNodeInfo target = findNode(roots, LABELS);
        boolean isOffer = fare != null && target != null; // a price and an Accept button = ride request

        if (!isOffer) {
            lastKey = ""; lastSkipKey = "";
            removeChip();
            sp.edit().putString("dump", "pkg=" + pkg + "\nresult=no ride request on screen\n\n" + dump).apply();
            return;
        }

        String type = MainActivity.rideType(sp, t);
        double pk = MainActivity.perKm(fare, km, drop);
        double dist = (km == null ? 0 : km) + (drop == null ? 0 : drop);
        double fuel = MainActivity.costPerKm(sp, type) * dist;
        int verdict = Double.isNaN(pk) ? -1 : MainActivity.verdict(pk, MainActivity.targetFor(sp, type));
        String key = fare + "|" + km + "|" + drop; // one alert / log entry per ride request, not per screen event

        String reason = auto ? decide(sp, fare, km, drop) : null; // plain-language reason it was not auto-accepted
        boolean accept = auto && reason == null && now - lastClick >= 4000;

        if (showChip) showChip(fare, pk, dist, fuel, type, verdict, reason);
        if (verdict == 2 && sp.getBoolean("alert", true) && !key.equals(lastKey)) alert();
        lastKey = key;
        if (reason != null && !key.equals(lastSkipKey)) { MainActivity.addSkip(sp, reason, fare); lastSkipKey = key; }

        sp.edit().putString("dump", "pkg=" + pkg + " fare=" + fare + " pickup=" + km + " drop=" + drop + " type=" + type
                + "\nresult=" + (accept ? "TAPPING" : "skipped: " + (reason == null ? "auto-accept off or cooling down" : reason))
                + "\n\n" + dump).apply();

        double near = MainActivity.pref(sp, "near", 0); // instant-accept radius: nearby pickups are tapped with no delay
        long delay = near > 0 && km != null && km <= near ? 0 : (long) MainActivity.pref(sp, "delay", 300);
        if (accept) {
            lastClick = now;
            MainActivity.addHistory(sp, pkg, fare, km, drop, type);
            h.postDelayed(() -> tap(target), delay);
        } else if (reject && verdict == 0 && now - lastClick >= 4000) {
            // optional: decline rides rated Low
            AccessibilityNodeInfo rj = findNode(roots, REJECT);
            if (rj != null) { lastClick = now; h.postDelayed(() -> tap(rj), delay); }
        }
    }

    // ---------- earnings chip (accessibility overlay: no extra permission needed) ----------
    void showChip(double fare, double pk, double dist, double fuel, String type, int verdict, String reason) {
        SharedPreferences sp = prefs();
        float d = getResources().getDisplayMetrics().density;
        String pos = sp.getString("chip_pos", "bottom");
        if (chipView != null && !pos.equals(chipPos)) removeChip();
        if (chipView == null) {
            chipPos = pos;
            chipView = new TextView(this);
            chipView.setPadding((int) (18 * d), (int) (12 * d), (int) (18 * d), (int) (12 * d));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
            boolean top = "top".equals(pos);
            lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) ((top ? 56 : 96) * d);
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(chipView, lp);
        }
        int fg = verdict < 0 ? Color.WHITE : CHIP_FG[verdict];
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(verdict < 0 ? 0xFF344054 : CHIP_BG[verdict]);
        bg.setCornerRadius(16 * d);
        chipView.setBackground(bg);
        chipView.setTextColor(fg);

        // big verdict, clear take-home, small detail
        String l1 = verdict < 0 ? "₹" + Math.round(fare)
            : I18n.t(sp, CHIP_NAME[verdict]) + "  ·  ₹" + String.format(Locale.US, "%.2f", pk) + "/km";
        String l2 = verdict < 0 ? I18n.t(sp, "Distance not shown on this request")
            : I18n.t(sp, "You keep") + " ₹" + Math.round(fare - fuel);
        String l3 = verdict < 0 ? "" : String.format(Locale.US, "%s ₹%d  ·  %s ₹%d  ·  %.1f km  ·  %s",
            I18n.t(sp, "Fare"), Math.round(fare), I18n.t(sp, "fuel"), Math.round(fuel), dist, I18n.t(sp, type));
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int a = 0;
        sb.append(l1); sb.setSpan(new StyleSpan(Typeface.BOLD), a, sb.length(), 0); sb.setSpan(new RelativeSizeSpan(1.7f), a, sb.length(), 0);
        sb.append('\n'); a = sb.length();
        sb.append(l2); sb.setSpan(new StyleSpan(Typeface.BOLD), a, sb.length(), 0); sb.setSpan(new RelativeSizeSpan(1.3f), a, sb.length(), 0);
        if (!l3.isEmpty()) {
            sb.append('\n'); a = sb.length();
            sb.append(l3); sb.setSpan(new RelativeSizeSpan(0.9f), a, sb.length(), 0);
        }
        if (reason != null) {
            sb.append('\n'); a = sb.length();
            sb.append(I18n.t(sp, "Not auto-accepted") + ": " + reason);
            sb.setSpan(new RelativeSizeSpan(0.9f), a, sb.length(), 0);
            sb.setSpan(new ForegroundColorSpan(fg), a, sb.length(), 0);
        }
        chipView.setText(sb);
        h.removeCallbacks(hideChip);
        h.postDelayed(hideChip, 20000); // safety: never leave it up if events stop
    }

    /** Shows a made-up offer so the user can see the chip without waiting for a real ride. */
    void showSample() {
        SharedPreferences sp = prefs();
        String type = sp.getString("vehicle", "Bike");
        double fare = 46, pickup = 3.6, drop = 3.6, dist = pickup + drop;
        double pk = MainActivity.perKm(fare, pickup, drop);
        showChip(fare, pk, dist, MainActivity.costPerKm(sp, type) * dist, type,
            MainActivity.verdict(pk, MainActivity.targetFor(sp, type)), null);
        h.removeCallbacks(hideChip);
        h.postDelayed(hideChip, 8000);
    }

    void removeChip() {
        h.removeCallbacks(hideChip);
        if (chipView != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(chipView); } catch (RuntimeException ex) { /* already gone */ }
            chipView = null;
        }
    }

    // ---------- floating bubble ----------
    void showBubble(SharedPreferences sp) {
        updateBubbleRing();
        if (bubble != null) return;
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (52 * d);
        bubble = new ImageView(this);
        bubble.setPadding((int) (5 * d), (int) (5 * d), (int) (5 * d), (int) (5 * d));
        try (java.io.InputStream in = getAssets().open("logo.png")) {
            bubble.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (java.io.IOException ex) { /* ring only */ }
        bubbleLp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = sp.getInt("bx", (int) (8 * d));
        bubbleLp.y = sp.getInt("by", (int) (220 * d));
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved; long down;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                float slop = 8 * v.getResources().getDisplayMetrics().density;
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = ev.getRawX(); sy = ev.getRawY(); ox = bubbleLp.x; oy = bubbleLp.y; moved = false; down = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(ev.getRawX() - sx) > slop || Math.abs(ev.getRawY() - sy) > slop) moved = true;
                        if (moved) {
                            bubbleLp.x = ox + (int) (ev.getRawX() - sx); bubbleLp.y = oy + (int) (ev.getRawY() - sy);
                            wm.updateViewLayout(v, bubbleLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        SharedPreferences s = prefs();
                        if (moved) s.edit().putInt("bx", bubbleLp.x).putInt("by", bubbleLp.y).apply();
                        else if (System.currentTimeMillis() - down > 600)
                            startActivity(new Intent(AcceptService.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        else {
                            boolean on = !s.getBoolean("on", false);
                            s.edit().putBoolean("on", on).apply();
                            Toast.makeText(AcceptService.this, I18n.t(s, on ? "Auto-accept is on" : "Auto-accept is paused"), Toast.LENGTH_SHORT).show();
                        }
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bubbleLp);
        updateBubbleRing();
    }

    /** Green ring = auto-accept on, grey ring = paused. */
    void updateBubbleRing() {
        if (bubble == null) return;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.WHITE);
        g.setStroke((int) (3 * d), prefs().getBoolean("on", false) ? 0xFF1E8E4E : 0xFF98A2B3);
        bubble.setBackground(g);
    }

    void removeBubble() {
        if (bubble != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(bubble); } catch (RuntimeException ex) { /* already gone */ }
            bubble = null;
        }
    }

    void alert() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.vibrate(new long[]{0, 120, 80, 120}, -1);
        try { new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_ACK, 200); }
        catch (RuntimeException ex) { /* no audio available */ }
    }

    // ---------- matching ----------
    static List<Double> all(Pattern p, String s) {
        List<Double> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) out.add(Double.valueOf(m.group(1)));
        return out;
    }

    /** Returns a plain-language reason the ride does not meet the filters, or null if it does. */
    static String decide(SharedPreferences sp, Double fare, Double km, Double drop) {
        double minFare = MainActivity.pref(sp, "fare", 0);
        if (fare < minFare)
            return String.format(Locale.US, I18n.t(sp, "Fare ₹%s is below your minimum of ₹%s"), MainActivity.n(fare), MainActivity.n(minFare));
        double maxKm = MainActivity.pref(sp, "dist", 0);
        if (maxKm > 0) {
            if (km == null) return I18n.t(sp, "Pickup distance not shown, so it can't be checked");
            if (km > maxKm)
                return String.format(Locale.US, I18n.t(sp, "Pickup %s km is farther than your limit of %s km"), MainActivity.n(km), MainActivity.n(maxKm));
        }
        double minPk = MainActivity.pref(sp, "perkm", 0);
        if (minPk > 0) {
            double pk = MainActivity.perKm(fare, km, drop);
            if (Double.isNaN(pk)) return I18n.t(sp, "Distance not shown, so earnings per km can't be checked");
            if (pk < minPk)
                return String.format(Locale.US, I18n.t(sp, "Pays ₹%s per km, below your minimum of ₹%s"), String.format(Locale.US, "%.1f", pk), MainActivity.n(minPk));
        }
        return null;
    }

    AccessibilityNodeInfo findNode(List<AccessibilityNodeInfo> roots, String[] labels) {
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo n = walk(root, labels);
            if (n != null) return n;
        }
        return null;
    }

    // own tree walk: findAccessibilityNodeInfosByText returns nothing on Rapido's overlay
    static AccessibilityNodeInfo walk(AccessibilityNodeInfo n, String[] labels) {
        if (n == null) return null;
        for (CharSequence s : new CharSequence[]{n.getText(), n.getContentDescription()})
            if (s != null) for (String l : labels) if (s.toString().trim().equalsIgnoreCase(l)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo f = walk(n.getChild(i), labels);
            if (f != null) return f;
        }
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
}
