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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcceptService extends AccessibilityService {
    static final Pattern FARE = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    static final Pattern KM = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*km", Pattern.CASE_INSENSITIVE);
    // a distance on its own short line: "0.9 km" or "400 m" (meters are converted to km)
    static final Pattern DIST_LINE = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(km|m)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern MINS = Pattern.compile("([0-9]+)\\s*min", Pattern.CASE_INSENSITIVE);
    static final String[] LABELS = {"Accept", "Accept ride", "Accept order", "Accept trip"};
    static final String[] REJECT = {"Reject", "Decline", "Skip"};
    static final String ACTION_TOGGLE = "com.edvenswa.autoaccept.TOGGLE";
    static final int[] CHIP_BG = {0xFFB3261E, 0xFFFFC107, 0xFF1E8E4E}; // low, average, worth it
    static final int[] CHIP_FG = {Color.WHITE, 0xFF1B1B1B, Color.WHITE};
    static final String[] CHIP_NAME = {"Low", "Average", "Worth it"};

    static {
        I18n.a("Pickup %s is farther than your limit of %s", "पिकअप %s आपकी सीमा %s से अधिक दूर है", "పిక్‌అప్ %s మీ పరిమితి %s కంటే ఎక్కువ దూరం");
        I18n.a("Drop %s is farther than your limit of %s", "ड्रॉप %s आपकी सीमा %s से अधिक दूर है", "డ్రాప్ %s మీ పరిమితి %s కంటే ఎక్కువ దూరం");
        I18n.a("Drop distance not shown, so it can't be checked", "ड्रॉप दूरी नहीं दिखी, इसलिए जाँच नहीं हो सकती", "డ్రాప్ దూరం చూపలేదు, అందువల్ల తనిఖీ చేయలేము");
        I18n.a("No distance", "दूरी नहीं", "దూరం లేదు");
        I18n.a("Keep", "बचत", "మిగులు");
        I18n.a("Ride accepted", "राइड स्वीकार की गई", "రైడ్ అంగీకరించబడింది");
        I18n.a("Pickup", "पिकअप", "పికప్");
        I18n.a("Drop", "ड्रॉप", "డ్రాప్");
        I18n.a("away", "दूर", "దూరంలో");
        I18n.a("min", "मिनट", "నిమిషాలు");
        I18n.a("Navigate to drop", "ड्रॉप तक नेविगेट करें", "డ్రాప్‌కు నావిగేట్ చేయండి");
        I18n.a("OK", "ठीक है", "సరే");
        I18n.a("Sample pickup street, Gachibowli", "नमूना पिकअप सड़क, गचिबोवली", "నమూనా పికప్ వీధి, గచ్చిబొవ్లి");
        I18n.a("Sample drop point, Hitech City", "नमूना ड्रॉप पॉइंट, हाइटेक सिटी", "నమూనా డ్రాప్ పాయింట్, హైటెక్ సిటీ");
    }

    static volatile AcceptService instance;
    static volatile int events;       // same process as the UI, so statics are visible to it
    static volatile String lastPkg = "none";
    static volatile boolean connected;

    /** One ride request card on the driver app's screen. */
    static class Card {
        AccessibilityNodeInfo accept, node;
        Rect acceptBounds = new Rect(), fareBounds = new Rect();
        String text = "", pickupAddr = "", dropAddr = "", type = "Bike", key = "", reason;
        double fare, perKm, dist, fuel;
        Double pickup, drop;
        int mins = -1, verdict = -1;
    }

    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable hideChips = this::removeChips;
    final Runnable hideTrip = this::removeTrip;
    long lastClick, lastRun, autoTapUntil;
    final Set<String> alerted = new HashSet<>(), skipped = new HashSet<>();
    List<Card> lastCards = new ArrayList<>();
    final List<ChipView> chips = new ArrayList<>();
    final List<int[]> chipBase = new ArrayList<>(); // anchor of each chip before the user's drag offset
    int chipDx, chipDy;
    LinearLayout tripView;
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
    float density() { return getResources().getDisplayMetrics().density; }
    WindowManager wm() { return (WindowManager) getSystemService(WINDOW_SERVICE); }

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
        removeChips(); removeBubble(); removeTrip();
        try { unregisterReceiver(toggleRcv); } catch (RuntimeException ex) { /* not registered */ }
        prefs().unregisterOnSharedPreferenceChangeListener(prefL);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1);
        return super.onUnbind(i);
    }

    @Override public void onInterrupt() { removeChips(); }

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

    // =====================================================================
    // event handling
    // =====================================================================
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        SharedPreferences sp = prefs();
        if (e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        events++; lastPkg = pkg; // diagnostics, shown via long-press on the app name
        boolean watched = sp.getString("pkgs", MainActivity.DEFAULT_PKGS).contains(pkg);

        // floating round bubble: visible over the driver app, hidden when another app comes to the front
        if (sp.getBoolean("bubble", true)) {
            if (watched) showBubble(sp);
            else if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !pkg.startsWith("com.android.systemui") && !pkg.contains("inputmethod") && !pkg.contains("keyboard")) removeBubble();
        }
        boolean auto = sp.getBoolean("on", false), showChip = sp.getBoolean("chip", true), reject = sp.getBoolean("reject", false);
        boolean trip = sp.getBoolean("trip", true);
        if (!auto && !showChip && !reject && !trip) { removeChips(); return; }
        if (!watched) return;
        if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) { onClick(e, sp, pkg); return; }
        // throttle bursts of events, but always process the final one so the chip never shows a previous order
        if (System.currentTimeMillis() - lastRun < 150) { trailPkg = pkg; h.removeCallbacks(trail); h.postDelayed(trail, 180); return; }
        analyze(pkg, e.getSource());
    }

    String trailPkg = "";
    final Runnable trail = () -> analyze(trailPkg, null);

    void analyze(String pkg, AccessibilityNodeInfo src) {
        SharedPreferences sp = prefs();
        boolean auto = sp.getBoolean("on", false), showChip = sp.getBoolean("chip", true), reject = sp.getBoolean("reject", false);
        boolean trip = sp.getBoolean("trip", true);
        long now = System.currentTimeMillis();
        lastRun = now;
        h.removeCallbacks(trail);

        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && pkg.contentEquals(r.getPackageName())) roots.add(r);
        }
        if (src != null) { // overlays: read the window that fired the event
            AccessibilityNodeInfo top = src;
            for (AccessibilityNodeInfo p = src.getParent(); p != null; p = p.getParent()) top = p;
            roots.add(top);
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) roots.add(r);
        }
        if (roots.isEmpty()) return;

        // every Accept button on screen = one ride request card each
        StringBuilder text = new StringBuilder(), dump = new StringBuilder();
        List<AccessibilityNodeInfo> accepts = new ArrayList<>();
        for (AccessibilityNodeInfo r : roots) { collect(r, text, dump); findAll(r, LABELS, accepts); }
        List<Card> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AccessibilityNodeInfo a : accepts) {
            Rect r = new Rect();
            a.getBoundsInScreen(r);
            if (!seen.add(r.toShortString())) continue; // same window reached twice
            Card c = buildCard(a, sp);
            if (c != null) { c.acceptBounds = r; cards.add(c); }
        }
        lastCards = cards;

        if (cards.isEmpty()) {
            alerted.clear(); skipped.clear();
            removeChips();
            sp.edit().putString("dump", "pkg=" + pkg + "\nresult=no ride request on screen\n\n" + dump).apply();
            return;
        }

        StringBuilder summary = new StringBuilder();
        for (Card c : cards) {
            double dist = (c.pickup == null ? 0 : c.pickup) + (c.drop == null ? 0 : c.drop);
            c.type = MainActivity.rideType(sp, c.text);
            c.perKm = MainActivity.perKm(c.fare, c.pickup, c.drop);
            c.dist = dist;
            c.fuel = MainActivity.costPerKm(sp, c.type) * dist;
            c.verdict = Double.isNaN(c.perKm) ? -1 : MainActivity.verdict(c.perKm, MainActivity.targetFor(sp, c.type));
            c.key = c.fare + "|" + c.pickup + "|" + c.drop;
            c.reason = auto ? decide(sp, c.fare, c.pickup, c.drop) : null;
            summary.append("card fare=").append(c.fare).append(" pickup=").append(c.pickup).append(" drop=").append(c.drop)
                .append(" mins=").append(c.mins).append(" type=").append(c.type)
                .append(" reason=").append(c.reason).append('\n');
        }
        sp.edit().putString("dump", "pkg=" + pkg + " cards=" + cards.size() + "\n" + summary + "\n" + dump).apply();

        if (showChip) showChips(cards); else removeChips();

        for (Card c : cards) {
            if (c.verdict == 2 && sp.getBoolean("alert", true) && alerted.add(c.key)) alert();
            if (c.reason != null && skipped.add(c.key)) MainActivity.addSkip(sp, c.reason, c.fare);
        }

        // auto-accept: of the cards that pass the filters, take the one with the nearest pickup
        Card pick = null;
        if (auto && now - lastClick >= 4000)
            for (Card c : cards)
                if (c.reason == null && (pick == null || nearer(c, pick))) pick = c;
        double near = MainActivity.pref(sp, "near", 0); // instant-accept radius: nearby pickups are tapped with no delay
        if (pick != null) {
            final Card chosen = pick;
            long delay = near > 0 && chosen.pickup != null && chosen.pickup <= near ? 0 : (long) MainActivity.pref(sp, "delay", 300);
            lastClick = now; autoTapUntil = now + delay + 2500;
            MainActivity.addHistory(sp, pkg, chosen.fare, chosen.pickup, chosen.drop, chosen.type);
            h.postDelayed(() -> tap(chosen.accept), delay);
            if (trip) h.postDelayed(() -> showTrip(chosen), delay + 700);
        } else if (reject && now - lastClick >= 4000) {
            // optional: decline a card rated Low (only when nothing was accepted)
            for (Card c : cards) {
                if (c.verdict != 0) continue;
                List<AccessibilityNodeInfo> rj = new ArrayList<>();
                findAll(c.node, REJECT, rj);
                if (!rj.isEmpty()) {
                    lastClick = now;
                    h.postDelayed(() -> tap(rj.get(0)), (long) MainActivity.pref(sp, "delay", 300));
                    break;
                }
            }
        }
    }

    static boolean nearer(Card a, Card b) {
        return (a.pickup == null ? 1e9 : a.pickup) < (b.pickup == null ? 1e9 : b.pickup);
    }

    /** The rider tapped Accept themselves: log the ride and show the trip details. */
    void onClick(AccessibilityEvent e, SharedPreferences sp, String pkg) {
        AccessibilityNodeInfo src = e.getSource();
        if (src == null || walk(src, LABELS) == null) return; // not an Accept button
        long now = System.currentTimeMillis();
        if (now < autoTapUntil) return; // our own tap, already handled
        Rect r = new Rect();
        src.getBoundsInScreen(r);
        int slack = (int) (40 * density());
        for (Card c : lastCards) {
            Rect a = new Rect(c.acceptBounds);
            a.inset(-slack, -slack);
            if (a.contains(r.centerX(), r.centerY())) {
                lastClick = now;
                MainActivity.addHistory(sp, pkg, c.fare, c.pickup, c.drop, c.type);
                if (sp.getBoolean("trip", true)) showTrip(c);
                return;
            }
        }
    }

    // =====================================================================
    // reading a card
    // =====================================================================
    /** Climbs from an Accept button to the smallest container that holds one fare and a distance (the card). */
    Card buildCard(AccessibilityNodeInfo accept, SharedPreferences sp) {
        for (AccessibilityNodeInfo p = accept.getParent(); p != null; p = p.getParent()) {
            if (countAccept(p) > 1) return null; // reached the list holding several cards
            List<String> lines = new ArrayList<>();
            gather(p, lines);
            String t = TextUtils.join("\n", lines);
            Double fare = first(FARE, t);
            List<Double> kms = distances(lines, t);
            if (fare == null || kms.isEmpty()) continue;

            Card c = new Card();
            c.accept = accept; c.node = p; c.text = t; c.fare = fare;
            c.pickup = kms.get(0);
            c.drop = kms.size() > 1 ? kms.get(1) : null;
            AccessibilityNodeInfo fn = fareNode(p);
            if (fn != null) fn.getBoundsInScreen(c.fareBounds);

            // pickup and drop places, and the drop duration, from the card's text lines
            int ik = -1, ik2 = -1;
            for (int i = 0; i < lines.size(); i++) {
                String s = lines.get(i);
                if (s.length() <= 24 && DIST_LINE.matcher(s).find()) {
                    if (ik < 0) ik = i; else { ik2 = i; break; }
                }
            }
            if (ik >= 0) c.pickupAddr = place(lines, ik + 1, ik2 < 0 ? lines.size() : ik2);
            if (ik2 >= 0) {
                c.dropAddr = place(lines, ik2 + 1, lines.size());
                for (int i = ik2; i < Math.min(lines.size(), ik2 + 4); i++) {
                    Matcher m = MINS.matcher(lines.get(i));
                    if (m.find()) { c.mins = Integer.parseInt(m.group(1)); break; }
                }
            }
            return c;
        }
        return null;
    }

    /** Distances in km from the card's short text lines ("0.9 km", "400 m"); falls back to any "x km" in the text. */
    static List<Double> distances(List<String> lines, String text) {
        List<Double> out = new ArrayList<>();
        for (String s : lines) {
            if (s.length() > 24) continue;
            Matcher m = DIST_LINE.matcher(s);
            if (m.find()) {
                double v = Double.parseDouble(m.group(1));
                out.add(m.group(2).equalsIgnoreCase("m") ? v / 1000 : v);
            }
        }
        return out.isEmpty() ? all(KM, text) : out;
    }

    /** First reasonably long text line in [from, to): a street or place name, not a distance or button. */
    static String place(List<String> lines, int from, int to) {
        for (int i = from; i < Math.min(to, lines.size()); i++) {
            String s = lines.get(i).trim();
            if (s.length() > 10 && !DIST_LINE.matcher(s).find() && !MINS.matcher(s).find() && !FARE.matcher(s).find())
                return s.length() > 90 ? s.substring(0, 90) + "…" : s;
        }
        return "";
    }

    static void gather(AccessibilityNodeInfo n, List<String> lines) {
        if (n == null) return;
        if (n.getText() != null) lines.add(n.getText().toString());
        if (n.getContentDescription() != null) lines.add(n.getContentDescription().toString());
        for (int i = 0; i < n.getChildCount(); i++) gather(n.getChild(i), lines);
    }

    static AccessibilityNodeInfo fareNode(AccessibilityNodeInfo n) {
        if (n == null) return null;
        CharSequence s = n.getText();
        if (s != null && s.length() < 14 && FARE.matcher(s).find()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo f = fareNode(n.getChild(i));
            if (f != null) return f;
        }
        return null;
    }

    static int countAccept(AccessibilityNodeInfo n) {
        if (n == null) return 0;
        int c = 0;
        for (CharSequence s : new CharSequence[]{n.getText(), n.getContentDescription()})
            if (s != null) for (String l : LABELS) if (s.toString().trim().equalsIgnoreCase(l)) { c++; break; }
        for (int i = 0; i < n.getChildCount(); i++) c += countAccept(n.getChild(i));
        return c;
    }

    // =====================================================================
    // earnings chips: one small chip per card, beside the fare, draggable
    // =====================================================================
    void showChips(List<Card> cards) {
        SharedPreferences sp = prefs();
        float d = density();
        List<int[]> pos = new ArrayList<>();
        List<Card> shown = new ArrayList<>();
        for (Card c : cards) {
            if (c.node != null && !c.node.isVisibleToUser()) continue;
            int x, y;
            if (!c.fareBounds.isEmpty()) { x = c.fareBounds.right + (int) (10 * d); y = c.fareBounds.top - (int) (2 * d); }
            else { x = c.acceptBounds.left; y = c.acceptBounds.top - (int) (46 * d); }
            pos.add(new int[]{x, y});
            shown.add(c);
        }
        render(sp, shown, pos);
    }

    Bitmap logoBmp;

    Bitmap logo() {
        if (logoBmp == null)
            try (java.io.InputStream in = getAssets().open("logo.png")) { logoBmp = BitmapFactory.decodeStream(in); }
            catch (java.io.IOException ex) { /* chip just has no logo */ }
        return logoBmp;
    }

    /** Small dark pill like Rapido's own chip: logo, big earnings per km, and two tiny lines (rating, what you keep). */
    class ChipView extends LinearLayout {
        final TextView main, verdict, keep;

        ChipView() {
            super(AcceptService.this);
            float d = density();
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding((int) (5 * d), (int) (4 * d), (int) (12 * d), (int) (4 * d));
            ImageView logoView = new ImageView(AcceptService.this);
            if (logo() != null) logoView.setImageBitmap(logoBmp);
            addView(logoView, new LinearLayout.LayoutParams((int) (22 * d), (int) (22 * d)));
            main = new TextView(AcceptService.this);
            main.setTextSize(15); main.setTextColor(Color.WHITE); main.setTypeface(Typeface.DEFAULT_BOLD);
            main.setPadding((int) (7 * d), 0, (int) (9 * d), 0);
            addView(main);
            LinearLayout col = new LinearLayout(AcceptService.this);
            col.setOrientation(VERTICAL);
            verdict = new TextView(AcceptService.this);
            verdict.setTextSize(9.5f);
            keep = new TextView(AcceptService.this);
            keep.setTextSize(11); keep.setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(verdict); col.addView(keep);
            addView(col);
        }

        void bind(Card c, SharedPreferences sp) {
            float d = density();
            int[] ring = {0xFFEF5350, 0xFFFFC107, 0xFF2EAD5B};      // low, average, worth it
            int[] word = {0xFFFF8A80, 0xFFFFD54F, 0xFF5BD68A};
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF2121212); bg.setCornerRadius(20 * d);
            bg.setStroke((int) (1.5f * d), c.verdict < 0 ? 0xFF98A2B3 : ring[c.verdict]);
            setBackground(bg);
            if (c.verdict < 0) {
                main.setText("₹" + Math.round(c.fare));
                verdict.setText(I18n.t(sp, "No distance")); verdict.setTextColor(0xFFCBD5E1);
                keep.setText("");
                return;
            }
            main.setText("₹" + String.format(Locale.US, "%.2f", c.perKm) + "/km");
            verdict.setText(I18n.t(sp, CHIP_NAME[c.verdict]) + (c.pickup == null ? "" : "  ·  " + MainActivity.fmtDist(sp, c.pickup)));
            verdict.setTextColor(word[c.verdict]);
            double kept = c.fare - c.fuel;
            keep.setText(I18n.t(sp, "Keep") + " ₹" + Math.round(kept));
            keep.setTextColor(kept >= 0 ? 0xFF5BD68A : 0xFFFF8A80);
        }
    }

    void render(SharedPreferences sp, List<Card> cs, List<int[]> pos) {
        float d = density();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        if (chips.isEmpty()) { chipDx = sp.getInt("chip_dx", 0); chipDy = sp.getInt("chip_dy", 0); }
        while (chips.size() > cs.size()) { removeChipView(chips.remove(chips.size() - 1)); chipBase.remove(chipBase.size() - 1); }
        while (chips.size() < cs.size()) { chips.add(newChip()); chipBase.add(new int[2]); }
        for (int i = 0; i < cs.size(); i++) {
            Card c = cs.get(i);
            chips.get(i).bind(c, sp);
            chipBase.get(i)[0] = pos.get(i)[0]; chipBase.get(i)[1] = pos.get(i)[1];
        }
        relayoutChips(dm);
        h.removeCallbacks(hideChips);
        h.postDelayed(hideChips, 20000); // safety: never leave chips up if events stop
    }

    void relayoutChips(DisplayMetrics dm) {
        for (int i = 0; i < chips.size(); i++) {
            ChipView t = chips.get(i);
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) t.getLayoutParams();
            int w = t.getWidth() > 0 ? t.getWidth() : (int) (110 * density());
            lp.x = Math.max(0, Math.min(dm.widthPixels - w, chipBase.get(i)[0] + chipDx));
            lp.y = Math.max(0, chipBase.get(i)[1] + chipDy);
            try { wm().updateViewLayout(t, lp); } catch (RuntimeException ex) { /* removed meanwhile */ }
        }
    }

    ChipView newChip() {
        ChipView t = new ChipView();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // drag any chip: all chips move together and the offset is remembered
        t.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN: sx = ev.getRawX(); sy = ev.getRawY(); ox = chipDx; oy = chipDy; return true;
                    case MotionEvent.ACTION_MOVE:
                        chipDx = ox + (int) (ev.getRawX() - sx); chipDy = oy + (int) (ev.getRawY() - sy);
                        relayoutChips(getResources().getDisplayMetrics());
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs().edit().putInt("chip_dx", chipDx).putInt("chip_dy", chipDy).apply();
                        return true;
                }
                return false;
            }
        });
        wm().addView(t, lp);
        return t;
    }

    void removeChipView(View t) {
        try { wm().removeView(t); } catch (RuntimeException ex) { /* already gone */ }
    }

    void removeChips() {
        h.removeCallbacks(hideChips);
        for (ChipView t : chips) removeChipView(t);
        chips.clear(); chipBase.clear();
    }

    /** Shows a made-up offer (chip, then the trip details) so the user can see both without a real ride. */
    void showSample() {
        SharedPreferences sp = prefs();
        Card c = new Card();
        c.type = sp.getString("vehicle", "Bike");
        c.fare = 46; c.pickup = 0.9; c.drop = 5.1; c.mins = 12;
        c.pickupAddr = I18n.t(sp, "Sample pickup street, Gachibowli");
        c.dropAddr = I18n.t(sp, "Sample drop point, Hitech City");
        c.dist = 6.0; c.perKm = MainActivity.perKm(c.fare, c.pickup, c.drop);
        c.fuel = MainActivity.costPerKm(sp, c.type) * c.dist;
        c.verdict = MainActivity.verdict(c.perKm, MainActivity.targetFor(sp, c.type));
        DisplayMetrics dm = getResources().getDisplayMetrics();
        List<Card> one = new ArrayList<>();
        one.add(c);
        List<int[]> pos = new ArrayList<>();
        pos.add(new int[]{dm.widthPixels / 3, dm.heightPixels / 3});
        render(sp, one, pos);
        h.removeCallbacks(hideChips);
        h.postDelayed(hideChips, 8000);
        h.postDelayed(() -> showTrip(c), 2500);
    }

    // =====================================================================
    // trip details after accepting
    // =====================================================================
    void showTrip(Card c) {
        removeTrip();
        SharedPreferences sp = prefs();
        float d = density();
        DisplayMetrics dm = getResources().getDisplayMetrics();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (20 * d), (int) (18 * d), (int) (20 * d), (int) (16 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(20 * d); bg.setStroke((int) d, 0xFFE6E8EE);
        box.setBackground(bg);

        TextView title = text(I18n.t(sp, "Ride accepted"), 20, 0xFF14171F, true);
        box.addView(title);
        if (c.verdict >= 0) {
            TextView chip = text(I18n.t(sp, CHIP_NAME[c.verdict]) + "  ·  ₹" + String.format(Locale.US, "%.2f", c.perKm) + "/km", 13, CHIP_FG[c.verdict], true);
            GradientDrawable cb = new GradientDrawable();
            cb.setColor(CHIP_BG[c.verdict]); cb.setCornerRadius(12 * d);
            chip.setBackground(cb);
            chip.setPadding((int) (10 * d), (int) (3 * d), (int) (10 * d), (int) (3 * d));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.topMargin = (int) (8 * d);
            box.addView(chip, cl);
        }

        info(box, I18n.t(sp, "Pickup"), c.pickupAddr.isEmpty() ? "–" : c.pickupAddr,
            c.pickup == null ? "" : MainActivity.fmtDist(sp, c.pickup) + " " + I18n.t(sp, "away"));
        String dropSub = (c.drop == null ? "" : MainActivity.fmtDist(sp, c.drop)) + (c.mins >= 0 ? "  ·  " + c.mins + " " + I18n.t(sp, "min") : "");
        info(box, I18n.t(sp, "Drop"), c.dropAddr.isEmpty() ? "–" : c.dropAddr, dropSub);

        TextView money = text(I18n.t(sp, "Fare") + " ₹" + Math.round(c.fare) + "  ·  " + I18n.t(sp, "fuel") + " ₹" + Math.round(c.fuel)
            + "  ·  " + I18n.t(sp, "You keep") + " ₹" + Math.round(c.fare - c.fuel), 14, 0xFF14171F, true);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = (int) (16 * d);
        box.addView(money, ml);

        LinearLayout buttons = new LinearLayout(this);
        TextView nav = button(I18n.t(sp, "Navigate to drop"), false);
        TextView ok = button(I18n.t(sp, "OK"), true);
        final String dropQuery = c.dropAddr;
        nav.setOnClickListener(v -> {
            if (!dropQuery.isEmpty()) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(dropQuery))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (RuntimeException ex) { Toast.makeText(this, "No maps app found", Toast.LENGTH_SHORT).show(); }
            }
            removeTrip();
        });
        ok.setOnClickListener(v -> removeTrip());
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, (int) (46 * d), 1);
        b1.rightMargin = (int) (8 * d);
        buttons.addView(nav, b1);
        buttons.addView(ok, new LinearLayout.LayoutParams(0, (int) (46 * d), 1));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = (int) (18 * d);
        box.addView(buttons, bl);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams((int) (dm.widthPixels * 0.9f), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        tripView = box;
        wm().addView(box, lp);
        h.removeCallbacks(hideTrip);
        h.postDelayed(hideTrip, 90000); // safety: never block the screen for good
    }

    void removeTrip() {
        h.removeCallbacks(hideTrip);
        if (tripView != null) {
            try { wm().removeView(tripView); } catch (RuntimeException ex) { /* already gone */ }
            tripView = null;
        }
    }

    TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    void info(LinearLayout box, String label, String main, String sub) {
        float d = density();
        TextView l = text(label.toUpperCase(), 11, 0xFF667085, true);
        l.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = (int) (16 * d);
        box.addView(l, ll);
        box.addView(text(main, 15, 0xFF14171F, true));
        if (!sub.isEmpty()) box.addView(text(sub, 13, 0xFF667085, false));
    }

    TextView button(String s, boolean primary) {
        float d = density();
        TextView t = text(s, 14, primary ? Color.WHITE : 0xFF1F4FD8, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * d);
        g.setColor(primary ? 0xFF1F4FD8 : Color.WHITE);
        if (!primary) g.setStroke((int) d, 0xFF1F4FD8);
        t.setBackground(g);
        return t;
    }

    // =====================================================================
    // floating bubble
    // =====================================================================
    void showBubble(SharedPreferences sp) {
        updateBubbleRing();
        if (bubble != null) return;
        float d = density();
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
        final WindowManager wm = wm();
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
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.WHITE);
        g.setStroke((int) (3 * density()), prefs().getBoolean("on", false) ? 0xFF1E8E4E : 0xFF98A2B3);
        bubble.setBackground(g);
    }

    void removeBubble() {
        if (bubble != null) {
            try { wm().removeView(bubble); } catch (RuntimeException ex) { /* already gone */ }
            bubble = null;
        }
    }

    void alert() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.vibrate(new long[]{0, 120, 80, 120}, -1);
        try { new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_ACK, 200); }
        catch (RuntimeException ex) { /* no audio available */ }
    }

    // =====================================================================
    // matching helpers
    // =====================================================================
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
                return String.format(Locale.US, I18n.t(sp, "Pickup %s is farther than your limit of %s"), MainActivity.fmtDist(sp, km), MainActivity.fmtDist(sp, maxKm));
        }
        double maxDrop = MainActivity.pref(sp, "dropmax", 0);
        if (maxDrop > 0) {
            if (drop == null) return I18n.t(sp, "Drop distance not shown, so it can't be checked");
            if (drop > maxDrop)
                return String.format(Locale.US, I18n.t(sp, "Drop %s is farther than your limit of %s"), MainActivity.fmtDist(sp, drop), MainActivity.fmtDist(sp, maxDrop));
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

    // own tree walk: findAccessibilityNodeInfosByText returns nothing on Rapido's overlay
    static void findAll(AccessibilityNodeInfo n, String[] labels, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        boolean hit = false;
        for (CharSequence s : new CharSequence[]{n.getText(), n.getContentDescription()})
            if (!hit && s != null) for (String l : labels) if (s.toString().trim().equalsIgnoreCase(l)) { hit = true; break; }
        if (hit) { out.add(n); return; } // no need to look inside a matching node
        for (int i = 0; i < n.getChildCount(); i++) findAll(n.getChild(i), labels, out);
    }

    static AccessibilityNodeInfo walk(AccessibilityNodeInfo n, String[] labels) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        findAll(n, labels, out);
        return out.isEmpty() ? null : out.get(0);
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
