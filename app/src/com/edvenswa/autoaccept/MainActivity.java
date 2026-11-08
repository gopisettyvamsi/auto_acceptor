package com.edvenswa.autoaccept;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final String PREFS = "p";
    static final String DEFAULT_PKGS = "com.rapido.rider,com.rapido.captain,com.ubercab.driver,com.olacabs.oladriver";
    // user-facing app names -> internal package ids (never shown on screen)
    static final String[][] APPS = {
        {"Rapido", "com.rapido.rider,com.rapido.captain"},
        {"Uber Driver", "com.ubercab.driver"},
        {"Ola Driver", "com.olacabs.oladriver"}};
    static final String[] LANGS = {"en", "hi", "te"};
    static final String[] LANG_NAMES = {"English", "हिन्दी", "తెలుగు"};
    // name, minimum fare, maximum pickup km, minimum earnings per km
    static final String[][] PROFILES = {{"Relaxed", "0", "0", "0"}, {"Peak", "40", "3", "8"}, {"Night", "50", "2.5", "9"}};

    // palette: one accent, neutral greys
    static final int ACCENT = 0xFF1F4FD8, ACCENT_SOFT = 0xFFEAF0FF, BG = 0xFFF4F5F8, SURFACE = Color.WHITE,
            LINE = 0xFFE6E8EE, TEXT = 0xFF14171F, SUBTLE = 0xFF667085, OK = 0xFF1E8E4E, WARN = 0xFFB3261E,
            HERO = 0xFF101828, ON_HERO = 0xB3FFFFFF;

    SharedPreferences sp;
    FrameLayout content;
    LinearLayout nav, bottom;
    int tab = 0, step = 0, period = 0; // current tab, onboarding step, money period (0 today, 1 week, 2 month)

    // =====================================================================
    // model
    // =====================================================================
    static String appName(String pkg) {
        for (String[] a : APPS) if (a[1].contains(pkg)) return a[0];
        return "Driver app";
    }

    static double pref(SharedPreferences sp, String k, double def) {
        try { return Double.parseDouble(sp.getString(k, String.valueOf(def))); } catch (Exception e) { return def; }
    }

    /** Whole numbers without decimals, otherwise one decimal. */
    static String n(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v); }

    /** Earnings per km over the whole trip (pickup + drop); matches the Rapido chip. NaN if unknown. */
    static double perKm(double fare, Double pickup, Double drop) {
        double km = (pickup == null ? 0 : pickup) + (drop == null ? 0 : drop);
        return km > 0 ? fare / km : Double.NaN;
    }

    static final String[] TYPES = {"Bike", "Auto", "Car"};
    static final String[] VNAMES = {"Bike / Scooty", "Auto", "Car"};
    static final double[] DEF_MILEAGE = {45, 25, 15}; // km per litre
    static final Pattern BIKE = Pattern.compile("\\b(bike|scooty|scooter|moto)\\b");
    static final Pattern AUTO = Pattern.compile("\\bauto\\b");
    static final Pattern CAR = Pattern.compile("\\b(cab|car|sedan|suv|taxi|mini|prime)\\b");

    /** Fuel cost per km for a ride type = fuel price / mileage. */
    static double costPerKm(SharedPreferences sp, String type) {
        int i = Math.max(0, Arrays.asList(TYPES).indexOf(type));
        double mileage = pref(sp, "mile_" + TYPES[i], DEF_MILEAGE[i]);
        return mileage > 0 ? pref(sp, "petrol", 105) / mileage : 0;
    }

    /** Ride type from the offer screen text; falls back to the user's own vehicle. */
    static String rideType(SharedPreferences sp, String screen) {
        String s = screen.toLowerCase(Locale.ROOT);
        if (BIKE.matcher(s).find()) return "Bike";
        if (AUTO.matcher(s).find()) return "Auto";
        if (CAR.matcher(s).find()) return "Car";
        return sp.getString("vehicle", "Bike");
    }

    /** Target earnings per km for a ride type, plus the peak-hours bump when active. */
    static double targetFor(SharedPreferences sp, String type) {
        double base = pref(sp, "target_" + type, pref(sp, "target", 8));
        if (sp.getBoolean("peak_on", false)) {
            int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            int from = (int) pref(sp, "peak_from", 17), to = (int) pref(sp, "peak_to", 21);
            boolean inPeak = from <= to ? hr >= from && hr < to : hr >= from || hr < to;
            if (inPeak) base += pref(sp, "peak_plus", 2);
        }
        return base;
    }

    /** 2 = worth it, 1 = average, 0 = low, relative to the user's target. */
    static int verdict(double perKm, double target) {
        return perKm >= target ? 2 : perKm >= target * 0.75 ? 1 : 0;
    }

    static class Ride {
        long t; String app, type; double fare; Double pickup, drop;
        double km() { return (pickup == null ? 0 : pickup) + (drop == null ? 0 : drop); }
        double perKm() { return MainActivity.perKm(fare, pickup, drop); }
    }

    // history: one record per line "time|app|fare|pickup|drop|type", newest first, capped at 100
    static void addHistory(SharedPreferences sp, String pkg, double fare, Double pickup, Double drop, String type) {
        String rec = System.currentTimeMillis() + "|" + appName(pkg) + "|" + fare + "|"
            + (pickup == null ? "" : pickup) + "|" + (drop == null ? "" : drop) + "|" + type;
        String[] old = sp.getString("hist", "").split("\n");
        StringBuilder sb = new StringBuilder(rec);
        for (int i = 0; i < Math.min(old.length, 99); i++) if (!old[i].isEmpty()) sb.append('\n').append(old[i]);
        sp.edit().putString("hist", sb.toString()).apply();
    }

    static List<Ride> parseRides(SharedPreferences sp) {
        List<Ride> out = new ArrayList<>();
        for (String row : sp.getString("hist", "").split("\n")) {
            String[] r = row.split("\\|", -1);
            if (r.length < 5) continue; // ignore records from older builds
            try {
                Ride x = new Ride();
                x.t = Long.parseLong(r[0]); x.app = r[1]; x.fare = Double.parseDouble(r[2]);
                x.type = r.length > 5 && !r[5].isEmpty() ? r[5] : "Bike";
                x.pickup = r[3].isEmpty() ? null : Double.valueOf(r[3]);
                x.drop = r[4].isEmpty() ? null : Double.valueOf(r[4]);
                out.add(x);
            } catch (Exception e) { /* skip bad record */ }
        }
        return out;
    }

    // skipped rides: one per line "time|reason|fare", newest first, capped at 30
    static void addSkip(SharedPreferences sp, String reason, double fare) {
        String rec = System.currentTimeMillis() + "|" + reason.replace('|', ' ').replace('\n', ' ') + "|" + fare;
        String[] old = sp.getString("skips", "").split("\n");
        StringBuilder sb = new StringBuilder(rec);
        for (int i = 0; i < Math.min(old.length, 29); i++) if (!old[i].isEmpty()) sb.append('\n').append(old[i]);
        sp.edit().putString("skips", sb.toString()).apply();
    }

    // money entries: one per line "time|category|amount|note", newest first, capped at 300
    static final String[] CATS = {"Income - manual ride", "Income - tip or bonus", "Maintenance",
        "Parking & toll", "Fine", "Phone & data", "Food", "Other"};

    static class Entry {
        long t; String cat, note; double amt;
        boolean income() { return cat.startsWith("Income"); }
    }

    static List<Entry> parseEntries(SharedPreferences sp) {
        List<Entry> out = new ArrayList<>();
        for (String row : sp.getString("exp", "").split("\n")) {
            String[] r = row.split("\\|", 4);
            if (r.length < 3) continue;
            try {
                Entry e = new Entry();
                e.t = Long.parseLong(r[0]); e.cat = r[1]; e.amt = Double.parseDouble(r[2]); e.note = r.length > 3 ? r[3] : "";
                out.add(e);
            } catch (Exception ex) { /* skip bad record */ }
        }
        return out;
    }

    void saveEntries(List<Entry> es) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(es.size(), 300); i++) {
            Entry e = es.get(i);
            if (i > 0) sb.append('\n');
            sb.append(e.t).append('|').append(e.cat).append('|').append(e.amt).append('|').append(e.note);
        }
        sp.edit().putString("exp", sb.toString()).apply();
    }

    static long midnight() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** End-of-day summary text, or null if nothing happened today. */
    static String dailySummary(SharedPreferences sp) {
        long start = midnight();
        double fare = 0, fuel = 0, income = 0, expenses = 0; int n = 0;
        double[] hour = new double[24];
        Calendar c = Calendar.getInstance();
        for (Ride x : parseRides(sp)) {
            if (x.t < start) continue;
            n++; fare += x.fare; fuel += costPerKm(sp, x.type) * x.km();
            c.setTimeInMillis(x.t); hour[c.get(Calendar.HOUR_OF_DAY)] += x.fare;
        }
        for (Entry e : parseEntries(sp)) {
            if (e.t < start) continue;
            if (e.income()) income += e.amt; else expenses += e.amt;
        }
        if (n == 0 && income == 0 && expenses == 0) return null;
        double profit = fare + income - fuel - expenses - pref(sp, "fixed", 0) / 30;
        int best = 0;
        for (int i = 1; i < 24; i++) if (hour[i] > hour[best]) best = i;
        String s = n + " " + I18n.t(sp, n == 1 ? "ride" : "rides") + "  ·  ₹" + Math.round(fare + income) + " " + I18n.t(sp, "earned")
            + "  ·  ₹" + Math.round(profit) + " " + I18n.t(sp, profit >= 0 ? "profit" : "loss");
        if (hour[best] > 0) s += "  ·  " + I18n.t(sp, "best hour") + " " + (best % 12 == 0 ? 12 : best % 12) + (best < 12 ? " AM" : " PM");
        return s;
    }

    // =====================================================================
    // notifications and scheduling
    // =====================================================================
    static Notification.Builder noteBuilder(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(new NotificationChannel("vamsify", "Vamsify", NotificationManager.IMPORTANCE_LOW));
            return new Notification.Builder(c, "vamsify");
        }
        return new Notification.Builder(c);
    }

    /** Schedules (or cancels) the daily 9 PM summary. Alarms are cleared on reboot, so this runs whenever the app or service starts. */
    static void scheduleSummary(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7, new Intent(c, SummaryReceiver.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (!sp.getBoolean("summary", true)) { am.cancel(pi); return; }
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, 21); t.set(Calendar.MINUTE, 0); t.set(Calendar.SECOND, 0);
        if (t.before(Calendar.getInstance())) t.add(Calendar.DAY_OF_MONTH, 1);
        am.setInexactRepeating(AlarmManager.RTC, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }

    // =====================================================================
    // view helpers
    // =====================================================================
    String L(String s) { return I18n.t(sp, s); }
    String F(String template, Object... a) { return String.format(Locale.US, L(template), a); }
    float scale() { return sp.getBoolean("large", false) ? 1.2f : 1f; }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    TextView tv(String s, int size, int color, String family) {
        TextView t = new TextView(this);
        t.setText(L(s)); t.setTextSize(size * scale()); t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    TextView label(ViewGroup p, String s, int size, int color, String family, int top) {
        TextView t = tv(s, size, color, family);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        p.addView(t, lp);
        return t;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    LinearLayout col() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }

    FrameLayout badge(int res, int bg, int fg, int size, int stroke) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(shape(bg, size / 2, stroke));
        ImageView i = new ImageView(this);
        i.setImageResource(res); i.setColorFilter(fg);
        f.addView(i, new FrameLayout.LayoutParams(dp(size / 2), dp(size / 2), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return f;
    }

    LinearLayout card(ViewGroup root, int top) {
        LinearLayout c = col();
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(shape(SURFACE, 16, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        root.addView(c, lp);
        return c;
    }

    LinearLayout newPage() {
        content.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = col();
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        sv.addView(root);
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    /** Small caps heading with an optional action link on the right. */
    void section(LinearLayout root, String title, String action, Runnable onAction) {
        LinearLayout r = row();
        TextView t = tv(title, 12, SUBTLE, "sans-serif-medium");
        t.setText(t.getText().toString().toUpperCase());
        t.setLetterSpacing(0.08f);
        r.addView(t, weight());
        if (action != null) {
            TextView a = tv(action, 14, ACCENT, "sans-serif-medium");
            a.setOnClickListener(v -> onAction.run());
            r.addView(a);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(24); lp.bottomMargin = dp(8);
        root.addView(r, lp);
    }

    TextView chip(int v) {
        int[] fg = {0xFFB3261E, 0xFF8A5A00, 0xFF1E8E4E}, bg = {0xFFFCE8E6, 0xFFFFF1D6, 0xFFE3F4EA};
        String[] name = {"Low", "Average", "Worth it"};
        TextView t = tv(name[v], 12, fg[v], "sans-serif-medium");
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(shape(bg[v], 12, 0));
        return t;
    }

    Switch toggle(boolean checked) {
        Switch s = new Switch(this);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFFF2F4F7}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0x661F4FD8, 0xFFD0D5DD}));
        s.setChecked(checked);
        return s;
    }

    CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(L(text)); c.setChecked(checked); c.setTextSize(16 * scale()); c.setTextColor(TEXT);
        c.setButtonTintList(ColorStateList.valueOf(ACCENT));
        return c;
    }

    EditText field(LinearLayout p, String name, String help, String val, int type, int top) {
        label(p, name, 14, TEXT, "sans-serif-medium", top);
        EditText e = new EditText(this);
        e.setInputType(type); e.setText(val); e.setSingleLine(true);
        e.setTextColor(TEXT); e.setTextSize(16 * scale());
        e.setBackground(shape(SURFACE, 10, 0xFFC4C9D2));
        e.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(Math.round(48 * scale())));
        lp.topMargin = dp(8);
        p.addView(e, lp);
        label(p, help, 13, SUBTLE, "sans-serif", 4);
        return e;
    }

    Button button(LinearLayout p, String s, boolean primary, int top) {
        Button b = new Button(this);
        b.setText(L(s)); b.setAllCaps(false); b.setTextSize(15 * scale());
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? Color.WHITE : ACCENT);
        b.setBackground(primary ? shape(ACCENT, 12, 0) : shape(SURFACE, 12, ACCENT));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(Math.round(50 * scale())));
        lp.topMargin = dp(top);
        p.addView(b, lp);
        return b;
    }

    /** Segmented control. sel = -1 for none selected. Repaints itself; pick is called on every tap. */
    void segmented(LinearLayout parent, String[] names, int sel, int top, java.util.function.IntConsumer pick) {
        LinearLayout seg = row();
        TextView[] o = new TextView[names.length];
        int[] cur = {sel};
        Runnable paint = () -> {
            for (int i = 0; i < o.length; i++) {
                boolean s = i == cur[0];
                o[i].setBackground(shape(s ? ACCENT : SURFACE, 10, s ? 0 : 0xFFC4C9D2));
                o[i].setTextColor(s ? Color.WHITE : TEXT);
            }
        };
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            o[i] = tv(names[i], 14, TEXT, "sans-serif-medium");
            o[i].setGravity(Gravity.CENTER);
            o[i].setPadding(dp(4), dp(11), dp(4), dp(11));
            o[i].setOnClickListener(v -> { cur[0] = idx; paint.run(); pick.accept(idx); });
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) ol.leftMargin = dp(8);
            seg.addView(o[i], ol);
        }
        paint.run();
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(top);
        parent.addView(seg, sl);
    }

    void divider(LinearLayout box, int top) {
        View d = new View(this);
        d.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(1));
        dl.topMargin = dp(top);
        box.addView(d, dl);
    }

    CheckBox[] appChecks(LinearLayout parent) {
        String saved = sp.getString("pkgs", DEFAULT_PKGS);
        CheckBox[] boxes = new CheckBox[APPS.length];
        for (int i = 0; i < APPS.length; i++) {
            boxes[i] = check(APPS[i][0], saved.contains(APPS[i][1].split(",")[0]));
            parent.addView(boxes[i]);
        }
        return boxes;
    }

    String pkgsFrom(CheckBox[] boxes) {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < APPS.length; i++) if (boxes[i].isChecked()) p.append(APPS[i][1]).append(',');
        return p.toString();
    }

    // =====================================================================
    // ride list, weekly summary, export
    // =====================================================================
    void rideList(LinearLayout box, int max) {
        List<Ride> rs = parseRides(sp);
        if (rs.isEmpty()) {
            LinearLayout e = col();
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.setPadding(0, dp(12), 0, dp(12));
            e.addView(badge(R.drawable.ic_history, ACCENT_SOFT, ACCENT, 56, 0));
            label(e, "No rides accepted yet", 16, TEXT, "sans-serif-medium", 12).setGravity(Gravity.CENTER);
            label(e, "Accepted rides appear here with your earnings and a worth-it rating.", 13, SUBTLE, "sans-serif", 4).setGravity(Gravity.CENTER);
            box.addView(e);
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());
        for (int i = 0; i < Math.min(max, rs.size()); i++) {
            Ride x = rs.get(i);
            if (i > 0) divider(box, 12);
            double pk = x.perKm(), fuel = costPerKm(sp, x.type) * x.km(), keep = x.fare - fuel;
            LinearLayout r = row();
            if (i > 0) ((LinearLayout.LayoutParams) (r.getLayoutParams() == null ? new LinearLayout.LayoutParams(-1, -2) : r.getLayoutParams())).topMargin = dp(12);
            r.addView(badge(R.drawable.ic_car, ACCENT_SOFT, ACCENT, 44, 0));
            LinearLayout c = col();
            c.setPadding(dp(12), 0, dp(8), 0);
            String money = "₹" + Math.round(x.fare);
            label(c, Double.isNaN(pk) ? money : money + "  ·  ₹" + String.format(Locale.US, "%.2f", pk) + "/km", 16, TEXT, "sans-serif-medium", 0);
            label(c, x.app + "  ·  " + L(x.type) + "  ·  " + f.format(new Date(x.t)), 13, SUBTLE, "sans-serif", 2);
            if (!Double.isNaN(pk)) label(c, n(Math.rint(x.km() * 10) / 10) + " km  ·  " + L("fuel") + " ₹" + Math.round(fuel)
                + "  ·  " + L("you keep") + " ₹" + Math.round(keep), 13, SUBTLE, "sans-serif", 0);
            r.addView(c, weight());
            if (!Double.isNaN(pk)) r.addView(chip(verdict(pk, targetFor(sp, x.type))));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = dp(i > 0 ? 12 : 0);
            box.addView(r, rl);
        }
    }

    class WeekChart extends View {
        final double[] v; final String[] d;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        WeekChart(double[] v, String[] d) { super(MainActivity.this); this.v = v; this.d = d; }
        @Override protected void onDraw(android.graphics.Canvas c) {
            double max = 1;
            for (double x : v) max = Math.max(max, x);
            float w = getWidth(), h = getHeight(), slot = w / v.length, bw = slot * 0.5f, foot = dp(20);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            p.setTextSize(dp(11));
            for (int i = 0; i < v.length; i++) {
                float bh = (float) ((h - foot - dp(18)) * v[i] / max), x = slot * i + (slot - bw) / 2, top = h - foot - bh;
                p.setColor(i == v.length - 1 ? ACCENT : 0xFFC7D2F5);
                c.drawRoundRect(x, top, x + bw, h - foot, dp(4), dp(4), p);
                p.setColor(SUBTLE);
                c.drawText(d[i], slot * i + slot / 2, h - dp(4), p);
                if (v[i] > 0) c.drawText("₹" + Math.round(v[i]), slot * i + slot / 2, top - dp(4), p);
            }
        }
    }

    void weekCard(LinearLayout root, List<Ride> rs) {
        long day = 24L * 3600 * 1000, start = midnight() - 6 * day;
        double[] v = new double[7]; String[] d = new String[7];
        SimpleDateFormat df = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) d[i] = df.format(new Date(start + i * day + day / 2));
        double fare = 0, km = 0; int cnt = 0;
        for (Ride x : rs) {
            if (x.t < start) continue;
            v[Math.min(6, (int) ((x.t - start) / day))] += x.fare;
            cnt++; fare += x.fare; km += x.km();
        }
        int best = 0;
        for (int i = 1; i < 7; i++) if (v[i] > v[best]) best = i;

        LinearLayout c = card(root, 16);
        label(c, "Last 7 days", 14, TEXT, "sans-serif-medium", 0);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, dp(150));
        cl.topMargin = dp(8);
        c.addView(new WeekChart(v, d), cl);
        LinearLayout stats = row();
        String[][] st = {{"₹" + Math.round(fare), "Earned"}, {String.valueOf(cnt), "Rides"},
            {km > 0 ? "₹" + String.format(Locale.US, "%.1f", fare / km) : "–", "Avg per km"}};
        for (String[] s : st) {
            LinearLayout k = col();
            label(k, s[0], 18, TEXT, "sans-serif-medium", 0);
            label(k, s[1], 12, SUBTLE, "sans-serif", 0);
            stats.addView(k, weight());
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(12);
        c.addView(stats, sl);
        if (v[best] > 0) label(c, L("Best day") + ": " + d[best] + "  ·  ₹" + Math.round(v[best]), 13, SUBTLE, "sans-serif", 10);
    }

    void exportCsv(List<Ride> rs) {
        StringBuilder sb = new StringBuilder("date,app,type,fare,pickup_km,drop_km,earn_per_km\n");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (Ride x : rs) {
            double pk = x.perKm();
            sb.append(f.format(new Date(x.t))).append(',').append(x.app).append(',').append(x.type).append(',')
              .append(Math.round(x.fare)).append(',').append(x.pickup == null ? "" : x.pickup).append(',')
              .append(x.drop == null ? "" : x.drop).append(',')
              .append(Double.isNaN(pk) ? "" : String.format(Locale.US, "%.2f", pk)).append('\n');
        }
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Vamsify ride history").putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, L("Export as CSV")));
    }

    // =====================================================================
    // money: earnings, expenses, profit / loss
    // =====================================================================
    String signed(double v) { return (v < 0 ? "−₹" : "₹") + Math.abs(Math.round(v)); }

    void moneyRow(LinearLayout box, String name, String sub, double value, boolean bold, int top) {
        LinearLayout r = row();
        LinearLayout l = col();
        label(l, name, bold ? 16 : 15, TEXT, bold ? "sans-serif-medium" : "sans-serif", 0);
        if (sub != null) label(l, sub, 12, SUBTLE, "sans-serif", 0);
        r.addView(l, weight());
        r.addView(tv(signed(value), bold ? 18 : 15, Math.round(value) == 0 ? SUBTLE : value > 0 ? OK : WARN, "sans-serif-medium"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        box.addView(r, lp);
    }

    void money() {
        LinearLayout root = newPage();
        label(root, "Money", 24, TEXT, "sans-serif-medium", 0);
        label(root, "Earnings, expenses and profit.", 14, SUBTLE, "sans-serif", 4);
        segmented(root, new String[]{"Today", "7 days", "This month"}, period, 16, i -> { period = i; money(); });

        long day = 24L * 3600 * 1000, start; int days;
        if (period == 0) { start = midnight(); days = 1; }
        else if (period == 1) { start = midnight() - 6 * day; days = 7; }
        else {
            Calendar m = Calendar.getInstance();
            days = m.get(Calendar.DAY_OF_MONTH);
            m.set(Calendar.DAY_OF_MONTH, 1); m.set(Calendar.HOUR_OF_DAY, 0); m.set(Calendar.MINUTE, 0); m.set(Calendar.SECOND, 0); m.set(Calendar.MILLISECOND, 0);
            start = m.getTimeInMillis();
        }

        double rideEarn = 0, fuel = 0, km = 0; int cnt = 0;
        Map<String, double[]> byType = new LinkedHashMap<>(), byApp = new LinkedHashMap<>();
        for (Ride x : parseRides(sp)) {
            if (x.t < start) continue;
            double f = costPerKm(sp, x.type) * x.km();
            cnt++; rideEarn += x.fare; fuel += f; km += x.km();
            for (int pass = 0; pass < 2; pass++) {
                Map<String, double[]> mp = pass == 0 ? byType : byApp;
                String k = pass == 0 ? x.type : x.app;
                double[] a = mp.get(k);
                if (a == null) { a = new double[3]; mp.put(k, a); }
                a[0]++; a[1] += x.fare; a[2] += f;
            }
        }
        double income = 0, expenses = 0;
        List<Entry> inPeriod = new ArrayList<>();
        for (Entry e : parseEntries(sp)) {
            if (e.t < start) continue;
            inPeriod.add(e);
            if (e.income()) income += e.amt; else expenses += e.amt;
        }
        double fixed = pref(sp, "fixed", 0) / 30 * days;
        double earn = rideEarn + income, costs = fuel + expenses + fixed, net = earn - costs;

        // hero: net profit or loss
        LinearLayout hero = col();
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(shape(HERO, 20, 0));
        hero.addView(tv(net >= 0 ? "Net profit" : "Net loss", 14, ON_HERO, "sans-serif"));
        label(hero, signed(net), 36, net >= 0 ? 0xFF4ADE80 : 0xFFF87171, "sans-serif-medium", 4);
        label(hero, L("Earnings") + " ₹" + Math.round(earn) + "  −  " + L("costs") + " ₹" + Math.round(costs), 13, ON_HERO, "sans-serif", 0);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(16);
        root.addView(hero, hl);

        section(root, "Breakdown", null, null);
        LinearLayout b = card(root, 0);
        moneyRow(b, "Ride earnings", cnt + " " + L(cnt == 1 ? "ride" : "rides"), rideEarn, false, 0);
        if (income > 0) moneyRow(b, "Other income", null, income, false, 12);
        moneyRow(b, "Fuel (estimated)", n(Math.rint(km * 10) / 10) + " " + L("km driven"), -fuel, false, 12);
        moneyRow(b, "Other expenses", null, -expenses, false, 12);
        if (fixed > 0) moneyRow(b, "Fixed costs", F("Monthly costs spread over %s days", days), -fixed, false, 12);
        divider(b, 14);
        moneyRow(b, net >= 0 ? "Net profit" : "Net loss", null, net, true, 12);

        section(root, "Per ride and per km", null, null);
        LinearLayout mc = card(root, 0);
        LinearLayout ms = row();
        String[][] st = {
            {cnt > 0 ? signed((rideEarn - fuel) / cnt) : "–", "Profit per ride"},
            {km > 0 ? "₹" + String.format(Locale.US, "%.1f", (earn - costs) / km) : "–", "Profit per km"},
            {km > 0 ? "₹" + String.format(Locale.US, "%.1f", costs / km) : "–", "Cost per km"}};
        for (String[] s : st) {
            LinearLayout k = col();
            label(k, s[0], 18, TEXT, "sans-serif-medium", 0);
            label(k, s[1], 12, SUBTLE, "sans-serif", 0);
            ms.addView(k, weight());
        }
        mc.addView(ms);
        label(mc, net >= 0 ? F("You are ₹%s above break-even.", Math.round(net)) : F("You need ₹%s more to break even.", Math.round(-net)), 13, SUBTLE, "sans-serif", 12);

        for (int pass = 0; pass < 2; pass++) {
            Map<String, double[]> mp = pass == 0 ? byType : byApp;
            if (mp.isEmpty()) continue;
            section(root, pass == 0 ? "By vehicle" : "By app", null, null);
            LinearLayout c = card(root, 0);
            boolean first = true;
            for (Map.Entry<String, double[]> en : mp.entrySet()) {
                double[] a = en.getValue();
                if (!first) divider(c, 12);
                moneyRow(c, L(en.getKey()), (long) a[0] + " " + L(a[0] == 1 ? "ride" : "rides") + "  ·  " + L("earned") + " ₹" + Math.round(a[1])
                    + "  ·  " + L("fuel") + " ₹" + Math.round(a[2]), a[1] - a[2], false, first ? 0 : 12);
                first = false;
            }
        }

        section(root, "Expenses & income", "Add", this::addEntryDialog);
        LinearLayout ec = card(root, 0);
        if (inPeriod.isEmpty())
            label(ec, "Nothing added for this period. Fuel is calculated automatically; add maintenance, tolls, fines or extra income here.", 13, SUBTLE, "sans-serif", 0);
        SimpleDateFormat f = new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());
        for (int i = 0; i < Math.min(inPeriod.size(), 20); i++) {
            Entry e = inPeriod.get(i);
            if (i > 0) divider(ec, 12);
            moneyRow(ec, L(e.cat), (e.note.isEmpty() ? "" : e.note + "  ·  ") + f.format(new Date(e.t)), e.income() ? e.amt : -e.amt, false, i > 0 ? 12 : 0);
            ec.getChildAt(ec.getChildCount() - 1).setOnClickListener(v -> new AlertDialog.Builder(this).setMessage(L("Delete this entry?"))
                .setNegativeButton(L("Cancel"), null)
                .setPositiveButton(L("Delete"), (d, w) -> {
                    List<Entry> all = parseEntries(sp);
                    for (int k = 0; k < all.size(); k++) if (all.get(k).t == e.t && all.get(k).amt == e.amt) { all.remove(k); break; }
                    saveEntries(all); money();
                }).show());
        }
    }

    void addEntryDialog() {
        LinearLayout box = col();
        box.setPadding(dp(20), dp(16), dp(20), 0);
        label(box, "Type", 14, TEXT, "sans-serif-medium", 0);
        Spinner cat = new Spinner(this);
        String[] shown = new String[CATS.length];
        for (int i = 0; i < CATS.length; i++) shown[i] = L(CATS[i]);
        cat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, shown));
        box.addView(cat);
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText amt = field(box, "Amount (₹)", "Enter the amount.", "", num, 16);
        EditText note = field(box, "Note (optional)", "For example: chain and oil change.", "", InputType.TYPE_CLASS_TEXT, 16);
        new AlertDialog.Builder(this).setTitle(L("Add entry")).setView(box)
            .setNegativeButton(L("Cancel"), null)
            .setPositiveButton(L("Save"), (d, w) -> {
                double a;
                try { a = Double.parseDouble(amt.getText().toString()); } catch (Exception ex) { return; }
                if (a <= 0) return;
                Entry e = new Entry();
                e.t = System.currentTimeMillis(); e.cat = CATS[cat.getSelectedItemPosition()]; e.amt = a;
                e.note = note.getText().toString().replace('|', ' ').replace('\n', ' ');
                List<Entry> all = parseEntries(sp);
                all.add(0, e);
                saveEntries(all);
                money();
            }).show();
    }

    // =====================================================================
    // screens
    // =====================================================================
    void show(int t) {
        tab = t;
        bottom.setVisibility(View.VISIBLE);
        for (int i = 0; i < nav.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) nav.getChildAt(i);
            int c = i == t ? ACCENT : SUBTLE;
            ((ImageView) item.getChildAt(0)).setColorFilter(c);
            ((TextView) item.getChildAt(1)).setTextColor(c);
        }
        if (t == 0) home(); else if (t == 1) history(); else if (t == 2) money(); else settings();
    }

    // ---------- first-run setup ----------
    void onboard() {
        bottom.setVisibility(View.GONE);
        LinearLayout r = newPage();
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        label(r, F("Step %s of 3", step + 1), 13, SUBTLE, "sans-serif-medium", 0);
        if (step == 0) {
            label(r, "Welcome to Vamsify", 26, TEXT, "sans-serif-medium", 8);
            label(r, "Tell us about your vehicle so we can estimate fuel cost and profit on every ride.", 15, SUBTLE, "sans-serif", 6);
            label(r, "Language", 14, TEXT, "sans-serif-medium", 24);
            segmented(r, LANG_NAMES, Arrays.asList(LANGS).indexOf(sp.getString("lang", "en")), 8,
                i -> { sp.edit().putString("lang", LANGS[i]).apply(); recreate(); });
            label(r, "My vehicle", 14, TEXT, "sans-serif-medium", 20);
            segmented(r, VNAMES, Math.max(0, Arrays.asList(TYPES).indexOf(sp.getString("vehicle", "Bike"))), 8,
                i -> sp.edit().putString("vehicle", TYPES[i]).apply());
            EditText fuel = field(r, "Fuel price (₹ per litre)", "Current fuel price in your city.", sp.getString("petrol", "105"), num, 20);
            button(r, "Next", true, 28).setOnClickListener(v -> { sp.edit().putString("petrol", fuel.getText().toString()).apply(); step = 1; onboard(); });
        } else if (step == 1) {
            label(r, "Turn on the service", 26, TEXT, "sans-serif-medium", 8);
            label(r, "Vamsify needs Accessibility access to read ride requests and show the earnings chip.", 15, SUBTLE, "sans-serif", 6);
            LinearLayout c = card(r, 20);
            label(c, "1. Tap the button below and open Installed apps.", 15, TEXT, "sans-serif", 0);
            label(c, "2. Choose Vamsify and switch it on.", 15, TEXT, "sans-serif", 10);
            label(c, "3. If it is greyed out: open App info, tap the menu at the top right and choose Allow restricted settings, then try again.", 15, TEXT, "sans-serif", 10);
            button(r, "Open Accessibility settings", false, 20).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            label(r, AcceptService.connected ? "Service is running." : "Service is not on yet. You can also do this later from Home.", 13,
                AcceptService.connected ? OK : SUBTLE, "sans-serif", 10);
            button(r, "Next", true, 20).setOnClickListener(v -> { step = 2; onboard(); });
        } else {
            label(r, "Choose your driver apps", 26, TEXT, "sans-serif-medium", 8);
            label(r, "Vamsify only works inside the apps you select.", 15, SUBTLE, "sans-serif", 6);
            LinearLayout c = card(r, 20);
            CheckBox[] boxes = appChecks(c);
            button(r, "Start", true, 28).setOnClickListener(v -> {
                sp.edit().putString("pkgs", pkgsFrom(boxes)).putBoolean("onboarded", true).apply();
                askNotificationPermission();
                scheduleSummary(this);
                show(0);
            });
        }
    }

    // ---------- home ----------
    boolean driverAppInstalled() {
        for (String[] a : APPS)
            if (sp.getString("pkgs", DEFAULT_PKGS).contains(a[1].split(",")[0]))
                for (String p : a[1].split(","))
                    try { getPackageManager().getPackageInfo(p, 0); return true; } catch (Exception e) { /* try next */ }
        return false;
    }

    void healthCard(LinearLayout p) {
        boolean svc = AcceptService.connected;
        boolean batt = ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
        boolean app = driverAppInstalled();
        LinearLayout c = card(p, 16);
        if (svc && batt && app) {
            LinearLayout r = row();
            r.addView(badge(R.drawable.ic_check_circle, 0xFFE3F4EA, OK, 40, 0));
            LinearLayout t = col();
            t.setPadding(dp(12), 0, 0, 0);
            label(t, "Everything is set up", 15, TEXT, "sans-serif-medium", 0);
            label(t, "Service on, battery unrestricted, driver app found.", 12, SUBTLE, "sans-serif", 0);
            r.addView(t, weight());
            c.addView(r);
            return;
        }
        label(c, "Setup check", 16, TEXT, "sans-serif-medium", 0);
        String[][] items = {
            {"Accessibility service", svc ? "On" : "Off. Tap to turn it on."},
            {"Battery use", batt ? "Unrestricted" : "Restricted. Tap to set Unrestricted so Android doesn't stop the service."},
            {"Driver app", app ? "Installed" : "No selected driver app found on this phone."}};
        boolean[] ok = {svc, batt, app};
        for (int i = 0; i < 3; i++) {
            LinearLayout r = row();
            r.addView(badge(ok[i] ? R.drawable.ic_check_circle : R.drawable.ic_error, ok[i] ? 0xFFE3F4EA : 0xFFFCE8E6, ok[i] ? OK : WARN, 36, 0));
            LinearLayout t = col();
            t.setPadding(dp(12), 0, 0, 0);
            label(t, items[i][0], 15, TEXT, "sans-serif-medium", 0);
            label(t, items[i][1], 12, SUBTLE, "sans-serif", 0);
            r.addView(t, weight());
            final int idx = i;
            if (!ok[i] && i < 2) r.setOnClickListener(v -> startActivity(new Intent(idx == 0
                ? Settings.ACTION_ACCESSIBILITY_SETTINGS : Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = dp(12);
            c.addView(r, rl);
        }
    }

    String profileInfo() {
        List<String> parts = new ArrayList<>();
        if (pref(sp, "fare", 0) > 0) parts.add(F("Min fare ₹%s", n(pref(sp, "fare", 0))));
        if (pref(sp, "dist", 0) > 0) parts.add(F("Pickup up to %s km", n(pref(sp, "dist", 0))));
        if (pref(sp, "perkm", 0) > 0) parts.add(F("At least ₹%s per km", n(pref(sp, "perkm", 0))));
        return parts.isEmpty() ? L("No limits set") : android.text.TextUtils.join("  ·  ", parts);
    }

    void profileCard(LinearLayout p) {
        LinearLayout c = card(p, 16);
        label(c, "Quick profile", 16, TEXT, "sans-serif-medium", 0);
        TextView info = label(c, profileInfo(), 13, SUBTLE, "sans-serif", 4);
        String cur = sp.getString("profile", "");
        int sel = -1;
        String[] names = new String[PROFILES.length];
        for (int i = 0; i < PROFILES.length; i++) { names[i] = PROFILES[i][0]; if (names[i].equals(cur)) sel = i; }
        segmented(c, names, sel, 12, i -> {
            sp.edit().putString("fare", PROFILES[i][1]).putString("dist", PROFILES[i][2])
                .putString("perkm", PROFILES[i][3]).putString("profile", PROFILES[i][0]).apply();
            info.setText(profileInfo());
        });
    }

    void home() {
        LinearLayout root = newPage();
        boolean running = AcceptService.connected;

        LinearLayout head = row();
        ImageView logo = new ImageView(this);
        try (java.io.InputStream in = getAssets().open("logo.png")) {
            logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (java.io.IOException ex) { /* header just shows the name */ }
        head.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout names = col();
        names.setPadding(dp(12), 0, 0, 0);
        label(names, "Welcome back", 13, SUBTLE, "sans-serif", 0);
        TextView name = label(names, "Vamsify", 20, TEXT, "sans-serif-medium", 0);
        head.addView(names, weight());
        head.addView(badge(R.drawable.ic_notifications, SURFACE, TEXT, 44, LINE));
        root.addView(head);
        // support only: long-press the name for technical details
        name.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setMessage("connected: " + AcceptService.connected
                + "\nevents: " + AcceptService.events + "\nlast app: " + AcceptService.lastPkg
                + "\n\n" + sp.getString("dump", "nothing read yet")).show();
            return true;
        });

        // today's numbers
        double fare = 0, km = 0, keepSum = 0; int cnt = 0, worth = 0;
        for (Ride x : parseRides(sp)) {
            if (x.t < midnight()) continue;
            cnt++; fare += x.fare; km += x.km();
            keepSum += x.fare - costPerKm(sp, x.type) * x.km();
            if (!Double.isNaN(x.perKm()) && verdict(x.perKm(), targetFor(sp, x.type)) == 2) worth++;
        }

        LinearLayout hero = col();
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(shape(HERO, 20, 0));
        LinearLayout top = row();
        top.addView(tv("Today's earnings", 14, ON_HERO, "sans-serif"), weight());
        LinearLayout pill = row();
        pill.setPadding(dp(10), dp(5), dp(12), dp(5));
        pill.setBackground(shape(0x26FFFFFF, 14, 0));
        View dot = new View(this);
        dot.setBackground(shape(running ? 0xFF4ADE80 : 0xFFF87171, 4, 0));
        pill.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView ps = tv(running ? "Service on" : "Enable service", 12, Color.WHITE, "sans-serif-medium");
        ps.setPadding(dp(6), 0, 0, 0);
        pill.addView(ps);
        pill.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        top.addView(pill);
        hero.addView(top);
        label(hero, "₹" + Math.round(fare), 36, Color.WHITE, "sans-serif-medium", 6);
        label(hero, cnt + " " + L(cnt == 1 ? "ride" : "rides") + " " + L("accepted today"), 13, ON_HERO, "sans-serif", 0);
        divider(hero, 16);
        ((LinearLayout.LayoutParams) hero.getChildAt(hero.getChildCount() - 1).getLayoutParams()).bottomMargin = dp(14);
        hero.getChildAt(hero.getChildCount() - 1).setBackgroundColor(0x26FFFFFF);
        LinearLayout stats = row();
        String[][] st = {
            {km > 0 ? "₹" + String.format(Locale.US, "%.1f", fare / km) : "–", "Avg per km"},
            {"₹" + Math.round(keepSum), "You keep"},
            {cnt > 0 ? worth + "/" + cnt : "–", "Worth it"}};
        for (String[] s : st) {
            LinearLayout c = col();
            label(c, s[0], 18, Color.WHITE, "sans-serif-medium", 0);
            label(c, s[1], 12, ON_HERO, "sans-serif", 0);
            stats.addView(c, weight());
        }
        hero.addView(stats);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(20);
        root.addView(hero, hlp);

        // daily goal progress
        double goal = pref(sp, "goal", 0);
        if (goal > 0) {
            LinearLayout gc = card(root, 16);
            LinearLayout gr = row();
            gr.addView(tv("Daily goal", 14, TEXT, "sans-serif-medium"), weight());
            gr.addView(tv(fare >= goal ? L("Goal reached") : "₹" + Math.round(goal - fare) + " " + L("to go"), 13,
                fare >= goal ? OK : SUBTLE, "sans-serif-medium"));
            gc.addView(gr);
            LinearLayout track = new LinearLayout(this);
            track.setWeightSum(1f);
            track.setBackground(shape(LINE, 4, 0));
            View bar = new View(this);
            bar.setBackground(shape(fare >= goal ? OK : ACCENT, 4, 0));
            track.addView(bar, new LinearLayout.LayoutParams(0, -1, (float) Math.min(1, fare / goal)));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, dp(8));
            tl.topMargin = dp(10);
            gc.addView(track, tl);
            label(gc, "₹" + Math.round(fare) + " " + L("of") + " ₹" + Math.round(goal), 12, SUBTLE, "sans-serif", 6);
        }

        LinearLayout controls = col(), recent = col();

        // quick actions
        LinearLayout qa = row();
        Object[][] q = {{"Rides", R.drawable.ic_history, 1}, {"Filters", R.drawable.ic_tune, 3},
                        {"Earnings", R.drawable.ic_wallet, 2}, {"Apps", R.drawable.ic_car, 3}};
        for (Object[] a : q) {
            LinearLayout c = col();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.addView(badge((Integer) a[1], SURFACE, ACCENT, 56, LINE));
            label(c, (String) a[0], 12, TEXT, "sans-serif-medium", 8).setGravity(Gravity.CENTER);
            c.setOnClickListener(v -> show((Integer) a[2]));
            qa.addView(c, weight());
        }
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-1, -2);
        qlp.topMargin = dp(20);
        controls.addView(qa, qlp);

        healthCard(controls);

        // switches
        LinearLayout sw = card(controls, 16);
        LinearLayout sr = row();
        sr.addView(badge(R.drawable.ic_bolt, ACCENT_SOFT, ACCENT, 44, 0));
        LinearLayout sc = col();
        sc.setPadding(dp(12), 0, dp(8), 0);
        label(sc, "Auto-accept", 16, TEXT, "sans-serif-medium", 0);
        label(sc, "Accept rides that match your filters", 13, SUBTLE, "sans-serif", 2);
        sr.addView(sc, weight());
        Switch on = toggle(sp.getBoolean("on", false));
        on.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("on", c).apply());
        sr.addView(on);
        sw.addView(sr);
        divider(sw, 14);
        LinearLayout cr = row();
        ((LinearLayout.LayoutParams) sw.getChildAt(sw.getChildCount() - 1).getLayoutParams()).bottomMargin = dp(14);
        cr.addView(badge(R.drawable.ic_trending_up, ACCENT_SOFT, ACCENT, 44, 0));
        LinearLayout cc = col();
        cc.setPadding(dp(12), 0, dp(8), 0);
        label(cc, "Earnings chip", 16, TEXT, "sans-serif-medium", 0);
        label(cc, "Show fare, fuel cost and a worth-it rating on every ride request", 13, SUBTLE, "sans-serif", 2);
        cr.addView(cc, weight());
        Switch chipSw = toggle(sp.getBoolean("chip", true));
        chipSw.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("chip", c).apply());
        cr.addView(chipSw);
        sw.addView(cr);

        profileCard(controls);

        Button sample = button(controls, "Try a sample ride", false, 16);
        sample.setOnClickListener(v -> {
            AcceptService s = AcceptService.instance;
            if (s == null) Toast.makeText(this, L("Turn on the service first"), Toast.LENGTH_LONG).show();
            else { s.showSample(); Toast.makeText(this, L("Sample shown for 8 seconds. It appears over any app."), Toast.LENGTH_LONG).show(); }
        });

        section(recent, "Recent rides", "View all", () -> show(1));
        rideList(card(recent, 0), 3);

        if (sp.getBoolean("onehand", false)) { root.addView(recent); root.addView(controls); } // controls within thumb reach
        else { root.addView(controls); root.addView(recent); }
    }

    // ---------- history ----------
    void history() {
        LinearLayout root = newPage();
        label(root, "Ride history", 24, TEXT, "sans-serif-medium", 0);
        List<Ride> rs = parseRides(sp);
        double total = 0;
        for (Ride x : rs) total += x.fare;
        label(root, rs.isEmpty() ? L("Nothing here yet.") : rs.size() + " " + L("rides") + "  ·  ₹" + Math.round(total) + " " + L("total"), 14, SUBTLE, "sans-serif", 4);
        weekCard(root, rs);

        String sk = sp.getString("skips", "");
        if (!sk.isEmpty()) {
            section(root, "Recently skipped", null, null);
            LinearLayout c = card(root, 0);
            SimpleDateFormat f = new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());
            String[] lines = sk.split("\n");
            for (int i = 0; i < Math.min(5, lines.length); i++) {
                String[] r = lines[i].split("\\|", 3);
                if (r.length < 3) continue;
                if (i > 0) divider(c, 10);
                label(c, "₹" + n(Double.parseDouble(r[2])) + "  ·  " + f.format(new Date(Long.parseLong(r[0]))), 14, TEXT, "sans-serif-medium", i > 0 ? 10 : 0);
                label(c, r[1], 13, SUBTLE, "sans-serif", 2);
            }
        }

        section(root, "All rides", null, null);
        rideList(card(root, 0), 50);
        if (!rs.isEmpty()) {
            button(root, "Export as CSV", false, 16).setOnClickListener(v -> exportCsv(rs));
            button(root, "Clear history", false, 12).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(L("Clear all accepted-ride history?"))
                .setNegativeButton(L("Cancel"), null)
                .setPositiveButton(L("Clear"), (d, w) -> { sp.edit().remove("hist").remove("skips").apply(); history(); }).show());
        }
    }

    // ---------- settings ----------
    void settings() {
        LinearLayout root = newPage();
        label(root, "Settings", 24, TEXT, "sans-serif-medium", 0);
        label(root, "Choose which rides are accepted.", 14, SUBTLE, "sans-serif", 4);
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

        section(root, "Ride filters", null, null);
        LinearLayout f = card(root, 0);
        EditText fare = field(f, "Minimum fare (₹)", "Rides below this fare are skipped. Use 0 for no limit.", sp.getString("fare", "0"), num, 0);
        EditText dist = field(f, "Maximum pickup distance (km)", "Rides with a farther pickup are skipped. Use 0 for no limit.", sp.getString("dist", "0"), num, 20);
        EditText perkm = field(f, "Minimum earnings per km (₹)", "Skips rides that pay less per km. Use 0 for no limit.", sp.getString("perkm", "0"), num, 20);
        EditText delay = field(f, "Response delay (seconds)", "Wait this long before accepting.", n(pref(sp, "delay", 300) / 1000), num, 20);
        EditText near = field(f, "Instant-accept radius (km)", "Rides with pickup this close are accepted with no delay. Use 0 to turn off.", sp.getString("near", "0"), num, 20);

        section(root, "Earnings targets", null, null);
        LinearLayout e = card(root, 0);
        String t0 = sp.getString("target", "8");
        EditText tBike = field(e, "Bike / scooty target (₹ per km)", "Rides at or above this are marked Worth it.", sp.getString("target_Bike", t0), num, 0);
        EditText tAuto = field(e, "Auto target (₹ per km)", "Rides at or above this are marked Worth it.", sp.getString("target_Auto", t0), num, 20);
        EditText tCar = field(e, "Car target (₹ per km)", "Rides at or above this are marked Worth it.", sp.getString("target_Car", t0), num, 20);

        section(root, "Vehicle & fuel", null, null);
        LinearLayout vc = card(root, 0);
        label(vc, "My vehicle", 14, TEXT, "sans-serif-medium", 0);
        String[] veh = {sp.getString("vehicle", "Bike")};
        segmented(vc, VNAMES, Math.max(0, Arrays.asList(TYPES).indexOf(veh[0])), 8, i -> veh[0] = TYPES[i]);
        label(vc, "Used when the ride type isn't shown on the request.", 13, SUBTLE, "sans-serif", 4);
        EditText petrol = field(vc, "Fuel price (₹ per litre)", "Current fuel price in your city.", sp.getString("petrol", "105"), num, 20);
        EditText mBike = field(vc, "Bike / scooty mileage (km per litre)", "Used to work out fuel cost per km.", sp.getString("mile_Bike", "45"), num, 20);
        EditText mAuto = field(vc, "Auto mileage (km per litre)", "Used to work out fuel cost per km.", sp.getString("mile_Auto", "25"), num, 20);
        EditText mCar = field(vc, "Car mileage (km per litre)", "Used to work out fuel cost per km.", sp.getString("mile_Car", "15"), num, 20);
        EditText fixed = field(vc, "Monthly fixed costs (₹)", "EMI, insurance, phone plan. Spread evenly across the days in Money.", sp.getString("fixed", "0"), num, 20);

        section(root, "Goals & alerts", null, null);
        LinearLayout ga = card(root, 0);
        EditText goal = field(ga, "Daily earnings goal (₹)", "Shows progress on the home screen. Use 0 to hide.", sp.getString("goal", "0"), num, 0);
        CheckBox alert = check("Vibrate and beep for Worth it rides", sp.getBoolean("alert", true));
        CheckBox reject = check("Decline rides rated Low", sp.getBoolean("reject", false));
        CheckBox peak = check("Be stricter in peak hours", sp.getBoolean("peak_on", false));
        LinearLayout.LayoutParams cbl = new LinearLayout.LayoutParams(-1, -2);
        cbl.topMargin = dp(12);
        ga.addView(alert, cbl);
        ga.addView(reject, new LinearLayout.LayoutParams(-1, -2));
        label(ga, "Taps Reject on Low rides. Off by default; a wrong match would lose you a ride.", 13, SUBTLE, "sans-serif", 0);
        ga.addView(peak, cbl);
        EditText pFrom = field(ga, "Peak hours start (0-23)", "Hour of day, 24-hour clock.", sp.getString("peak_from", "17"), InputType.TYPE_CLASS_NUMBER, 8);
        EditText pTo = field(ga, "Peak hours end (0-23)", "Hour of day, 24-hour clock.", sp.getString("peak_to", "21"), InputType.TYPE_CLASS_NUMBER, 20);
        EditText pPlus = field(ga, "Extra target in peak hours (₹ per km)", "Added to your targets during peak hours.", sp.getString("peak_plus", "2"), num, 20);

        section(root, "Display & language", null, null);
        LinearLayout dc = card(root, 0);
        label(dc, "Language", 14, TEXT, "sans-serif-medium", 0);
        String[] lang = {sp.getString("lang", "en")};
        segmented(dc, LANG_NAMES, Math.max(0, Arrays.asList(LANGS).indexOf(lang[0])), 8, i -> lang[0] = LANGS[i]);
        label(dc, "Earnings chip position", 14, TEXT, "sans-serif-medium", 20);
        String[] pos = {sp.getString("chip_pos", "bottom")};
        segmented(dc, new String[]{"Top", "Bottom"}, "top".equals(pos[0]) ? 0 : 1, 8, i -> pos[0] = i == 0 ? "top" : "bottom");
        CheckBox bubble = check("Show the floating Vamsify bubble over driver apps", sp.getBoolean("bubble", true));
        CheckBox large = check("Large text and bigger buttons", sp.getBoolean("large", false));
        CheckBox onehand = check("One-hand layout (main controls at the bottom)", sp.getBoolean("onehand", false));
        CheckBox notif = check("Pause and resume notification", sp.getBoolean("notif", true));
        CheckBox summary = check("Daily summary at 9 PM", sp.getBoolean("summary", true));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(12);
        dc.addView(bubble, dl);
        for (CheckBox c : new CheckBox[]{large, onehand, notif, summary}) dc.addView(c, new LinearLayout.LayoutParams(-1, -2));

        section(root, "Driver apps", null, null);
        LinearLayout a = card(root, 0);
        CheckBox[] boxes = appChecks(a);

        Button save = button(root, "Save changes", true, 24);
        save.setOnClickListener(v -> {
            boolean redraw = !lang[0].equals(sp.getString("lang", "en")) || large.isChecked() != sp.getBoolean("large", false)
                || onehand.isChecked() != sp.getBoolean("onehand", false);
            double secs = 0.3;
            try { secs = Double.parseDouble(delay.getText().toString()); } catch (Exception ex) { /* keep default */ }
            boolean filtersChanged = !fare.getText().toString().equals(sp.getString("fare", "0"))
                || !dist.getText().toString().equals(sp.getString("dist", "0")) || !perkm.getText().toString().equals(sp.getString("perkm", "0"));
            SharedPreferences.Editor ed = sp.edit()
                .putString("fare", fare.getText().toString()).putString("dist", dist.getText().toString())
                .putString("perkm", perkm.getText().toString()).putString("delay", String.valueOf((long) (secs * 1000)))
                .putString("near", near.getText().toString())
                .putString("target_Bike", tBike.getText().toString()).putString("target_Auto", tAuto.getText().toString())
                .putString("target_Car", tCar.getText().toString())
                .putString("vehicle", veh[0]).putString("petrol", petrol.getText().toString())
                .putString("mile_Bike", mBike.getText().toString()).putString("mile_Auto", mAuto.getText().toString())
                .putString("mile_Car", mCar.getText().toString()).putString("fixed", fixed.getText().toString())
                .putString("goal", goal.getText().toString())
                .putBoolean("alert", alert.isChecked()).putBoolean("reject", reject.isChecked()).putBoolean("peak_on", peak.isChecked())
                .putString("peak_from", pFrom.getText().toString()).putString("peak_to", pTo.getText().toString())
                .putString("peak_plus", pPlus.getText().toString())
                .putString("lang", lang[0]).putString("chip_pos", pos[0])
                .putBoolean("bubble", bubble.isChecked()).putBoolean("large", large.isChecked())
                .putBoolean("onehand", onehand.isChecked()).putBoolean("notif", notif.isChecked())
                .putBoolean("summary", summary.isChecked()).putString("pkgs", pkgsFrom(boxes));
            if (filtersChanged) ed.putString("profile", ""); // edited by hand, so no preset applies
            ed.apply();
            if (notif.isChecked() || summary.isChecked()) askNotificationPermission();
            scheduleSummary(this);
            Toast.makeText(this, L("Changes saved"), Toast.LENGTH_SHORT).show();
            if (redraw) recreate();
        });
    }

    // =====================================================================
    // shell
    // =====================================================================
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(SURFACE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout frame = col();
        content = new FrameLayout(this);
        frame.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = col();
        View line = new View(this);
        line.setBackgroundColor(LINE);
        bottom.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        nav = new LinearLayout(this);
        nav.setBackgroundColor(SURFACE);
        String[] names = {"Home", "History", "Money", "Settings"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_history, R.drawable.ic_wallet, R.drawable.ic_settings};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = col();
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(10), 0, dp(10));
            ImageView ic = new ImageView(this);
            ic.setImageResource(icons[i]);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(24), dp(24));
            il.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(ic, il);
            TextView t = tv(names[i], 12, SUBTLE, "sans-serif-medium");
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(2), 0, 0);
            item.addView(t, new LinearLayout.LayoutParams(-1, -2));
            item.setOnClickListener(v -> show(idx));
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        bottom.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        frame.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(frame);
        scheduleSummary(this);
        if (sp.getBoolean("onboarded", false)) show(0); else onboard();
    }

    @Override protected void onResume() {
        super.onResume();
        if (sp.getBoolean("onboarded", false)) show(tab); else onboard(); // refresh service status and history
    }
}
