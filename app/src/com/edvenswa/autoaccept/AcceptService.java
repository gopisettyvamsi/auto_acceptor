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
import android.speech.tts.TextToSpeech;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcceptService extends AccessibilityService {
    static final Pattern FARE = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    static final Pattern KM = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*km", Pattern.CASE_INSENSITIVE);
    // a distance on its own short line: "0.9 km" or "400 m" (meters are converted to km)
    static final Pattern DIST_LINE = FareParser.DIST_LINE;
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
        I18n.a("Drop is outside your Go To areas", "ड्रॉप आपके गो टू इलाकों से बाहर है", "డ్రాప్ మీ గో టు ప్రాంతాల వెలుపల ఉంది");
        I18n.a("Ride is outside your Stay In areas", "राइड आपके स्टे इन इलाकों से बाहर है", "రైడ్ మీ స్టే ఇన్ ప్రాంతాల వెలుపల ఉంది");
        I18n.a("Ride is outside your Go To and Stay In areas", "राइड आपके गो टू और स्टे इन इलाकों से बाहर है", "రైడ్ మీ గో టు, స్టే ఇన్ ప్రాంతాల వెలుపల ఉంది");
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
    static class Card { // one ride request card on the driver app's screen
        AccessibilityNodeInfo accept, node, reject;
        Rect acceptBounds = new Rect(), fareBounds = new Rect(), distBounds = new Rect();
        String text = "", pickupAddr = "", dropAddr = "", type = "Bike", key = "", reason;
        double fare, base, bonus, perKm, dist, fuel; // fare = base + any extra the customer added
        int rank;      // 2 = matches Go To, 1 = matches Stay In, 0 = neither
        String tag = "";
        String status = "";      // why this ride is or is not being auto-accepted, shown on its chip
        boolean statusGood;
        Double pickup, drop;
        int mins = -1, verdict = -1;
    }

    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable hideChips = this::removeChips;
    final Runnable hideTrip = this::removeTrip;
    long lastClick, lastRun, autoTapUntil, pendingUntil;
    String pendingKey = "";
    final Set<String> alerted = new HashSet<>(), skipped = new HashSet<>();
    List<Card> lastCards = new ArrayList<>();
    final List<ChipView> chips = new ArrayList<>();
    final List<int[]> chipBase = new ArrayList<>(); // per chip: x, y beside the fare, fare height, pickup-row top, pickup-row height
    final Map<String, int[]> drag = new HashMap<>(); // offset of a chip the driver dragged; only for the offer on screen, never saved
    LinearLayout tripView;
    int pBg, pFg, pSub, pLine, pAcc; // trip popup colours, set from the theme each time it opens
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
        if ("on".equals(k)) {
            updateBubbleRing();
            if (s.getBoolean("voice", false)) say(s, lang -> I18n.tl(lang, s.getBoolean("on", false) ? "Auto-accept is on" : "Auto-accept is paused"));
        }
        if ("bubble".equals(k) && !s.getBoolean("bubble", true)) removeBubble();
    };

    SharedPreferences prefs() { return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE); }
    float density() { return getResources().getDisplayMetrics().density; }
    WindowManager wm() { return (WindowManager) getSystemService(WINDOW_SERVICE); }

    @Override protected void onServiceConnected() {
        connected = true; instance = this;
        PrefsGuard.sanitize(prefs());
        IntentFilter f = new IntentFilter(ACTION_TOGGLE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(toggleRcv, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(toggleRcv, f);
        prefs().edit().remove("chip_dx").remove("chip_dy").apply(); // older builds saved one drag offset for every chip
        prefs().registerOnSharedPreferenceChangeListener(prefL);
        refreshNote();
        MainActivity.scheduleSummary(this);
    }

    @Override public boolean onUnbind(Intent i) {
        connected = false; instance = null;
        removeChips(); removeBubble(); removeTrip(); removeFlash();
        releaseSpeech();
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
        if (pkg.equals(getPackageName())) { AccessibilityNodeInfo es = e.getSource(); if (es != null && isOverlay(es)) return; }
        events++; lastPkg = pkg; // diagnostics, shown via long-press on the app name
        boolean watched = sp.getString("pkgs", MainActivity.DEFAULT_PKGS).contains(pkg)
            || (SampleActivity.visible && pkg.equals(getPackageName())); // the in-app test screen counts as a driver app

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

    static boolean isOverlay(AccessibilityNodeInfo n) {
        AccessibilityWindowInfo w = n.getWindow();
        return w != null && w.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY;
    }

    String trailPkg = "";
    final Runnable trail = () -> analyze(trailPkg, null);
    String curPkg = "";
    // Rapido slides cards around without telling accessibility, so while cards are showing we look again every 0.7 s
    final Runnable tick = () -> { if (!chips.isEmpty()) analyze(curPkg, null); };

    void analyze(String pkg, AccessibilityNodeInfo src) {
        SharedPreferences sp = prefs();
        boolean auto = sp.getBoolean("on", false), showChip = sp.getBoolean("chip", true), reject = sp.getBoolean("reject", false);
        boolean trip = sp.getBoolean("trip", true);
        long now = System.currentTimeMillis();
        lastRun = now;
        h.removeCallbacks(trail);

        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue; // our own chips, bubble and popup are never card text
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && pkg.contentEquals(r.getPackageName())) roots.add(r);
        }
        if (src != null && !isOverlay(src)) { // read the window that fired the event
            AccessibilityNodeInfo top = src;
            for (AccessibilityNodeInfo p = src.getParent(); p != null; p = p.getParent()) top = p;
            roots.add(top);
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) roots.add(r);
        }
        if (roots.isEmpty()) return;

        // read each ride card by where its text sits on screen, so several cards (and cards whose Accept is hidden) all work
        StringBuilder text = new StringBuilder(), dump = new StringBuilder();
        List<AccessibilityNodeInfo> accepts = new ArrayList<>(), rejects = new ArrayList<>();
        for (AccessibilityNodeInfo r : roots) { collect(r, text, dump); findAll(r, LABELS, accepts); findAll(r, REJECT, rejects); }
        List<Card> cards = readCards(roots, accepts, rejects);
        if (cards.isEmpty()) cards = cardsFromButtons(accepts, sp);
        lastCards = cards;

        if (cards.isEmpty()) {
            alerted.clear(); skipped.clear();
            h.removeCallbacks(tick);
            removeChips();
            sp.edit().putString("dump", "pkg=" + pkg + "\nresult=no ride request on screen\n\n" + dump).apply();
            return;
        }

        StringBuilder summary = new StringBuilder();
        refreshAway(sp);
        breakReminder(sp);
        // Go To / Stay In: the driver's own priority rules, matched against the addresses on the card
        boolean goOn = sp.getBoolean("goto_on", false), stayOn = sp.getBoolean("stay_on", false);
        boolean strictGo = goOn && sp.getBoolean("goto_strict", false), strictStay = stayOn && sp.getBoolean("stay_strict", false);
        String goAreas = sp.getString("goto_areas", ""), stayAreas = sp.getString("stay_areas", "");
        for (Card c : cards) {
            double dist = (c.pickup == null ? 0 : c.pickup) + (c.drop == null ? 0 : c.drop);
            c.type = MainActivity.rideType(sp, c.text);
            c.perKm = MainActivity.perKm(c.fare, c.pickup, c.drop);
            c.dist = dist;
            c.fuel = MainActivity.costPerKm(sp, c.type) * dist;
            c.verdict = Double.isNaN(c.perKm) ? -1 : MainActivity.verdict(c.perKm, MainActivity.targetFor(sp, c.type));
            c.key = c.fare + "|" + c.pickup + "|" + c.drop;
            c.reason = auto ? decide(sp, c.fare, c.pickup, c.drop, c.mins, c.type) : null;
            boolean go = goOn && AreaMatch.hasAny(c.dropAddr, goAreas);
            boolean stay = stayOn && AreaMatch.hasAny(c.pickupAddr, stayAreas) && AreaMatch.hasAny(c.dropAddr, stayAreas);
            c.rank = go ? 2 : stay ? 1 : 0;
            c.tag = go ? "Go To" : stay ? "Stay In" : "";
            if (auto && c.reason == null && (strictGo || strictStay) && !((strictGo && go) || (strictStay && stay)))
                c.reason = I18n.t(sp, strictGo && strictStay ? "Ride is outside your Go To and Stay In areas"
                    : strictGo ? "Drop is outside your Go To areas" : "Ride is outside your Stay In areas");
            summary.append("card fare=").append(c.fare).append(" pickup=").append(c.pickup).append(" drop=").append(c.drop)
                .append(" mins=").append(c.mins).append(" type=").append(c.type)
                .append(" reason=").append(c.reason).append('\n');
        }
        sp.edit().putString("dump", "pkg=" + pkg + " cards=" + cards.size() + "\n" + summary + "\n" + dump).apply();

        // which card auto-accept takes, and a plain-language status for every card (shown on its chip)
        Card pick = null;
        boolean cooling = auto && now - lastClick < MainActivity.pref(sp, "cool", 4) * 1000;
        if (auto && !cooling)
            for (Card c : cards)
                if (c.accept != null && c.reason == null && (pick == null || better(c, pick, PrefsGuard.getInt(sp, "sort", 0)))) pick = c;
        for (Card c : cards) {
            boolean pending = c.key.equals(pendingKey) && now < pendingUntil; // chosen already, waiting out the response delay
            c.statusGood = c == pick || pending;
            if (!auto) c.status = I18n.t(sp, "Auto-accept is paused");
            else if (c == pick || pending) c.status = I18n.t(sp, "Accepting this ride") + (c.dist > 0 ? "  ·  " + I18n.t(sp, "Score") + " " + MainActivity.score(sp, c.fare, c.dist, c.type) : "");
            else if (c.reason != null) c.status = c.reason;
            else if (c.accept == null) c.status = I18n.t(sp, "Accept button not visible");
            else if (cooling) c.status = I18n.t(sp, "Waiting after the last accept");
            else c.status = I18n.t(sp, "Another ride has priority");
        }

        if (showChip) showChips(cards); else removeChips();
        curPkg = pkg;
        h.removeCallbacks(tick);
        if (showChip) h.postDelayed(tick, 450);

        Card bestNew = null; // one alert per new ride; when several appear together, for the best-rated
        for (Card c : cards) {
            if (alerted.add(c.key) && c.verdict >= 0 && (bestNew == null || c.verdict > bestNew.verdict)) bestNew = c;
            if (c.reason != null && skipped.add(c.key)) MainActivity.addSkip(sp, c.reason, c.fare);
        }
        if (bestNew != null) notifyRide(sp, bestNew);

        double near = MainActivity.pref(sp, "near", 0); // instant-accept radius: nearby pickups are tapped with no delay
        if (pick != null) {
            final Card chosen = pick;
            long delay = near > 0 && chosen.pickup != null && chosen.pickup <= near ? 0 : (long) MainActivity.pref(sp, "delay", 300);
            lastClick = now; autoTapUntil = now + delay + 2500;
            pendingKey = chosen.key; pendingUntil = now + delay + 1500;
            if (!pkg.equals(getPackageName())) { MainActivity.addHistory(sp, pkg, chosen.fare, chosen.pickup, chosen.drop, chosen.type); geocodePickup(sp, chosen.pickupAddr, chosen.fare); }
            h.postDelayed(() -> tap(chosen.accept), delay);
            if (trip) h.postDelayed(() -> showTrip(chosen), delay + 700);
        } else if (reject && now - lastClick >= 4000) {
            // optional: decline a card rated Low (only when nothing was accepted)
            for (Card c : cards) {
                if (c.verdict != 0) continue;
                if (c.reject != null) {
                    final AccessibilityNodeInfo rj = c.reject;
                    lastClick = now;
                    MainActivity.addSkip(sp, I18n.t(sp, "Declined: rated Low"), c.fare);
                    h.postDelayed(() -> tap(rj), (long) MainActivity.pref(sp, "delay", 300));
                    break;
                }
            }
        }
    }

    static String shorten(String s) { return s.length() > 90 ? s.substring(0, 90) + "\u2026" : s; }

    /** Go To beats Stay In beats neither; ties follow the driver's sort choice, then the nearest pickup. */
    static boolean better(Card a, Card b, int sort) {
        return RideOrder.better(a.rank, a.perKm, a.fare, a.fare - a.fuel, a.pickup, b.rank, b.perKm, b.fare, b.fare - b.fuel, b.pickup, sort);
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
            if (c.acceptBounds.isEmpty()) continue;
            Rect a = new Rect(c.acceptBounds);
            a.inset(-slack, -slack);
            if (a.contains(r.centerX(), r.centerY())) {
                lastClick = now;
                if (!pkg.equals(getPackageName())) { MainActivity.addHistory(sp, pkg, c.fare, c.pickup, c.drop, c.type); geocodePickup(sp, c.pickupAddr, c.fare); }
                if (sp.getBoolean("trip", true)) showTrip(c);
                return;
            }
        }
    }

    // =====================================================================
    // reading the cards
    // =====================================================================
    /** Reads every ride card on screen by where its text sits (see CardLayout); each card gets its own Accept and Reject. */
    List<Card> readCards(List<AccessibilityNodeInfo> roots, List<AccessibilityNodeInfo> accepts, List<AccessibilityNodeInfo> rejects) {
        List<CardLayout.Box> boxes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AccessibilityNodeInfo r : roots) addBoxes(r, boxes, seen);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        List<Card> out = new ArrayList<>();
        for (CardLayout.Region rg : CardLayout.split(boxes, dm.widthPixels, dm.heightPixels)) {
            List<String> lines = rg.lines();
            String t = TextUtils.join("\n", lines);
            double[] fs = FareParser.parse(lines);
            List<Double> kms = distances(lines, t);
            if (fs == null || kms.isEmpty()) continue;
            Card c = fromLines(lines, t, fs, kms);
            c.node = (AccessibilityNodeInfo) rg.head.ref;
            c.fareBounds.set(rg.head.left, rg.head.top, rg.head.right, rg.head.bottom);
            if (rg.dist1 != null) c.distBounds.set(rg.dist1.left, rg.dist1.top, rg.dist1.right, rg.dist1.bottom);
            c.accept = firstIn(accepts, rg.start, rg.end); // may be null when the app hides this card's button
            if (c.accept != null) c.accept.getBoundsInScreen(c.acceptBounds);
            c.reject = firstIn(rejects, rg.start, rg.end);
            out.add(c);
        }
        return out;
    }

    /** Fallback for layouts CardLayout cannot read: climb from each Accept button to the container holding a fare and a distance. */
    List<Card> cardsFromButtons(List<AccessibilityNodeInfo> accepts, SharedPreferences sp) {
        List<Card> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AccessibilityNodeInfo a : accepts) {
            Rect r = new Rect();
            a.getBoundsInScreen(r);
            if (!seen.add(r.toShortString())) continue; // same window reached twice
            Card c = buildCard(a, sp);
            if (c != null) { c.acceptBounds = r; cards.add(c); }
        }
        return cards;
    }

    static AccessibilityNodeInfo firstIn(List<AccessibilityNodeInfo> nodes, int start, int end) {
        AccessibilityNodeInfo best = null;
        int bestTop = Integer.MAX_VALUE;
        Rect r = new Rect();
        for (AccessibilityNodeInfo n : nodes) {
            n.getBoundsInScreen(r);
            if (r.top >= start && r.top < end && r.top < bestTop) { best = n; bestTop = r.top; }
        }
        return best;
    }

    static void addBoxes(AccessibilityNodeInfo n, List<CardLayout.Box> out, Set<String> seen) {
        if (n == null) return;
        CharSequence tx = n.getText(), d = n.getContentDescription();
        String s = (tx != null ? tx.toString() : d != null ? d.toString() : "").trim();
        if (!s.isEmpty()) {
            Rect b = new Rect();
            n.getBoundsInScreen(b);
            if (!b.isEmpty() && seen.add(b.toShortString() + s)) out.add(new CardLayout.Box(s, b.left, b.top, b.right, b.bottom, n));
        }
        for (int i = 0; i < n.getChildCount(); i++) addBoxes(n.getChild(i), out, seen);
    }

    /** Builds a Card from a card's text lines: price, distances, pickup and drop places, drop duration. */
    static Card fromLines(List<String> lines, String t, double[] fs, List<Double> kms) {
        Card c = new Card();
        c.text = t; c.fare = fs[0] + fs[1]; c.base = fs[0]; c.bonus = fs[1];
        c.pickup = kms.get(0);
        c.drop = kms.size() > 1 ? kms.get(1) : null;
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

    /** Climbs from an Accept button to the smallest container that holds one fare and a distance (the card). */
    Card buildCard(AccessibilityNodeInfo accept, SharedPreferences sp) {
        for (AccessibilityNodeInfo p = accept.getParent(); p != null; p = p.getParent()) {
            if (countAccept(p) > 1) return null; // reached the list holding several cards
            List<String> lines = new ArrayList<>();
            gather(p, lines);
            String t = TextUtils.join("\n", lines);
            double[] fs = FareParser.parse(lines);
            List<Double> kms = distances(lines, t);
            if (fs == null || kms.isEmpty()) continue;
            Card c = fromLines(lines, t, fs, kms);
            c.accept = accept; c.node = p;
            AccessibilityNodeInfo fn = fareNode(p);
            if (fn != null) fn.getBoundsInScreen(c.fareBounds);
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
                return s;
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
            pos.add(new int[]{x, y, c.fareBounds.height(), c.distBounds.isEmpty() ? -1 : c.distBounds.top, c.distBounds.height(), c.fareBounds.bottom, c.distBounds.right});
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

    void render(SharedPreferences sp, List<Card> cs, List<int[]> pos) {
        float d = density();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        while (chips.size() > cs.size()) { removeChipView(chips.remove(chips.size() - 1)); chipBase.remove(chipBase.size() - 1); }
        while (chips.size() < cs.size()) { chips.add(newChip()); chipBase.add(new int[7]); }
        for (int i = 0; i < cs.size(); i++) {
            Card c = cs.get(i);
            chips.get(i).fit = 1f;
            chips.get(i).bind(c, sp);
            chips.get(i).key = c.key;
            System.arraycopy(pos.get(i), 0, chipBase.get(i), 0, 7);
        }
        relayoutChips(dm);
        h.removeCallbacks(hideChips);
        h.postDelayed(hideChips, 20000); // safety: never leave chips up if events stop
    }

    /**
     * Puts each chip where it covers nothing: beside the fare when there is room, otherwise on the pickup-distance row
     * to the right of the distance (never above the fare line, never over the distance number). When the free space is
     * too small the chip shrinks its own text to fit.
     */
    void relayoutChips(DisplayMetrics dm) {
        float d = density();
        int margin = (int) (8 * d), gap = (int) (8 * d);
        int pos = PrefsGuard.getInt(prefs(), "chip_pos", 0); // 0 automatic (beside the fare when it fits), 1 always beside the distance
        for (int i = 0; i < chips.size(); i++) {
            ChipView t = chips.get(i);
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) t.getLayoutParams();
            int[] b = chipBase.get(i); // x, y beside the fare, fare height, distance top, distance height, fare bottom, distance right
            int bx = b[0], by = b[1], w, hh;

            int[] nat = t.naturalSize();
            boolean besideFare = pos == 0 && bx + nat[0] <= dm.widthPixels - margin;
            boolean hasRow = b[3] >= 0;
            if (besideFare || !hasRow) {
                int availW = dm.widthPixels - margin - bx;
                if (!besideFare) t.fitTo(Math.max(availW, nat[0] / 2), 0);   // no row known: shrink to the space beside the fare
                else t.fit = 1f;
                w = Math.max(t.getMeasuredWidth(), 1); hh = Math.max(t.getMeasuredHeight(), 1);
                if (!besideFare) { bx = dm.widthPixels - margin - w; by = b[1] + b[2] + (int) (4 * d); }
            } else {
                // the pickup-distance row: from the fare's bottom edge down to the distance row's bottom edge, right of the distance
                int left = Math.max(b[6] + gap, dm.widthPixels / 3);
                int availW = dm.widthPixels - margin - left;
                int rowBottom = b[3] + b[4], maxH = rowBottom - b[5] - (int) (2 * d);
                t.fitTo(availW, maxH);
                w = Math.max(t.getMeasuredWidth(), 1); hh = Math.max(t.getMeasuredHeight(), 1);
                bx = dm.widthPixels - margin - w;
                by = hh <= b[4] ? b[3] + (b[4] - hh) / 2 : rowBottom - hh;   // taller than the row: sit on its bottom edge and grow upward, not above the fare
                by = Math.max(by, b[5] + (int) (2 * d));
            }
            int[] off = drag.get(t.key);
            if (off != null) { bx += off[0]; by += off[1]; }
            lp.x = Math.max(0, Math.min(dm.widthPixels - margin - w, bx));             // never off the screen
            lp.y = Math.max(0, Math.min(dm.heightPixels - margin - hh, by));
            try { wm().updateViewLayout(t, lp); } catch (RuntimeException ex) { /* removed meanwhile */ }
        }
    }

    /** Tap on a chip: move every chip to the next position. A switch for drivers who cannot drag. */
    void cyclePosition() {
        SharedPreferences sp = prefs();
        int p = (Math.min(1, PrefsGuard.getInt(sp, "chip_pos", 0)) + 1) % 2;
        sp.edit().putInt("chip_pos", p).apply();
        drag.clear();
        relayoutChips(getResources().getDisplayMetrics());
        Toast.makeText(this, I18n.t(sp, new String[]{"Automatic", "Beside the distance"}[p]), Toast.LENGTH_SHORT).show();
    }

    ChipView newChip() {
        ChipView t = new ChipView(this, logo());
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // drag a chip to move that chip only (lasts while this offer is on screen); a plain tap moves all chips to the next position
        t.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                String k = ((ChipView) v).key;
                float slop = 8 * density();
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        int[] cur = drag.get(k);
                        sx = ev.getRawX(); sy = ev.getRawY(); ox = cur == null ? 0 : cur[0]; oy = cur == null ? 0 : cur[1]; moved = false;
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(ev.getRawX() - sx) > slop || Math.abs(ev.getRawY() - sy) > slop) moved = true;
                        if (moved) {
                            drag.put(k, new int[]{ox + (int) (ev.getRawX() - sx), oy + (int) (ev.getRawY() - sy)});
                            relayoutChips(getResources().getDisplayMetrics());
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) cyclePosition();
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
        h.removeCallbacks(tick);
        for (ChipView t : chips) removeChipView(t);
        chips.clear(); chipBase.clear(); drag.clear();
    }

    // =====================================================================
    // trip details after accepting
    // =====================================================================
    void showTrip(Card c) {
        removeTrip();
        SharedPreferences sp = prefs();
        boolean dk = sp.getBoolean("dark", true);
        pBg = dk ? 0xFF151A25 : Color.WHITE; pFg = dk ? 0xFFF5F7FA : 0xFF0B0F19; pSub = dk ? 0xFFD2D8E4 : 0xFF1F2937;
        pLine = dk ? 0xFF2A3345 : 0xFFE6E8EE; pAcc = dk ? 0xFF5B8CFF : 0xFF1F4FD8;
        float d = density();
        DisplayMetrics dm = getResources().getDisplayMetrics();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (20 * d), (int) (18 * d), (int) (20 * d), (int) (16 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pBg); bg.setCornerRadius(20 * d); bg.setStroke((int) d, pLine);
        box.setBackground(bg);

        TextView title = text(I18n.t(sp, "Ride accepted"), 20, pFg, true);
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

        info(box, I18n.t(sp, "Pickup"), c.pickupAddr.isEmpty() ? "\u2013" : shorten(c.pickupAddr),
            c.pickup == null ? "" : MainActivity.fmtDist(sp, c.pickup) + " " + I18n.t(sp, "away"));
        String dropSub = (c.drop == null ? "" : MainActivity.fmtDist(sp, c.drop)) + (c.mins >= 0 ? "  ·  " + c.mins + " " + I18n.t(sp, "min") : "");
        info(box, I18n.t(sp, "Drop"), c.dropAddr.isEmpty() ? "\u2013" : shorten(c.dropAddr), dropSub);

        TextView money = text(I18n.t(sp, "Fare") + " ₹" + Math.round(c.fare) + (c.bonus > 0 ? " (\u20B9" + Math.round(c.base) + " + \u20B9" + Math.round(c.bonus) + ")" : "") + "  ·  " + I18n.t(sp, "fuel") + " ₹" + Math.round(c.fuel)
            + "  ·  " + I18n.t(sp, "You keep") + " ₹" + Math.round(c.fare - c.fuel), 14, pFg, true);
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
        if (sp.getBoolean("voice", false)) sayTrip(sp, c);
        h.removeCallbacks(hideTrip);
        h.postDelayed(hideTrip, sp.getBoolean("voice", false) ? 10000 : 90000); // voice-first closes the card itself; otherwise never block the screen for good
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
        t.setTypeface(Typeface.create("sans-serif-medium", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    void info(LinearLayout box, String label, String main, String sub) {
        float d = density();
        TextView l = text(label.toUpperCase(), 11, pSub, true);
        l.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = (int) (16 * d);
        box.addView(l, ll);
        box.addView(text(main, 15, pFg, true));
        if (!sub.isEmpty()) box.addView(text(sub, 13, pSub, true));
    }

    TextView button(String s, boolean primary) {
        float d = density();
        TextView t = text(s, 14, primary ? Color.WHITE : pAcc, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * d);
        g.setColor(primary ? pAcc : pBg);
        if (!primary) g.setStroke((int) d, pAcc);
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
        bubbleLp.x = PrefsGuard.getInt(sp, "bx", (int) (8 * d));
        bubbleLp.y = PrefsGuard.getInt(sp, "by", (int) (220 * d));
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

    // =====================================================================
    // alerts: vibration by rating, spoken, screen flash
    // =====================================================================
    TextToSpeech tts;
    boolean ttsReady;
    View flashView;

    /** One alert per new ride (the best of any that appear together), in the ways the driver chose. */
    void notifyRide(SharedPreferences sp, Card c) {
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (c.verdict == 2 && sp.getBoolean("alert", true)) alert();
        else if (sp.getBoolean("vib_verdict", false) && c.verdict < 2 && vib != null)
            vib.vibrate(c.verdict == 1 ? new long[]{0, 250} : new long[]{0, 600}, -1); // Average: one medium buzz; Low: one long buzz
        int speak = sp.getBoolean("voice", false) ? 2 : PrefsGuard.getInt(sp, "speak", 0); // voice-first speaks every ride
        if (speak == 2 || (speak == 1 && c.verdict == 2)) speak(sp, c);
        if (sp.getBoolean("flash", false)) flash(c.verdict);
    }

    /** Plays the alerts for a made-up ride, so drivers can check what they chose. */
    void testAlerts(int verdict) {
        Card c = new Card();
        c.fare = new double[]{15, 24, 33}[verdict]; c.dist = 3.7; c.fuel = 9; c.perKm = c.fare / 3.7; c.verdict = verdict;
        notifyRide(prefs(), c);
    }

    void speak(SharedPreferences sp, Card c) {
        say(sp, lang -> I18n.tl(lang, CHIP_NAME[c.verdict]) + ". " + MainActivity.fmtDist(sp, c.dist) + " " + I18n.tl(lang, "total") + ". "
            + I18n.tl(lang, "Keep") + " " + Math.round(c.fare - c.fuel) + " " + I18n.tl(lang, "rupees") + ".");
    }

    /** Speaks a sentence in the app language, or in English when the phone has no voice for it. The engine is started on first use and released when idle. */
    void say(SharedPreferences sp, java.util.function.Function<String, String> build) {
        if (tts == null) {
            pendingSp = sp; pendingMsg = build;
            tts = new TextToSpeech(this, st -> h.post(() -> {
                ttsReady = st == TextToSpeech.SUCCESS;
                java.util.function.Function<String, String> f = pendingMsg;
                pendingMsg = null;
                if (ttsReady && f != null) sayNow(pendingSp, f);
            }));
            return;
        }
        if (!ttsReady) { pendingSp = sp; pendingMsg = build; return; } // still starting: say it as soon as it is ready
        sayNow(sp, build);
    }

    void sayNow(SharedPreferences sp, java.util.function.Function<String, String> build) {
        String lang = sp.getString("lang", "en");
        int r = tts.setLanguage(new Locale(lang, "IN"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            lang = "en";
            tts.setLanguage(new Locale("en", "IN"));
        }
        tts.speak(build.apply(lang), TextToSpeech.QUEUE_FLUSH, null, "say");
        h.removeCallbacks(ttsIdle);
        h.postDelayed(ttsIdle, 30000);
    }

    final Runnable ttsIdle = this::releaseSpeech;
    SharedPreferences pendingSp;
    java.util.function.Function<String, String> pendingMsg;

    void releaseSpeech() {
        h.removeCallbacks(ttsIdle);
        pendingMsg = null;
        if (tts != null) { tts.shutdown(); tts = null; ttsReady = false; }
    }

    /** Voice-first mode: reads out what the trip card shows, so the driver never has to look. */
    void sayTrip(SharedPreferences sp, Card c) {
        say(sp, lang -> {
            StringBuilder sb = new StringBuilder(I18n.tl(lang, "Ride accepted")).append(". ");
            if (c.pickup != null) sb.append(I18n.tl(lang, "Pickup")).append(' ').append(MainActivity.fmtDist(sp, c.pickup)).append(' ').append(I18n.tl(lang, "away")).append(". ");
            if (c.drop != null) {
                sb.append(I18n.tl(lang, "Drop")).append(' ').append(MainActivity.fmtDist(sp, c.drop));
                if (c.mins >= 0) sb.append(", ").append(c.mins).append(' ').append(I18n.tl(lang, "min"));
                sb.append(". ");
            }
            return sb.append(I18n.tl(lang, "You keep")).append(' ').append(Math.round(c.fare - c.fuel)).append(' ').append(I18n.tl(lang, "rupees")).append('.').toString();
        });
    }

    /** A brief tint over the whole screen, green / amber / red by rating, for drivers who cannot hear the beep. */
    void flash(int verdict) {
        removeFlash();
        int[] cols = {0x66FF5252, 0x66FFC107, 0x6600C853};
        View v = new View(this);
        v.setBackgroundColor(cols[Math.max(0, Math.min(2, verdict))]);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        flashView = v;
        try { wm().addView(v, lp); } catch (RuntimeException ex) { flashView = null; return; }
        h.postDelayed(this::removeFlash, 350);
    }

    void removeFlash() {
        if (flashView != null) {
            try { wm().removeView(flashView); } catch (RuntimeException ex) { /* already gone */ }
            flashView = null;
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

    /** Nudges the driver to rest. ponytail: only checked while a ride card is on screen; use an alarm if that is too late. */
    void breakReminder(SharedPreferences sp) {
        double every = MainActivity.pref(sp, "brk_every", 0);
        if (every <= 0 || sp.getBoolean("break_on", false)) return;
        long first = Long.MAX_VALUE, now = System.currentTimeMillis();
        for (MainActivity.Ride r : MainActivity.parseRides(sp)) if (r.t >= MainActivity.midnight() && r.t < first) first = r.t;
        if (first == Long.MAX_VALUE) return;
        long last = Math.max(first, (long) MainActivity.pref(sp, "brk_last", 0));
        if (now - last <= every * 3600000L) return;
        sp.edit().putString("brk_last", String.valueOf(now)).apply();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(9, MainActivity.noteBuilder(this).setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(I18n.t(sp, "Time for a break")).setContentText(MainActivity.n(every) + " h").build());
    }

    /** Looks up the pickup address with the phone's own Geocoder (free, no key) and remembers it for the pickup-areas list. */
    void geocodePickup(final SharedPreferences sp, final String addr, final double fare) {
        if (addr == null || addr.trim().length() < 4 || !android.location.Geocoder.isPresent()) return;
        final android.location.Location near = lastLocation(this);
        final int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        new Thread(() -> {
            try {
                android.location.Geocoder g = new android.location.Geocoder(this, Locale.getDefault());
                List<android.location.Address> r = near == null ? g.getFromLocationName(addr, 1)
                    : g.getFromLocationName(addr, 1, near.getLatitude() - 0.5, near.getLongitude() - 0.5, near.getLatitude() + 0.5, near.getLongitude() + 0.5);
                if (r == null || r.isEmpty()) return;
                double la = r.get(0).getLatitude(), lo = r.get(0).getLongitude();
                float[] d = new float[1];
                if (near != null) { // a match in another city is a wrong match
                    android.location.Location.distanceBetween(near.getLatitude(), near.getLongitude(), la, lo, d);
                    if (d[0] > 60000) return;
                }
                MainActivity.addZone(sp, la, lo, fare, hour, addr.split(",")[0].trim());
            } catch (Exception e) { /* no network or no result: just no zone for this ride */ }
        }).start();
    }

    static double awayKm = -1; // distance from the driver's base, refreshed on every analyze; -1 = unknown

    /** Freshest last-known fix from any provider, or null (no permission, GPS off). */
    static android.location.Location lastLocation(Context ctx) {
        if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
        android.location.LocationManager lm = (android.location.LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        android.location.Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                android.location.Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (Exception e) { /* provider vanished */ }
        return best;
    }

    void refreshAway(SharedPreferences sp) {
        awayKm = -1;
        if (MainActivity.pref(sp, "radius", 0) <= 0 || sp.getString("base_lat", "").isEmpty()) return;
        android.location.Location l = lastLocation(this);
        if (l == null) return;
        float[] out = new float[1];
        try { android.location.Location.distanceBetween(l.getLatitude(), l.getLongitude(), Double.parseDouble(sp.getString("base_lat", "")), Double.parseDouble(sp.getString("base_lon", "")), out); }
        catch (Exception e) { return; }
        awayKm = out[0] / 1000.0;
    }

    /** Returns a plain-language reason the ride does not meet the filters, or null if it does. */
    static String decide(SharedPreferences sp, Double fare, Double km, Double drop, int mins, String type) {
        if (sp.getBoolean("break_on", false)) return I18n.t(sp, "You are on a break");
        double minFare = MainActivity.eff(sp, "fare", 0);
        boolean orMode = sp.getBoolean("or_mode", false); // ponytail: OR covers fare vs earnings-per-km only, the pair drivers actually trade off
        double pkMin = MainActivity.eff(sp, "perkm", 0), pkNow = MainActivity.perKm(fare, km, drop);
        boolean pkOk = pkMin > 0 && !Double.isNaN(pkNow) && pkNow >= pkMin;
        if (fare < minFare && !(orMode && pkOk))
            return String.format(Locale.US, I18n.t(sp, "Fare ₹%s is below your minimum of ₹%s"), MainActivity.n(fare), MainActivity.n(minFare));
        double rejLo = MainActivity.pref(sp, "rejlo", 0), rejHi = MainActivity.pref(sp, "rejhi", 0);
        if (rejHi > 0 && fare >= rejLo && fare <= rejHi) // ponytail: fare range only, add per-filter ranges if needed
            return String.format(Locale.US, I18n.t(sp, "Fare ₹%s is inside your reject range of ₹%s to ₹%s"), MainActivity.n(fare), MainActivity.n(rejLo), MainActivity.n(rejHi));
        double trip = (km == null ? 0 : km) + (drop == null ? 0 : drop);
        double maxFare = MainActivity.pref(sp, "maxfare", 0);
        if (maxFare > 0 && fare > maxFare)
            return String.format(Locale.US, I18n.t(sp, "Fare ₹%s is above your maximum of ₹%s"), MainActivity.n(fare), MainActivity.n(maxFare));
        double minTrip = MainActivity.pref(sp, "mintrip", 0), maxTrip = MainActivity.pref(sp, "maxtrip", 0);
        if ((minTrip > 0 || maxTrip > 0) && trip == 0) return I18n.t(sp, "Distance not shown, so trip length can't be checked");
        if (minTrip > 0 && trip < minTrip)
            return String.format(Locale.US, I18n.t(sp, "Trip %s is shorter than your minimum of %s"), MainActivity.fmtDist(sp, trip), MainActivity.fmtDist(sp, minTrip));
        if (maxTrip > 0 && trip > maxTrip)
            return String.format(Locale.US, I18n.t(sp, "Trip %s is longer than your limit of %s"), MainActivity.fmtDist(sp, trip), MainActivity.fmtDist(sp, maxTrip));
        double maxMins = MainActivity.pref(sp, "maxmins", 0);
        if (maxMins > 0 && mins >= 0 && mins > maxMins)
            return String.format(Locale.US, I18n.t(sp, "Ride takes %s min, longer than your limit of %s"), String.valueOf(mins), MainActivity.n(maxMins));
        double net = fare - MainActivity.costPerKm(sp, type) * trip; // fuel only; ponytail: add maintenance per km if wanted
        double minProfit = MainActivity.pref(sp, "minprofit", 0);
        if (minProfit > 0 && net < minProfit)
            return String.format(Locale.US, I18n.t(sp, "Profit ₹%s after fuel is below your minimum of ₹%s"), MainActivity.n(Math.round(net)), MainActivity.n(minProfit));
        double minNetKm = MainActivity.pref(sp, "minnetkm", 0);
        if (minNetKm > 0) {
            if (trip == 0) return I18n.t(sp, "Distance not shown, so profit per km can't be checked");
            if (net / trip < minNetKm)
                return String.format(Locale.US, I18n.t(sp, "Profit ₹%s per km after fuel is below your minimum of ₹%s"), String.format(Locale.US, "%.1f", net / trip), MainActivity.n(minNetKm));
        }
        double minHr = MainActivity.pref(sp, "minhr", 0);
        if (minHr > 0 && mins > 0 && net / (mins / 60.0) < minHr)
            return String.format(Locale.US, I18n.t(sp, "Profit ₹%s per hour is below your minimum of ₹%s"), MainActivity.n(Math.round(net / (mins / 60.0))), MainActivity.n(minHr));
        double dayRides = MainActivity.pref(sp, "dayrides", 0), dayKm = MainActivity.pref(sp, "daykm", 0);
        if (dayRides > 0 || dayKm > 0) {
            int n = 0; double kms = 0;
            for (MainActivity.Ride r : MainActivity.parseRides(sp)) if (r.t >= MainActivity.midnight()) { n++; kms += r.km(); }
            if (dayRides > 0 && n >= dayRides) return String.format(Locale.US, I18n.t(sp, "Daily limit of %s rides reached"), MainActivity.n(dayRides));
            if (dayKm > 0 && kms >= dayKm) return String.format(Locale.US, I18n.t(sp, "Daily limit of %s reached"), MainActivity.fmtDist(sp, dayKm));
        }
        double goal = MainActivity.pref(sp, "goal", 0);
        if (goal > 0 && sp.getBoolean("goal_stop", false)) {
            double earned = 0;
            for (MainActivity.Ride r : MainActivity.parseRides(sp)) if (r.t >= MainActivity.midnight()) earned += r.fare;
            if (earned >= goal) return I18n.t(sp, "Daily goal reached, auto-accept paused");
        }
        String sf = sp.getString("shift_from", ""), st = sp.getString("shift_to", "");
        if (!sf.isEmpty() && !st.isEmpty()) try {
            int a = Integer.parseInt(sf), b = Integer.parseInt(st), hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            boolean inside = a == b || (a < b ? hr >= a && hr < b : hr >= a || hr < b);
            if (!inside) return String.format(Locale.US, I18n.t(sp, "Outside your shift hours (%s:00 to %s:00)"), sf, st);
        } catch (Exception e) { /* bad hour typed: no shift limit */ }
        double maxHrs = MainActivity.pref(sp, "maxhrs", 0);
        if (maxHrs > 0) {
            long first = Long.MAX_VALUE;
            for (MainActivity.Ride r : MainActivity.parseRides(sp)) if (r.t >= MainActivity.midnight() && r.t < first) first = r.t;
            if (first != Long.MAX_VALUE && System.currentTimeMillis() - first > maxHrs * 3600000L)
                return String.format(Locale.US, I18n.t(sp, "You have worked more than %s hours today"), MainActivity.n(maxHrs));
        }
        double radius = MainActivity.pref(sp, "radius", 0);
        if (radius > 0 && awayKm >= 0 && awayKm > radius) // unknown location (-1) never blocks a ride
            return String.format(Locale.US, I18n.t(sp, "You are %s from your base, outside your %s area"), MainActivity.fmtDist(sp, awayKm), MainActivity.fmtDist(sp, radius));
        double minScore = MainActivity.pref(sp, "minscore", 0);
        if (minScore > 0) {
            int sc = MainActivity.score(sp, fare, trip, type);
            if (sc < 0) return I18n.t(sp, "Distance not shown, so the ride score can't be checked");
            if (sc < minScore)
                return String.format(Locale.US, I18n.t(sp, "Ride score %s is below your minimum of %s"), String.valueOf(sc), MainActivity.n(minScore));
        }
        double maxKm = MainActivity.eff(sp, "dist", 0);
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
        double minPk = MainActivity.eff(sp, "perkm", 0);
        if (minPk > 0 && !(orMode && minFare > 0 && fare >= minFare)) {
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
                .append(n.isClickable() ? " | clickable" : "").append(n.isSelected() ? " | selected" : "").append(n.isChecked() ? " | checked" : "").append('\n');
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), text, dump);
    }

    static Double first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }
}
