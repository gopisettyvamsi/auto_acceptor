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
import android.graphics.Bitmap;

public class MainActivity extends Activity {
    static final String PREFS = "p";
    static final String DEFAULT_PKGS = "com.rapido.rider,com.rapido.captain,com.ubercab.driver,com.olacabs.oladriver";
    // user-facing app names -> internal package ids (never shown on screen)
    static final String[][] APPS = {
        {"Rapido", "com.rapido.rider,com.rapido.captain"},
        {"Uber Driver", "com.ubercab.driver"},
        {"Ola Driver", "com.olacabs.oladriver"}};
    static {
        I18n.a("Show trip details after accepting", "स्वीकार करने के बाद यात्रा विवरण दिखाएं", "అంగీకరించిన తరువాత ట్రిప్ వివరాలు చూపించు");
    }
    static final String[] LANGS = {"en", "hi", "te", "ta", "kn", "mr", "bn"};
    static final String[] LANG_NAMES = {"English", "\u0939\u093F\u0928\u094D\u0926\u0940", "\u0C24\u0C46\u0C32\u0C41\u0C17\u0C41",
        "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD", "\u0C95\u0CA8\u0CCD\u0CA8\u0CA1", "\u092E\u0930\u093E\u0920\u0940", "\u09AC\u09BE\u0982\u09B2\u09BE"};

    // palette: one accent, neutral greys. Dark by default; applyTheme() swaps the values (light = black, bold text).
    static int ACCENT, ACCENT_SOFT, BG, SURFACE, LINE, TEXT, SUBTLE, OK, WARN, HERO,
            INPUT_LINE, OK_SOFT, WARN_SOFT, BAR, THUMB_OFF, TRACK_OFF;
    static final int ON_HERO = 0xB3FFFFFF;
    static boolean dark = true;

    static void applyTheme(SharedPreferences sp) {
        dark = sp.getBoolean("dark", true);
        if (dark) {
            ACCENT = 0xFF6B93FF; ACCENT_SOFT = 0xFF182446; BG = 0xFF080C16; SURFACE = 0xFF111827; LINE = 0xFF222D45;
            TEXT = 0xFFF3F6FC; SUBTLE = 0xFFD2D8E4; OK = 0xFF3DD68C; WARN = 0xFFFF6B63; HERO = 0xFF152036;
            INPUT_LINE = 0xFF34415F; OK_SOFT = 0xFF173A2A; WARN_SOFT = 0xFF45201E; BAR = 0xFF2C3A5E;
            THUMB_OFF = 0xFFAEB7C8; TRACK_OFF = 0xFF3A4560;
        } else {
            ACCENT = 0xFF2345C9; ACCENT_SOFT = 0xFFE8EEFF; BG = 0xFFF2F4F9; SURFACE = Color.WHITE; LINE = 0xFFE3E7EF;
            TEXT = 0xFF0B0F19; SUBTLE = 0xFF1F2937; OK = 0xFF1E8E4E; WARN = 0xFFB3261E; HERO = 0xFF0F1B3D;
            INPUT_LINE = 0xFFC4C9D2; OK_SOFT = 0xFFE3F4EA; WARN_SOFT = 0xFFFCE8E6; BAR = 0xFFC7D2F5;
            THUMB_OFF = 0xFFF2F4F7; TRACK_OFF = 0xFFD0D5DD;
        }
        if (sp.getBoolean("hc", false)) { // high contrast: secondary text as strong as the main text, stronger outlines
            SUBTLE = TEXT; LINE = dark ? 0xFF8A94A8 : 0xFF4B5563; INPUT_LINE = dark ? 0xFFAEB7C8 : 0xFF1F2937;
        }
    }

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

    // Distance unit for the filters and for display: kilometers (default) or meters. Everything is stored in km.
    static boolean meters(SharedPreferences sp) { return "m".equals(sp.getString("dist_unit", "km")); }
    static String unitName(SharedPreferences sp) { return meters(sp) ? "m" : "km"; }
    static double toUi(SharedPreferences sp, double km) { return meters(sp) ? km * 1000 : km; }
    static double fromUi(SharedPreferences sp, double v) { return meters(sp) ? v / 1000 : v; }

    /** At most dp decimals, no trailing zeros. */
    static String num(double v, int dp) {
        String s = String.format(Locale.US, "%." + dp + "f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
    static String n2(double v) { return num(v, 2); }
    static String n4(double v) { return num(v, 4); }
    static String fmtDist(SharedPreferences sp, double km) { return meters(sp) ? Math.round(km * 1000) + " m" : n2(km) + " km"; }
    static double parse(EditText e) { try { return Double.parseDouble(e.getText().toString()); } catch (Exception ex) { return 0; } }

    void saveFilters(EditText[] f) {
        sp.edit().putString("fare", f[0].getText().toString())
            .putString("dist", n4(fromUi(sp, parse(f[1])))).putString("dropmax", n4(fromUi(sp, parse(f[2]))))
            .putString("perkm", f[3].getText().toString())
            .putString("rejlo", f[4].getText().toString()).putString("rejhi", f[5].getText().toString())
            .putString("maxfare", f[6].getText().toString()).putString("mintrip", n4(fromUi(sp, parse(f[7])))).putString("maxtrip", n4(fromUi(sp, parse(f[8]))))
            .putString("maxmins", f[9].getText().toString()).putString("minprofit", f[10].getText().toString()).putString("minnetkm", f[11].getText().toString())
            .putString("minhr", f[12].getText().toString()).putString("dayrides", f[13].getText().toString()).putString("daykm", n4(fromUi(sp, parse(f[14])))).apply();
    }

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
        return (mileage > 0 ? pref(sp, "petrol", 105) / mileage : 0) + pref(sp, "maint", 0);
    }

    /** Ride type from the offer screen text; falls back to the user's own vehicle. */
    static String rideType(SharedPreferences sp, String screen) {
        String s = screen.toLowerCase(Locale.ROOT);
        if (BIKE.matcher(s).find()) return "Bike";
        if (AUTO.matcher(s).find()) return "Auto";
        if (CAR.matcher(s).find()) return "Car";
        return sp.getString("vehicle", "Bike");
    }

    /** Target earnings per km for a ride type. */
    static double targetFor(SharedPreferences sp, String type) {
        return pref(sp, "target_" + type, pref(sp, "target", 8));
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

    // history: one record per line "time|app|fare|pickup|drop|type", newest first, capped at 1000
    static void addHistory(SharedPreferences sp, String pkg, double fare, Double pickup, Double drop, String type) {
        String rec = System.currentTimeMillis() + "|" + appName(pkg) + "|" + fare + "|"
            + (pickup == null ? "" : pickup) + "|" + (drop == null ? "" : drop) + "|" + type;
        String[] old = sp.getString("hist", "").split("\n");
        StringBuilder sb = new StringBuilder(rec);
        for (int i = 0; i < Math.min(old.length, 999); i++) if (!old[i].isEmpty()) sb.append('\n').append(old[i]);
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
        for (int i = 0; i < Math.min(old.length, 299); i++) if (!old[i].isEmpty()) sb.append('\n').append(old[i]);
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
    static final float[] TEXT_SCALES = {1f, 1.15f, 1.3f, 1.5f};
    float scale() {
        int i = sp.contains("text_scale") ? PrefsGuard.getInt(sp, "text_scale", 0) : (sp.getBoolean("large", false) ? 1 : 0);
        return TEXT_SCALES[Math.max(0, Math.min(3, i))];
    }
    float taps() { return sp.getBoolean("big_taps", false) ? 1.25f : 1f; }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    /** Deep-navy to blue sweep for the big earnings cards. */
    GradientDrawable heroShape() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{HERO, dark ? 0xFF22408F : 0xFF2347B8});
        g.setCornerRadius(dp(24));
        return g;
    }

    /** Primary buttons keep a fixed blue so white text stays readable in both themes. */
    GradientDrawable primaryShape() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF3D6BFF, 0xFF2A4FD6});
        g.setCornerRadius(dp(14));
        return g;
    }

    TextView tv(String s, int size, int color, String family) {
        TextView t = new TextView(this);
        t.setText(L(s)); t.setTextSize(size * scale()); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-medium".equals(family) ? "sans-serif" : "sans-serif-medium",
            "sans-serif-medium".equals(family) ? Typeface.BOLD : Typeface.NORMAL));
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
        i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        f.addView(i, new FrameLayout.LayoutParams(dp(size / 2), dp(size / 2), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return f;
    }

    LinearLayout card(ViewGroup root, int top) {
        LinearLayout c = col();
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(shape(SURFACE, 18, LINE));
        if (!dark) c.setElevation(dp(2)); // dark theme relies on the outline; light gets a soft lift
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
        sv.setAlpha(0f); sv.setTranslationY(dp(10)); sv.animate().alpha(1f).translationY(0f).setDuration(180).start();
        return root;
    }

    /** Small caps heading with an optional action link on the right. */
    void section(LinearLayout root, String title, String action, Runnable onAction) {
        LinearLayout r = row();
        View bar = new View(this);
        bar.setBackground(shape(ACCENT, 2, 0));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(3), dp(13));
        bl.rightMargin = dp(8);
        r.addView(bar, bl);
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

    static int vehicleIcon(String type) {
        return "Bike".equals(type) ? R.drawable.ic_bike : "Auto".equals(type) ? R.drawable.ic_auto : R.drawable.ic_car;
    }

    TextView chip(int v) {
        int[] fg = dark ? new int[]{0xFFFF8A80, 0xFFFFD54F, 0xFF5BD68A} : new int[]{0xFFB3261E, 0xFF8A5A00, 0xFF1E8E4E};
        int[] bg = dark ? new int[]{0x33EF5350, 0x33FFC107, 0x332EAD5B} : new int[]{0xFFFCE8E6, 0xFFFFF1D6, 0xFFE3F4EA};
        String[] name = {"Low", "Average", "Worth it"};
        TextView t = tv(name[v], 12, fg[v], "sans-serif-medium");
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(shape(bg[v], 12, 0));
        return t;
    }

    Switch toggle(boolean checked) {
        Switch s = new Switch(this);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, THUMB_OFF}));
        s.setTrackTintList(new ColorStateList(st, new int[]{(ACCENT & 0x00FFFFFF) | 0x66000000, TRACK_OFF}));
        s.setChecked(checked);
        return s;
    }

    CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(L(text)); c.setChecked(checked); c.setTextSize(16 * scale()); c.setTextColor(TEXT);
        c.setButtonTintList(ColorStateList.valueOf(ACCENT));
        c.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return c;
    }

    EditText field(LinearLayout p, String name, String help, String val, int type, int top) {
        TextView fieldLabel = label(p, name, 14, TEXT, "sans-serif-medium", top);
        EditText e = new EditText(this);
        e.setId(View.generateViewId());
        fieldLabel.setLabelFor(e.getId());
        e.setInputType(type); e.setText(val); e.setSingleLine(true);
        e.setTextColor(TEXT); e.setTextSize(16 * scale());
        e.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        e.setBackground(shape(dark ? BG : 0xFFF8F9FC, 12, INPUT_LINE));
        e.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(Math.round(48 * scale() * taps())));
        lp.topMargin = dp(8);
        p.addView(e, lp);
        label(p, help, 13, SUBTLE, "sans-serif", 4);
        return e;
    }

    Button button(LinearLayout p, String s, boolean primary, int top) {
        Button b = new Button(this);
        b.setText(L(s)); b.setAllCaps(false); b.setTextSize(15 * scale());
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setTextColor(primary ? Color.WHITE : ACCENT);
        android.graphics.drawable.Drawable face = primary ? primaryShape() : shape(SURFACE, 14, ACCENT);
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(primary ? 0x44FFFFFF : (ACCENT & 0x00FFFFFF) | 0x33000000), face, shape(Color.WHITE, 14, 0)));
        b.setStateListAnimator(null);
        b.setElevation(dp(primary ? 4 : 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(Math.round(50 * scale() * taps())));
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
                o[i].setBackground(shape(s ? ACCENT : SURFACE, 12, s ? 0 : INPUT_LINE));
                o[i].setTextColor(s ? Color.WHITE : TEXT);
                o[i].setContentDescription(L(names[i]) + (s ? ", " + L("selected") : ""));
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
            r.addView(badge(vehicleIcon(x.type), ACCENT_SOFT, ACCENT, 44, 0));
            LinearLayout c = col();
            c.setPadding(dp(12), 0, dp(8), 0);
            String money = "₹" + Math.round(x.fare);
            label(c, Double.isNaN(pk) ? money : money + "  ·  ₹" + String.format(Locale.US, "%.2f", pk) + "/km", 16, TEXT, "sans-serif-medium", 0);
            label(c, x.app + "  ·  " + L(x.type) + "  ·  " + f.format(new Date(x.t)), 13, SUBTLE, "sans-serif", 2);
            if (!Double.isNaN(pk)) label(c, fmtDist(sp, x.km()) + "  ·  " + L("fuel") + " ₹" + Math.round(fuel)
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
            p.setTypeface(Typeface.DEFAULT_BOLD);
            for (int i = 0; i < v.length; i++) {
                float bh = (float) ((h - foot - dp(18)) * v[i] / max), x = slot * i + (slot - bw) / 2, top = h - foot - bh;
                p.setColor(i == v.length - 1 ? ACCENT : BAR);
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
            {km > 0 ? fmtDist(sp, km) : "\u2013", "Total km"}};
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
        hero.setBackground(heroShape()); hero.setElevation(dp(6));
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

        insights(root);

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
            item.setBackground(i == t ? shape(ACCENT_SOFT, 16, 0) : null);
            ((ImageView) item.getChildAt(0)).setColorFilter(c);
            ((TextView) item.getChildAt(1)).setTextColor(c);
        }
        if (t == 0) home(); else if (t == 1) history(); else if (t == 2) money(); else settings();
    }

    // ---------- first-run setup ----------
    static {
        I18n.a("Choose your language", "अपनी भाषा चुनें", "మీ భాషను ఎంచుకోండి");
        I18n.a("Know which rides are worth it. Accept the right ones automatically.", "जानें कौन सी राइड फायदेमंद है। सही राइड अपने आप स्वीकार करें।", "ఏ రైడ్‌లు లాభదాయకమో తెలుసుకోండి. సరైనవాటిని ఆటోమేటిక్‌గా అంగీకరించండి.");
        I18n.a("Continue", "जारी रखें", "కొనసాగించండి");
        I18n.a("Back", "वापस", "వెనుకకు");
        I18n.a("Get started", "शुरू करें", "ప్రారంభించండి");
        I18n.a("Step %s of %s", "चरण %s / %s", "దశ %s / %s");
        I18n.a("Your vehicle", "आपका वाहन", "మీ వాహనం");
        I18n.a("We use this to estimate fuel cost and profit on every ride.", "हम इसका उपयोग हर राइड पर ईंधन खर्च और मुनाफा आंकने में करते हैं।", "ప్రతి రైడ్‌పై ఇంధన ఖర్చు, లాభం అంచనా వేయడానికి దీన్ని వాడతాం.");
        I18n.a("%s km per litre", "%s किमी प्रति लीटर", "లీటరుకు %s కిమీ");
        I18n.a("Enable accessibility", "एक्सेसिबिलिटी चालू करें", "యాక్సెసిబిలిటీని ఆన్ చేయండి");
        I18n.a("Vamsify reads ride requests on screen to rate them and, if you choose, accept them. Nothing leaves your phone.", "Vamsify स्क्रीन पर राइड रिक्वेस्ट पढ़कर उन्हें रेट करता है और आप चाहें तो स्वीकार भी करता है। कुछ भी आपके फ़ोन से बाहर नहीं जाता।", "Vamsify స్క్రీన్‌పై రైడ్ రిక్వెస్ట్‌లను చదివి రేట్ చేస్తుంది, మీరు కోరితే అంగీకరిస్తుంది. ఏదీ మీ ఫోన్ దాటి వెళ్లదు.");
        I18n.a("Tap the button below and open Installed apps.", "नीचे का बटन दबाएँ और इंस्टॉल्ड ऐप्स खोलें।", "క్రింది బటన్ నొక్కి ఇన్‌స్టాల్ చేసిన యాప్‌లు తెరవండి.");
        I18n.a("Choose Vamsify and switch it on.", "Vamsify चुनें और इसे चालू करें।", "Vamsify ఎంచుకుని ఆన్ చేయండి.");
        I18n.a("If it is greyed out, open App info, tap the menu at the top right and choose Allow restricted settings, then try again.", "अगर यह ग्रे है, तो ऐप इंफ़ो खोलें, ऊपर दाएँ मेनू पर टैप करें और Allow restricted settings चुनें, फिर से कोशिश करें।", "గ్రే అయితే, యాప్ ఇన్ఫో తెరిచి, పై కుడి మెనూ నొక్కి Allow restricted settings ఎంచుకుని మళ్లీ ప్రయత్నించండి.");
        I18n.a("Service is on", "सेवा चालू है", "సేవ ఆన్‌లో ఉంది");
        I18n.a("Service is not on yet", "सेवा अभी चालू नहीं है", "సేవ ఇంకా ఆన్ కాలేదు");
        I18n.a("You can also do this later from Home.", "आप यह बाद में होम से भी कर सकते हैं।", "దీన్ని తర్వాత హోమ్ నుండి కూడా చేయవచ్చు.");
    }

    static final int ONB_STEPS = 4; // language, vehicle and fuel, service, driver apps

    void logoInto(ImageView v) {
        try (java.io.InputStream in = getAssets().open("logo.png")) {
            v.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (java.io.IOException ex) { /* name only */ }
    }

    /** A large selectable card (radio style): title, optional subtitle, check mark on the right. */
    void choice(LinearLayout parent, int icon, String title, String sub, boolean selected, Runnable onTap) {
        LinearLayout c = row();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(selected ? ACCENT_SOFT : SURFACE);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(selected ? 2 : 1), selected ? ACCENT : LINE);
        c.setBackground(g);
        if (icon != 0) { // vehicle cards carry their own icon
            FrameLayout ib = badge(icon, selected ? SURFACE : ACCENT_SOFT, ACCENT, 44, 0);
            ((LinearLayout.LayoutParams) ib.getLayoutParams()).rightMargin = dp(14);
            c.addView(ib);
        }
        LinearLayout t = col();
        label(t, title, 18, TEXT, "sans-serif-medium", 0);
        if (sub != null) label(t, sub, 13, SUBTLE, "sans-serif", 2);
        c.addView(t, weight());
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_check_circle);
        mark.setColorFilter(selected ? ACCENT : LINE);
        c.addView(mark, new LinearLayout.LayoutParams(dp(26), dp(26)));
        c.setOnClickListener(v -> onTap.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        parent.addView(c, lp);
    }

    void numbered(LinearLayout parent, int n, String text, int top) {
        LinearLayout r = row();
        TextView num = tv(String.valueOf(n), 14, ACCENT, "sans-serif-medium");
        num.setGravity(Gravity.CENTER);
        num.setBackground(shape(ACCENT_SOFT, 14, 0));
        r.addView(num, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView t = tv(text, 15, TEXT, "sans-serif");
        t.setPadding(dp(12), 0, 0, 0);
        r.addView(t, weight());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(r, lp);
    }

    void onboard() {
        bottom.setVisibility(View.GONE);
        content.removeAllViews();
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        LinearLayout shell = col();
        shell.setBackgroundColor(BG);

        // top bar: brand and progress
        LinearLayout top = col();
        top.setPadding(dp(20), dp(18), dp(20), dp(6));
        LinearLayout brand = row();
        ImageView mini = new ImageView(this);
        logoInto(mini);
        brand.addView(mini, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView wordmark = tv("Vamsify", 17, TEXT, "sans-serif-medium");
        wordmark.setPadding(dp(10), 0, 0, 0);
        brand.addView(wordmark);
        top.addView(brand);
        LinearLayout bar = row();
        for (int s = 0; s < ONB_STEPS; s++) {
            View seg = new View(this);
            seg.setBackground(shape(s <= step ? ACCENT : LINE, 2, 0));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(4), 1);
            if (s > 0) sl.leftMargin = dp(6);
            bar.addView(seg, sl);
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = dp(16);
        top.addView(bar, bl);
        label(top, F("Step %s of %s", step + 1, ONB_STEPS), 12, SUBTLE, "sans-serif-medium", 8);
        shell.addView(top);

        // scrolling body
        ScrollView sv = new ScrollView(this);
        LinearLayout body = col();
        body.setPadding(dp(20), dp(12), dp(20), dp(24));
        sv.addView(body);
        shell.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        final Runnable[] next = new Runnable[1];
        String cta = "Continue";
        if (step == 0) {
            ImageView hero = new ImageView(this);
            logoInto(hero);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(92), dp(92));
            hl.gravity = Gravity.CENTER_HORIZONTAL;
            hl.topMargin = dp(8);
            body.addView(hero, hl);
            label(body, "Welcome to Vamsify", 28, TEXT, "sans-serif-medium", 20).setGravity(Gravity.CENTER);
            label(body, "Know which rides are worth it. Accept the right ones automatically.", 15, SUBTLE, "sans-serif", 8).setGravity(Gravity.CENTER);
            label(body, "Choose your language", 13, SUBTLE, "sans-serif-medium", 32);
            String cur = sp.getString("lang", "en");
            for (int i = 0; i < LANGS.length; i++) {
                final int idx = i;
                choice(body, 0, LANG_NAMES[i], null, LANGS[i].equals(cur), () -> {
                    sp.edit().putString("lang", LANGS[idx]).apply();
                    recreate(); // redraws the whole flow in the chosen language
                });
            }
            next[0] = () -> { step = 1; onboard(); };
        } else if (step == 1) {
            label(body, "Your vehicle", 26, TEXT, "sans-serif-medium", 8);
            label(body, "We use this to estimate fuel cost and profit on every ride.", 15, SUBTLE, "sans-serif", 6);
            final EditText[] fuel = new EditText[1];
            String curV = sp.getString("vehicle", "Bike");
            for (int i = 0; i < TYPES.length; i++) {
                final int idx = i;
                choice(body, vehicleIcon(TYPES[i]), L(VNAMES[i]), F("%s km per litre", n(pref(sp, "mile_" + TYPES[i], DEF_MILEAGE[i]))), TYPES[i].equals(curV), () -> {
                    if (fuel[0] != null) sp.edit().putString("petrol", fuel[0].getText().toString()).apply(); // keep what was typed
                    sp.edit().putString("vehicle", TYPES[idx]).apply();
                    onboard();
                });
            }
            fuel[0] = field(body, "Fuel price (₹ per litre)", "Current fuel price in your city.", sp.getString("petrol", "105"), num, 24);
            next[0] = () -> { sp.edit().putString("petrol", fuel[0].getText().toString()).apply(); step = 2; onboard(); };
        } else if (step == 2) {
            label(body, "Enable accessibility", 26, TEXT, "sans-serif-medium", 8);
            label(body, "Vamsify reads ride requests on screen to rate them and, if you choose, accept them. Nothing leaves your phone.", 15, SUBTLE, "sans-serif", 6);
            boolean on = AcceptService.connected;
            LinearLayout st = card(body, 20);
            LinearLayout sr = row();
            sr.addView(badge(on ? R.drawable.ic_check_circle : R.drawable.ic_error, on ? OK_SOFT : WARN_SOFT, on ? OK : WARN, 40, 0));
            LinearLayout sc = col();
            sc.setPadding(dp(12), 0, 0, 0);
            label(sc, on ? "Service is on" : "Service is not on yet", 16, TEXT, "sans-serif-medium", 0);
            if (!on) label(sc, "You can also do this later from Home.", 12, SUBTLE, "sans-serif", 0);
            sr.addView(sc, weight());
            st.addView(sr);
            LinearLayout steps = card(body, 12);
            numbered(steps, 1, L("Tap the button below and open Installed apps."), 0);
            numbered(steps, 2, L("Choose Vamsify and switch it on."), 14);
            numbered(steps, 3, L("If it is greyed out, open App info, tap the menu at the top right and choose Allow restricted settings, then try again."), 14);
            button(body, "Open Accessibility settings", false, 16).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            next[0] = () -> { step = 3; onboard(); };
        } else {
            label(body, "Choose your driver apps", 26, TEXT, "sans-serif-medium", 8);
            label(body, "Vamsify only works inside the apps you select.", 15, SUBTLE, "sans-serif", 6);
            LinearLayout c = card(body, 20);
            final CheckBox[] boxes = appChecks(c);
            cta = "Get started";
            next[0] = () -> {
                sp.edit().putString("pkgs", pkgsFrom(boxes)).putBoolean("onboarded", true).apply();
                askNotificationPermission();
                scheduleSummary(this);
                show(0);
            };
        }

        // pinned footer: primary action, and Back from step 2 on
        LinearLayout footWrap = col();
        View rule = new View(this);
        rule.setBackgroundColor(LINE);
        footWrap.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout foot = col();
        foot.setBackgroundColor(SURFACE);
        foot.setPadding(dp(20), dp(12), dp(20), dp(12));
        button(foot, cta, true, 0).setOnClickListener(v -> next[0].run());
        if (step > 0) {
            TextView back = tv("Back", 15, ACCENT, "sans-serif-medium");
            back.setGravity(Gravity.CENTER);
            back.setPadding(0, dp(12), 0, dp(4));
            back.setOnClickListener(v -> { step--; onboard(); });
            foot.addView(back, new LinearLayout.LayoutParams(-1, -2));
        }
        footWrap.addView(foot);
        shell.addView(footWrap, new LinearLayout.LayoutParams(-1, -2));
        content.addView(shell, new FrameLayout.LayoutParams(-1, -1));
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
            r.addView(badge(R.drawable.ic_check_circle, OK_SOFT, OK, 40, 0));
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
            r.addView(badge(ok[i] ? R.drawable.ic_check_circle : R.drawable.ic_error, ok[i] ? OK_SOFT : WARN_SOFT, ok[i] ? OK : WARN, 36, 0));
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
        if (pref(sp, "dist", 0) > 0) parts.add(F("Pickup up to %s", fmtDist(sp, pref(sp, "dist", 0))));
        if (pref(sp, "dropmax", 0) > 0) parts.add(F("Drop up to %s", fmtDist(sp, pref(sp, "dropmax", 0))));
        if (pref(sp, "perkm", 0) > 0) parts.add(F("At least ₹%s per km", n(pref(sp, "perkm", 0))));
        return parts.isEmpty() ? L("No limits set") : android.text.TextUtils.join("  ·  ", parts);
    }

    String prioritySummary() {
        List<String> p = new ArrayList<>();
        if (sp.getBoolean("goto_on", false)) p.add("Go To");
        if (sp.getBoolean("stay_on", false)) p.add("Stay In");
        String[] sorts = {"Nearest", "Best ₹/km", "Top fare", "Most kept"};
        p.add(L(sorts[Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "sort", 0)))]));
        return android.text.TextUtils.join("  →  ", p);
    }

    String alertsSummary() {
        List<String> a = new ArrayList<>();
        if (sp.getBoolean("voice", false) || PrefsGuard.getInt(sp, "speak", 0) > 0) a.add(L("Spoken alerts"));
        if (sp.getBoolean("flash", false)) a.add(L("Flash"));
        if (sp.getBoolean("alert", true) || sp.getBoolean("vib_verdict", false)) a.add(L("Vibration"));
        return a.isEmpty() ? L("Off") : android.text.TextUtils.join("  ·  ", a);
    }

    /** One line of the "Your rules" card: icon, name, current value; tap to open that settings page. */
    void ruleRow(LinearLayout card, int icon, String name, String value, int page, boolean first) {
        if (!first) divider(card, 12);
        LinearLayout r = row();
        r.setPadding(0, dp(first ? 0 : 12), 0, 0);
        r.addView(badge(icon, ACCENT_SOFT, ACCENT, 36, 0));
        LinearLayout t = col();
        t.setPadding(dp(12), 0, dp(8), 0);
        label(t, name, 14, TEXT, "sans-serif-medium", 0);
        label(t, value, 12, SUBTLE, "sans-serif", 0);
        r.addView(t, weight());
        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.ic_chevron_right);
        chev.setColorFilter(SUBTLE);
        chev.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(chev, new LinearLayout.LayoutParams(dp(24), dp(24)));
        r.setOnClickListener(v -> { settingsPage = page; show(3); });
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Only shown when something stops Vamsify from working; each line fixes itself when tapped. */
    void issuesBanner(LinearLayout p, boolean svc, boolean batt, boolean app) {
        LinearLayout c = card(p, 16);
        c.setBackground(shape(WARN_SOFT, 16, WARN));
        LinearLayout h1 = row();
        h1.addView(badge(R.drawable.ic_error, SURFACE, WARN, 36, 0));
        TextView title = tv("Needs attention", 16, TEXT, "sans-serif-medium");
        title.setPadding(dp(12), 0, 0, 0);
        h1.addView(title, weight());
        c.addView(h1);
        String[][] items = {
            {"Accessibility service", "Off. Tap to turn it on."},
            {"Battery use", "Restricted. Tap to set Unrestricted so Android doesn't stop the service."},
            {"Driver app", "No selected driver app found on this phone."}};
        boolean[] ok = {svc, batt, app};
        for (int i = 0; i < 3; i++) {
            if (ok[i]) continue;
            final int idx = i;
            LinearLayout r = col();
            r.setPadding(dp(48), dp(10), 0, 0);
            label(r, items[i][0], 14, TEXT, "sans-serif-medium", 0);
            label(r, items[i][1], 12, SUBTLE, "sans-serif", 0);
            if (i < 2) r.setOnClickListener(v -> startActivity(new Intent(idx == 0
                ? Settings.ACTION_ACCESSIBILITY_SETTINGS : Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
            c.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /** A small tappable tile: icon over a short label. */
    void tile(LinearLayout row, int icon, String name, Runnable onTap, boolean first) {
        LinearLayout t = col();
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(8), dp(14), dp(8), dp(12));
        t.setBackground(shape(SURFACE, 14, LINE));
        t.addView(badge(icon, ACCENT_SOFT, ACCENT, 40, 0));
        label(t, name, 12, TEXT, "sans-serif-medium", 8).setGravity(Gravity.CENTER);
        t.setOnClickListener(v -> onTap.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (!first) lp.leftMargin = dp(10);
        row.addView(t, lp);
    }

    void home() {
        LinearLayout root = newPage();
        boolean svc = AcceptService.connected;
        boolean batt = ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
        boolean app = driverAppInstalled();
        boolean ready = svc && batt && app;
        boolean auto = sp.getBoolean("on", false);

        // ---- app bar: logo, name, live status, help
        LinearLayout head = row();
        ImageView logo = new ImageView(this);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        try (java.io.InputStream in = getAssets().open("logo.png")) {
            logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (java.io.IOException ex) { /* header just shows the name */ }
        head.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout names = col();
        names.setPadding(dp(12), 0, 0, 0);
        TextView name = label(names, "Vamsify", 20, TEXT, "sans-serif-medium", 0);
        LinearLayout stat = row();
        View sdot = new View(this);
        sdot.setBackground(shape(ready ? OK : WARN, 4, 0));
        stat.addView(sdot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView stxt = tv(ready ? "Ready" : "Needs attention", 12, ready ? OK : WARN, "sans-serif-medium");
        stxt.setPadding(dp(6), 0, 0, 0);
        stat.addView(stxt);
        names.addView(stat);
        head.addView(names, weight());
        FrameLayout helpBtn = badge(R.drawable.ic_help, SURFACE, TEXT, 44, LINE);
        helpBtn.setContentDescription(L("Help & guide"));
        helpBtn.setOnClickListener(v -> { settingsPage = 11; show(3); });
        head.addView(helpBtn);
        root.addView(head);
        // support only: long-press the name for technical details
        name.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setMessage("connected: " + AcceptService.connected
                + "\nevents: " + AcceptService.events + "\nlast app: " + AcceptService.lastPkg
                + "\n\n" + sp.getString("dump", "nothing read yet")).show();
            return true;
        });

        // ---- today's numbers
        double fare = 0, km = 0, keepSum = 0; int cnt = 0, worth = 0;
        for (Ride x : parseRides(sp)) {
            if (x.t < midnight()) continue;
            cnt++; fare += x.fare; km += x.km();
            keepSum += x.fare - costPerKm(sp, x.type) * x.km();
            if (!Double.isNaN(x.perKm()) && verdict(x.perKm(), targetFor(sp, x.type)) == 2) worth++;
        }

        // ---- earnings card, with the daily goal built in
        LinearLayout hero = col();
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(heroShape()); hero.setElevation(dp(6));
        LinearLayout top = row();
        top.addView(tv("Today's earnings", 14, ON_HERO, "sans-serif"), weight());
        TextView date = tv(new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date()), 12, ON_HERO, "sans-serif");
        top.addView(date);
        hero.addView(top);
        label(hero, "₹" + Math.round(fare), 38, Color.WHITE, "sans-serif-medium", 6);
        label(hero, cnt + " " + L(cnt == 1 ? "ride" : "rides") + " " + L("accepted today"), 13, ON_HERO, "sans-serif", 0);
        double goal = pref(sp, "goal", 0);
        if (goal > 0) {
            LinearLayout gr = row();
            gr.addView(tv("Daily goal", 12, ON_HERO, "sans-serif-medium"), weight());
            gr.addView(tv(fare >= goal ? L("Goal reached") : "₹" + Math.round(goal - fare) + " " + L("to go"), 12,
                fare >= goal ? 0xFF4ADE80 : Color.WHITE, "sans-serif-medium"));
            LinearLayout.LayoutParams grl = new LinearLayout.LayoutParams(-1, -2);
            grl.topMargin = dp(14);
            hero.addView(gr, grl);
            LinearLayout track = new LinearLayout(this);
            track.setWeightSum(1f);
            track.setBackground(shape(0x33FFFFFF, 4, 0));
            View bar = new View(this);
            bar.setBackground(shape(fare >= goal ? 0xFF4ADE80 : ACCENT, 4, 0));
            track.addView(bar, new LinearLayout.LayoutParams(0, -1, (float) Math.min(1, fare / goal)));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, dp(6));
            tl.topMargin = dp(6);
            hero.addView(track, tl);
        }
        divider(hero, 16);
        ((LinearLayout.LayoutParams) hero.getChildAt(hero.getChildCount() - 1).getLayoutParams()).bottomMargin = dp(14);
        hero.getChildAt(hero.getChildCount() - 1).setBackgroundColor(0x26FFFFFF);
        LinearLayout stats = row();
        String[][] st = {
            {km > 0 ? fmtDist(sp, km) : "–", "Total km"},
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
        hlp.topMargin = dp(18);
        root.addView(hero, hlp);

        LinearLayout controls = col(), info = col();

        // ---- the main control: auto-accept, and the earnings chip under it
        LinearLayout cc = card(controls, 16);
        LinearLayout r1 = row();
        r1.addView(badge(R.drawable.ic_bolt, auto ? OK_SOFT : ACCENT_SOFT, auto ? OK : ACCENT, 48, 0));
        LinearLayout t1 = col();
        t1.setPadding(dp(14), 0, dp(8), 0);
        label(t1, auto ? "Auto-accept is on" : "Auto-accept is paused", 17, TEXT, "sans-serif-medium", 0);
        label(t1, "Accept rides that match your filters", 13, SUBTLE, "sans-serif", 2);
        r1.addView(t1, weight());
        Switch on = toggle(auto);
        on.setContentDescription(L("Auto-accept"));
        on.setOnCheckedChangeListener((v, c) -> { sp.edit().putBoolean("on", c).apply(); v.post(this::home); });
        r1.addView(on);
        cc.addView(r1);
        divider(cc, 14);
        ((LinearLayout.LayoutParams) cc.getChildAt(cc.getChildCount() - 1).getLayoutParams()).bottomMargin = dp(14);
        LinearLayout r2 = row();
        r2.addView(badge(R.drawable.ic_trending_up, ACCENT_SOFT, ACCENT, 40, 0));
        LinearLayout t2 = col();
        t2.setPadding(dp(14), 0, dp(8), 0);
        label(t2, "Earnings chip", 15, TEXT, "sans-serif-medium", 0);
        label(t2, "Show fare, fuel cost and a worth-it rating on every ride request", 12, SUBTLE, "sans-serif", 2);
        r2.addView(t2, weight());
        Switch chipSw = toggle(sp.getBoolean("chip", true));
        chipSw.setContentDescription(L("Earnings chip"));
        chipSw.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("chip", c).apply());
        r2.addView(chipSw);
        cc.addView(r2);

        if (!ready) issuesBanner(controls, svc, batt, app);

        // ---- shortcuts
        LinearLayout tiles = row();
        tile(tiles, R.drawable.ic_car, "Sample ride", () -> {
            if (AcceptService.instance == null) Toast.makeText(this, L("Turn on the service first"), Toast.LENGTH_LONG).show();
            else startActivity(new Intent(this, SampleActivity.class)); // a test screen with several cards
        }, true);
        tile(tiles, R.drawable.ic_tune, "Filters", () -> { settingsPage = 0; show(3); }, false);
        tile(tiles, R.drawable.ic_palette, "Chip style", () -> { settingsPage = 9; show(3); }, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(16);
        controls.addView(tiles, tlp);

        // ---- the rules in force, so nothing is hidden in a menu
        section(info, "Your rules", "Edit", () -> { settingsPage = -1; show(3); });
        LinearLayout rules = card(info, 0);
        ruleRow(rules, R.drawable.ic_tune, L("Filters"), profileInfo(), 0, true);
        ruleRow(rules, R.drawable.ic_place, L("Priority"), prioritySummary(), 1, false);
        ruleRow(rules, R.drawable.ic_palette, L("Chip style"), sectionSummaryExtra(9), 9, false);
        ruleRow(rules, R.drawable.ic_notifications, L("Alerts"), alertsSummary(), 10, false);
        ruleRow(rules, R.drawable.ic_settings, L("Language"), LANG_NAMES[Math.max(0, Arrays.asList(LANGS).indexOf(sp.getString("lang", "en")))], 7, false);

        section(info, "Recent rides", "View all", () -> show(1));
        rideList(card(info, 0), 3);

        if (sp.getBoolean("onehand", false)) { root.addView(info); root.addView(controls); } // controls within thumb reach
        else { root.addView(controls); root.addView(info); }
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
            for (int i = 0; i < Math.min(20, lines.length); i++) {
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

    // ---------- settings: a short menu, each group on its own one-screen page ----------
    static final String[] SECTIONS = {"Ride filters", "Go To & Stay In", "Accept speed", "Earnings targets", "Vehicle & fuel", "Mileage",
        "Goals & alerts", "Display & language", "Driver apps", "Chip style", "Accessibility", "Help & guide", "Time profiles", "Shift & safety", "Backup & profiles"};
    static final int[] SECTION_ICONS = {R.drawable.ic_tune, R.drawable.ic_place, R.drawable.ic_bolt, R.drawable.ic_trending_up, R.drawable.ic_car,
        R.drawable.ic_wallet, R.drawable.ic_notifications, R.drawable.ic_settings, R.drawable.ic_check_circle,
        R.drawable.ic_palette, R.drawable.ic_accessibility, R.drawable.ic_help,
        R.drawable.ic_history, R.drawable.ic_check_circle, R.drawable.ic_settings};
    static {
        // v2 filters, profiles, breaks
        I18n.a("Best pickup areas", "सबसे अच्छे पिकअप क्षेत्र", "ఉత్తమ పికప్ ప్రాంతాలు");
        I18n.a("Busy around this hour", "इस घंटे के आसपास व्यस्त", "ఈ గంట చుట్టూ రద్దీ");
        I18n.a("avg", "औसत", "సగటు");
        I18n.a("Time profiles", "समय प्रोफ़ाइल", "సమయ ప్రొఫైల్‌లు");
        I18n.a("Shift & safety", "शिफ्ट और सुरक्षा", "షిఫ్ట్ & భద్రత");
        I18n.a("Backup & profiles", "बैकअप और प्रोफ़ाइल", "బ్యాకప్ & ప్రొఫైల్‌లు");
        I18n.a("Insights", "अंतर्दृष्टि", "అంతర్దృష్టులు");
        I18n.a("Expected today", "आज अपेक्षित", "ఈ రోజు అంచనా");
        I18n.a("Expected this week", "इस सप्ताह अपेक्षित", "ఈ వారం అంచనా");
        I18n.a("Best hours", "सबसे अच्छे घंटे", "ఉత్తమ గంటలు");
        I18n.a("Break time today", "आज का ब्रेक समय", "ఈ రోజు విరామ సమయం");
        I18n.a("auto-accept is paused", "ऑटो-स्वीकार रुका है", "ఆటో-అంగీకారం ఆగింది");
        I18n.a("Start break", "ब्रेक शुरू करें", "విరామం మొదలుపెట్టండి");
        I18n.a("End break", "ब्रेक खत्म करें", "విరామం ముగించండి");
        I18n.a("Time for a break", "ब्रेक का समय", "విరామ సమయం");
        I18n.a("Maximum fare (₹)", "अधिकतम किराया (₹)", "గరిష్ట ఛార్జీ (₹)");
        I18n.a("Reject fares from (₹)", "इस किराए से अस्वीकार (₹)", "ఈ ఛార్జీ నుండి తిరస్కరించు (₹)");
        I18n.a("Reject fares up to (₹)", "इस किराए तक अस्वीकार (₹)", "ఈ ఛార్జీ వరకు తిరస్కరించు (₹)");
        I18n.a("Maximum ride time (minutes)", "अधिकतम राइड समय (मिनट)", "గరిష్ట రైడ్ సమయం (నిమిషాలు)");
        I18n.a("Minimum profit per ride (₹)", "प्रति राइड न्यूनतम मुनाफ़ा (₹)", "రైడ్‌కు కనీస లాభం (₹)");
        I18n.a("Minimum profit per km (₹)", "प्रति किमी न्यूनतम मुनाफ़ा (₹)", "కిమీకి కనీస లాభం (₹)");
        I18n.a("Minimum profit per hour (₹)", "प्रति घंटा न्यूनतम मुनाफ़ा (₹)", "గంటకు కనీస లాభం (₹)");
        I18n.a("Daily ride limit", "दैनिक राइड सीमा", "రోజువారీ రైడ్ పరిమితి");
        I18n.a("Running cost per km (₹)", "प्रति किमी चलाने की लागत (₹)", "కిమీకి నడిపే ఖర్చు (₹)");
        I18n.a("Minimum ride score (0-100)", "न्यूनतम राइड स्कोर (0-100)", "కనీస రైడ్ స్కోరు (0-100)");
        I18n.a("Pause after each accept (seconds)", "हर स्वीकार के बाद रुकें (सेकंड)", "ప్రతి అంగీకారం తర్వాత విరామం (సెకన్లు)");
        I18n.a("Daily working hours limit", "दैनिक काम के घंटों की सीमा", "రోజువారీ పని గంటల పరిమితి");
        I18n.a("Minimum trip distance, pickup + drop (%s)", "न्यूनतम यात्रा दूरी, पिकअप + ड्रॉप (%s)", "కనీస ట్రిప్ దూరం, పికప్ + డ్రాప్ (%s)");
        I18n.a("Maximum trip distance, pickup + drop (%s)", "अधिकतम यात्रा दूरी, पिकअप + ड्रॉप (%s)", "గరిష్ట ట్రిప్ దూరం, పికప్ + డ్రాప్ (%s)");
        I18n.a("Daily distance limit (%s)", "दैनिक दूरी सीमा (%s)", "రోజువారీ దూర పరిమితి (%s)");
        I18n.a("Stay within (%s) of my base", "मेरे बेस से (%s) के भीतर रहें", "నా బేస్ నుండి (%s) లోపల ఉండండి");
        I18n.a("Fare ₹%s is inside your reject range of ₹%s to ₹%s", "किराया ₹%s आपकी अस्वीकार सीमा ₹%s से ₹%s के भीतर है", "ఛార్జీ ₹%s మీ తిరస్కరణ పరిధి ₹%s నుండి ₹%s లోపల ఉంది");
        I18n.a("Fare ₹%s is above your maximum of ₹%s", "किराया ₹%s आपके अधिकतम ₹%s से ऊपर है", "ఛార్జీ ₹%s మీ గరిష్ట ₹%s కంటే ఎక్కువ");
        I18n.a("Distance not shown, so trip length can't be checked", "दूरी नहीं दिखी, इसलिए यात्रा की लंबाई जाँच नहीं सकते", "దూరం కనిపించలేదు, కాబట్టి ట్రిప్ పొడవును తనిఖీ చేయలేము");
        I18n.a("Trip %s is shorter than your minimum of %s", "यात्रा %s आपके न्यूनतम %s से छोटी है", "ట్రిప్ %s మీ కనీస %s కంటే తక్కువ");
        I18n.a("Trip %s is longer than your limit of %s", "यात्रा %s आपकी सीमा %s से लंबी है", "ట్రిప్ %s మీ పరిమితి %s కంటే ఎక్కువ");
        I18n.a("Ride takes %s min, longer than your limit of %s", "राइड में %s मिनट लगेंगे, आपकी सीमा %s से अधिक", "రైడ్‌కు %s నిమిషాలు, మీ పరిమితి %s కంటే ఎక్కువ");
        I18n.a("Profit ₹%s after fuel is below your minimum of ₹%s", "ईंधन के बाद मुनाफ़ा ₹%s आपके न्यूनतम ₹%s से कम है", "ఇంధనం తర్వాత లాభం ₹%s మీ కనీస ₹%s కంటే తక్కువ");
        I18n.a("Distance not shown, so profit per km can't be checked", "दूरी नहीं दिखी, इसलिए प्रति किमी मुनाफ़ा जाँच नहीं सकते", "దూరం కనిపించలేదు, కాబట్టి కిమీకి లాభాన్ని తనిఖీ చేయలేము");
        I18n.a("Profit ₹%s per km after fuel is below your minimum of ₹%s", "ईंधन के बाद प्रति किमी मुनाफ़ा ₹%s आपके न्यूनतम ₹%s से कम है", "ఇంధనం తర్వాత కిమీకి లాభం ₹%s మీ కనీస ₹%s కంటే తక్కువ");
        I18n.a("Profit ₹%s per hour is below your minimum of ₹%s", "प्रति घंटा मुनाफ़ा ₹%s आपके न्यूनतम ₹%s से कम है", "గంటకు లాభం ₹%s మీ కనీస ₹%s కంటే తక్కువ");
        I18n.a("Daily limit of %s rides reached", "%s राइड की दैनिक सीमा पूरी हुई", "%s రైడ్‌ల రోజువారీ పరిమితి చేరింది");
        I18n.a("Daily limit of %s reached", "%s की दैनिक सीमा पूरी हुई", "%s రోజువారీ పరిమితి చేరింది");
        I18n.a("Daily goal reached, auto-accept paused", "दैनिक लक्ष्य पूरा, ऑटो-स्वीकार रुका", "రోజువారీ లక్ష్యం చేరింది, ఆటో-అంగీకారం ఆగింది");
        I18n.a("Outside your shift hours (%s:00 to %s:00)", "आपके शिफ्ट समय (%s:00 से %s:00) के बाहर", "మీ షిఫ్ట్ సమయం (%s:00 నుండి %s:00) వెలుపల");
        I18n.a("You have worked more than %s hours today", "आपने आज %s घंटे से अधिक काम किया है", "మీరు ఈ రోజు %s గంటల కంటే ఎక్కువ పని చేశారు");
        I18n.a("You are %s from your base, outside your %s area", "आप बेस से %s दूर हैं, आपके %s क्षेत्र के बाहर", "మీరు బేస్ నుండి %s దూరంలో ఉన్నారు, మీ %s ప్రాంతం వెలుపల");
        I18n.a("Distance not shown, so the ride score can't be checked", "दूरी नहीं दिखी, इसलिए राइड स्कोर जाँच नहीं सकते", "దూరం కనిపించలేదు, కాబట్టి రైడ్ స్కోరును తనిఖీ చేయలేము");
        I18n.a("Ride score %s is below your minimum of %s", "राइड स्कोर %s आपके न्यूनतम %s से कम है", "రైడ్ స్కోరు %s మీ కనీస %s కంటే తక్కువ");
        I18n.a("You are on a break", "आप ब्रेक पर हैं", "మీరు విరామంలో ఉన్నారు");
        I18n.a("Declined: rated Low", "अस्वीकार: कम रेटिंग", "తిరస్కరించబడింది: తక్కువ రేటింగ్");
        I18n.a("Score", "स्कोर", "స్కోరు");
        I18n.a("Accept speed", "स्वीकार की गति", "అంగీకార వేగం");
        I18n.a("Mileage", "माइलेज", "మైలేజ్");
        I18n.a("No goal", "कोई लक्ष्य नहीं", "లక్ష్యం లేదు");
        I18n.a("Delay %s s", "देरी %s सेकंड", "ఆలస్యం %s సెకన్లు");
        I18n.a("Instant within %s km", "%s किमी के भीतर तुरंत", "%s కిమీ లోపల వెంటనే");
        I18n.a("Distance unit", "दूरी की इकाई", "దూరం యూనిట్");
        I18n.a("Kilometers", "किलोमीटर", "కిలోమీటర్లు");
        I18n.a("Meters", "मीटर", "మీటర్లు");
        I18n.a("Maximum pickup distance (%s)", "अधिकतम पिकअप दूरी (%s)", "గరిష్ట పిక్‌అప్ దూరం (%s)");
        I18n.a("Maximum drop distance (%s)", "अधिकतम ड्रॉप दूरी (%s)", "గరిష్ట డ్రాప్ దూరం (%s)");
        I18n.a("Rides that go farther than this are skipped. Use 0 for no limit.", "इससे दूर जाने वाली राइड छोड़ दी जाती हैं। कोई सीमा नहीं के लिए 0 लिखें।", "దీని కంటే ఎక్కువ దూరం వెళ్లే రైడ్‌లు దాటవేయబడతాయి. పరిమితి లేకుండా 0 వాడండి.");
        I18n.a("Instant-accept radius (%s)", "तत्काल स्वीकार दायरा (%s)", "తక్షణ అంగీకార పరిధి (%s)");
        I18n.a("Pickup up to %s", "पिकअप %s तक", "పిక్‌అప్ %s వరకు");
        I18n.a("Drop up to %s", "ड्रॉप %s तक", "డ్రాప్ %s వరకు");
        I18n.a("Instant within %s", "%s के भीतर तुरंत", "%s లోపల వెంటనే");
        I18n.a("Dark mode", "डार्क मोड", "డార్క్ మోడ్");
        I18n.a("Go To & Stay In", "गो टू और स्टे इन", "గో టు & స్టే ఇన్");
        I18n.a("Go To: prefer rides heading to my areas", "गो टू: मेरे इलाकों की ओर जाने वाली राइड को प्राथमिकता", "గో టు: నా ప్రాంతాల వైపు వెళ్లే రైడ్‌లకు ప్రాధాన్యం");
        I18n.a("Go To areas", "गो टू इलाके", "గో టు ప్రాంతాలు");
        I18n.a("Comma-separated place names that appear in drop addresses, for example Kukatpally, KPHB.", "कॉमा से अलग किए गए स्थान के नाम जो ड्रॉप पते में दिखते हैं, जैसे कूकटपल्ली, KPHB।", "డ్రాప్ చిరునామాలో కనిపించే ప్రదేశాల పేర్లు, కామాతో వేరు చేయండి, ఉదా. కూకట్‌పల్లి, KPHB.");
        I18n.a("Only accept rides that match Go To", "केवल गो टू से मेल खाने वाली राइड स्वीकार करें", "గో టుకు సరిపోయే రైడ్‌లను మాత్రమే అంగీకరించండి");
        I18n.a("Stay In: prefer rides inside my area", "स्टे इन: मेरे इलाके के अंदर की राइड को प्राथमिकता", "స్టే ఇన్: నా ప్రాంతం లోపలి రైడ్‌లకు ప్రాధాన్యం");
        I18n.a("Stay In areas", "स्टे इन इलाके", "స్టే ఇన్ ప్రాంతాలు");
        I18n.a("Place names. Both the pickup and the drop must be inside.", "स्थान के नाम। पिकअप और ड्रॉप दोनों अंदर होने चाहिए।", "ప్రదేశాల పేర్లు. పిక్‌అప్, డ్రాప్ రెండూ లోపల ఉండాలి.");
        I18n.a("Only accept rides inside Stay In", "केवल स्टे इन के अंदर की राइड स्वीकार करें", "స్టే ఇన్ లోపలి రైడ్‌లను మాత్రమే అంగీకరించండి");
        I18n.a("Priority: Go To first, then Stay In, then nearest pickup.", "प्राथमिकता: पहले गो टू, फिर स्टे इन, फिर सबसे नज़दीकी पिकअप।", "ప్రాధాన్యం: మొదట గో టు, తర్వాత స్టే ఇన్, తర్వాత దగ్గరి పిక్‌అప్.");
        I18n.a("Total km", "कुल किमी", "మొత్తం కిమీ");
        I18n.a("Off", "बंद", "ఆఫ్");
    }

    static final int[] ORDER = {0, 1, 2, 3, 12, 9, 4, 5, 6, 13, 10, 7, 14, 8, 11}; // menu order; the numbers are the page ids

    Bitmap logoBitmap() {
        try (java.io.InputStream in = getAssets().open("logo.png")) { return android.graphics.BitmapFactory.decodeStream(in); }
        catch (java.io.IOException ex) { return null; }
    }

    /** A made-up ride with the given rating, for the chip preview. */
    AcceptService.Card sampleCard(int verdict) {
        AcceptService.Card k = new AcceptService.Card();
        double[] fare = {15, 24, 33};
        k.fare = fare[verdict]; k.base = k.fare; k.pickup = 1.6; k.drop = 2.1; k.dist = 3.7;
        k.fuel = 9; k.perKm = k.fare / k.dist; k.verdict = verdict; k.tag = verdict == 2 ? "Go To" : "";
        k.status = verdict == 2 ? L("Accepting this ride") : verdict == 1 ? L("Another ride has priority")
            : F("Pays \u20B9%s per km, below your minimum of \u20B9%s", "4.1", "6");
        k.statusGood = verdict == 2;
        return k;
    }

    void fixSetup() {
        boolean batt = ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
        if (!AcceptService.connected) startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        else if (!batt) startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        else Toast.makeText(this, L(driverAppInstalled() ? "Everything is set up" : "No selected driver app found on this phone."), Toast.LENGTH_LONG).show();
    }

    String sectionSummaryExtra(int i) {
        String[] sizes = {"Small", "Medium", "Large", "Extra large"}, texts = {"Normal", "Large", "Larger", "Largest"};
        if (i == 9) return L(ChipView.STYLES[Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "chip_style", 1)))]) + "  ·  " + L(sizes[Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "chip_size", 1)))]);
        if (i == 10) {
            int ts = sp.contains("text_scale") ? PrefsGuard.getInt(sp, "text_scale", 0) : (sp.getBoolean("large", false) ? 1 : 0);
            return L(texts[Math.max(0, Math.min(3, ts))]) + (PrefsGuard.getInt(sp, "speak", 0) > 0 ? "  ·  " + L("Spoken alerts") : "");
        }
        return L("Quick guide");
    }

    int settingsPage = -1; // -1 = menu
    int testVerdict = 2;  // the next alert the Test button plays: 2 Worth it, 1 Average, 0 Low

    void settings() { if (settingsPage < 0) settingsMenu(); else settingsSection(settingsPage); }

    String sectionSummary(int i) {
        switch (i) {
            case 0: return profileInfo();
            case 1: {
                List<String> on = new ArrayList<>();
                if (sp.getBoolean("goto_on", false)) on.add("Go To");
                if (sp.getBoolean("stay_on", false)) on.add("Stay In");
                return on.isEmpty() ? L("Off") : android.text.TextUtils.join("  ·  ", on);
            }
            case 2: {
                String s = F("Delay %s s", n(pref(sp, "delay", 300) / 1000));
                double near = pref(sp, "near", 0);
                return near > 0 ? s + "  ·  " + F("Instant within %s", fmtDist(sp, near)) : s;
            }
            case 3: {
                double t = pref(sp, "target", 8);
                return L("Bike") + " ₹" + n(pref(sp, "target_Bike", t)) + "  ·  " + L("Auto") + " ₹" + n(pref(sp, "target_Auto", t))
                    + "  ·  " + L("Car") + " ₹" + n(pref(sp, "target_Car", t));
            }
            case 4: return L(VNAMES[Math.max(0, Arrays.asList(TYPES).indexOf(sp.getString("vehicle", "Bike")))]) + "  ·  ₹" + n(pref(sp, "petrol", 105)) + "/L";
            case 5: return n(pref(sp, "mile_Bike", 45)) + "  ·  " + n(pref(sp, "mile_Auto", 25)) + "  ·  " + n(pref(sp, "mile_Car", 15)) + " km/L";
            case 6: return pref(sp, "goal", 0) > 0 ? "₹" + n(pref(sp, "goal", 0)) + " / " + L("Daily goal").toLowerCase() : L("No goal");
            case 7: return LANG_NAMES[Math.max(0, Arrays.asList(LANGS).indexOf(sp.getString("lang", "en")))];
            case 9: case 10: case 11: return sectionSummaryExtra(i);
            case 12: { int n = 0; for (String sl : SLOTS) for (String k : new String[]{"fare", "dist", "perkm"}) if (!sp.getString("tp_" + sl + "_" + k, "").isEmpty()) { n++; break; } return n == 0 ? L("Off") : n + " " + L("active"); }
            case 13: return !sp.getString("shift_from", "").isEmpty() ? sp.getString("shift_from", "") + ":00 - " + sp.getString("shift_to", "") + ":00" : L("No shift set");
            case 14: return L("Save, restore, share");
            default: {
                List<String> on = new ArrayList<>();
                for (String[] a : APPS) if (sp.getString("pkgs", DEFAULT_PKGS).contains(a[1].split(",")[0])) on.add(a[0]);
                return android.text.TextUtils.join(", ", on);
            }
        }
    }

    // ---------- time profiles: Weekend > Night (22-5) > Peak (7-10, 17-21) > normal; a blank field falls back to the base filter ----------
    static final String[] SLOTS = {"peak", "night", "weekend"}, SLOT_NAMES = {"Peak hours (7-10 AM, 5-9 PM)", "Night (10 PM - 5 AM)", "Weekend"};
    static String slot() { // ponytail: fixed hours, make them editable if drivers ask
        Calendar c = Calendar.getInstance();
        int d = c.get(Calendar.DAY_OF_WEEK), h = c.get(Calendar.HOUR_OF_DAY);
        if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) return "weekend";
        if (h >= 22 || h < 5) return "night";
        if ((h >= 7 && h < 10) || (h >= 17 && h < 21)) return "peak";
        return "";
    }
    /** A filter value for right now: the current time profile's override if set, else the base value. */
    static double eff(SharedPreferences sp, String k, double def) {
        String sl = slot();
        if (!sl.isEmpty()) try { String v = sp.getString("tp_" + sl + "_" + k, ""); if (!v.isEmpty()) return Double.parseDouble(v); } catch (Exception e) { /* use base */ }
        return pref(sp, k, def);
    }

    /** 0-100: profit per km after fuel against twice the driver's own target (so the target itself scores 50). -1 if no distance. */
    static int score(SharedPreferences sp, double fare, double trip, String type) {
        if (trip <= 0) return -1;
        double net = (fare - costPerKm(sp, type) * trip) / trip, t = targetFor(sp, type);
        return (int) Math.max(0, Math.min(100, t > 0 ? 100 * net / (2 * t) : 0));
    }

    // ---------- backup and profiles ----------
    static final String[] FKEYS = {"fare", "dist", "dropmax", "perkm", "rejlo", "rejhi", "maxfare", "mintrip", "maxtrip", "maxmins",
        "minprofit", "minnetkm", "minhr", "dayrides", "daykm", "near", "delay"};

    String backupText() {
        org.json.JSONObject o = new org.json.JSONObject();
        try { for (Map.Entry<String, ?> e : sp.getAll().entrySet()) if (!e.getKey().equals("dump")) o.put(e.getKey(), e.getValue()); }
        catch (Exception ex) { /* skip */ }
        return o.toString();
    }

    /** null on success, else an error message. */
    String restoreBackup(String txt) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(txt.trim());
            SharedPreferences.Editor ed = sp.edit();
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next(); Object v = o.get(k);
                if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Long) ed.putLong(k, (Long) v);
                else ed.putString(k, String.valueOf(v));
            }
            ed.apply();
            return null;
        } catch (Exception e) { return "That is not a valid backup."; }
    }

    void saveProfile(int n) {
        StringBuilder sb = new StringBuilder();
        for (String k : FKEYS) sb.append(k).append('=').append(sp.getString(k, "0")).append(';');
        sp.edit().putString("prof_" + n, sb.toString()).apply();
    }

    boolean loadProfile(int n) {
        String v = sp.getString("prof_" + n, "");
        if (v.isEmpty()) return false;
        SharedPreferences.Editor ed = sp.edit();
        for (String kv : v.split(";")) { int i = kv.indexOf('='); if (i > 0) ed.putString(kv.substring(0, i), kv.substring(i + 1)); }
        ed.apply();
        return true;
    }

    static String hash(String v) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes("UTF-8"))) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return v; }
    }

    // ---------- breaks ----------
    void toggleBreak() {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor ed = sp.edit();
        if (sp.getBoolean("break_on", false)) {
            long day = midnight(), total = (long) pref(sp, "brk_day", 0) == day ? (long) pref(sp, "brk_ms", 0) : 0;
            ed.putBoolean("break_on", false).putString("brk_day", String.valueOf(day))
                .putString("brk_ms", String.valueOf(total + now - (long) pref(sp, "break_since", now))).putString("brk_last", String.valueOf(now));
        } else ed.putBoolean("break_on", true).putString("break_since", String.valueOf(now));
        ed.apply();
    }

    long breakMinutesToday() {
        long now = System.currentTimeMillis(), t = (long) pref(sp, "brk_day", 0) == midnight() ? (long) pref(sp, "brk_ms", 0) : 0;
        if (sp.getBoolean("break_on", false)) t += now - (long) pref(sp, "break_since", now);
        return t / 60000;
    }

    // ---------- pickup areas: "lat|lon|fare|hour|name" per accepted ride, newest first, capped at 1000 ----------
    static synchronized void addZone(SharedPreferences sp, double lat, double lon, double fare, int hour, String name) {
        String rec = lat + "|" + lon + "|" + fare + "|" + hour + "|" + name.replace('|', ' ').replace('\n', ' ');
        String[] old = sp.getString("zones", "").split("\n");
        StringBuilder sb = new StringBuilder(rec);
        for (int i = 0; i < Math.min(old.length, 999); i++) if (!old[i].isEmpty()) sb.append('\n').append(old[i]);
        sp.edit().putString("zones", sb.toString()).apply();
    }

    /** Groups stored pickups into ~1 km cells; top 3 by total fare, optionally only rides from this hour +/- 1. */
    void zoneRows(LinearLayout c, boolean nowOnly) {
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        Map<String, double[]> cells = new LinkedHashMap<>(); // count, total fare, lat sum, lon sum
        Map<String, String> names = new LinkedHashMap<>();
        for (String row : sp.getString("zones", "").split("\n")) {
            String[] r = row.split("\\|", 5);
            if (r.length < 5) continue;
            try {
                double la = Double.parseDouble(r[0]), lo = Double.parseDouble(r[1]), f = Double.parseDouble(r[2]);
                int h = Integer.parseInt(r[3]);
                if (nowOnly && Math.min(Math.abs(h - hr), 24 - Math.abs(h - hr)) > 1) continue;
                String k = Math.round(la * 100) + "," + Math.round(lo * 100);
                double[] a = cells.get(k);
                if (a == null) { a = new double[4]; cells.put(k, a); names.put(k, r[4]); }
                a[0]++; a[1] += f; a[2] += la; a[3] += lo;
            } catch (Exception e) { /* skip bad record */ }
        }
        List<Map.Entry<String, double[]>> top = new ArrayList<>(cells.entrySet());
        java.util.Collections.sort(top, (x, y) -> Double.compare(y.getValue()[1], x.getValue()[1]));
        if (top.isEmpty()) { label(c, nowOnly ? "No pickups around this hour yet." : "No pickup areas yet. They fill in as rides are accepted.", 13, SUBTLE, "sans-serif", 6); return; }
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            double[] a = top.get(i).getValue();
            final double la = a[2] / a[0], lo = a[3] / a[0];
            final String nm = names.get(top.get(i).getKey());
            TextView t = label(c, nm + "  ·  " + (int) a[0] + " " + L("rides") + "  ·  ₹" + Math.round(a[1] / a[0]) + " " + L("avg"), 14, ACCENT, "sans-serif-medium", 8);
            t.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:" + la + "," + lo + "?q=" + la + "," + lo + "(" + android.net.Uri.encode(nm) + ")"))));
        }
    }

    // ---------- insights: simple averages over the stored rides (an estimate, not a model) ----------
    void insights(LinearLayout root) {
        section(root, "Insights", null, null);
        LinearLayout c = card(root, 16);
        Map<Long, Double> perDay = new java.util.TreeMap<>();
        double[] hourFare = new double[24];
        Calendar cal = Calendar.getInstance();
        long today = midnight();
        for (Ride x : parseRides(sp)) {
            cal.setTimeInMillis(x.t);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            long d = cal.getTimeInMillis();
            perDay.put(d, (perDay.containsKey(d) ? perDay.get(d) : 0) + x.fare);
            cal.setTimeInMillis(x.t);
            hourFare[cal.get(Calendar.HOUR_OF_DAY)] += x.fare;
        }
        label(c, "Best pickup areas", 14, TEXT, "sans-serif-medium", 0);
        zoneRows(c, false);
        label(c, "Busy around this hour", 14, TEXT, "sans-serif-medium", 14);
        zoneRows(c, true);
        label(c, "Tap an area to open it in Maps. Areas come from the pickups of rides you accepted.", 12, SUBTLE, "sans-serif", 8);
        divider(c, 12);
        if (perDay.size() < 3) { label(c, "Not enough rides yet. Insights appear after 3 days of rides.", 13, SUBTLE, "sans-serif", 0); return; }
        double all = 0; int same = 0; double sameSum = 0;
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        for (Map.Entry<Long, Double> e : perDay.entrySet()) {
            if (e.getKey() == today) continue;
            all += e.getValue();
            cal.setTimeInMillis(e.getKey());
            if (cal.get(Calendar.DAY_OF_WEEK) == dow) { same++; sameSum += e.getValue(); }
        }
        int past = perDay.containsKey(today) ? perDay.size() - 1 : perDay.size();
        if (past > 0) {
            double daily = same >= 2 ? sameSum / same : all / past;
            label(c, L("Expected today") + ": ₹" + Math.round(daily), 15, TEXT, "sans-serif-medium", 0);
            label(c, L("Expected this week") + ": ₹" + Math.round(all / past * 7), 14, TEXT, "sans-serif", 6);
        }
        Integer[] hs = new Integer[24];
        for (int i = 0; i < 24; i++) hs[i] = i;
        Arrays.sort(hs, (a, b) -> Double.compare(hourFare[b], hourFare[a]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3 && hourFare[hs[i]] > 0; i++) {
            int h = hs[i];
            sb.append(i > 0 ? "  ·  " : "").append(h % 12 == 0 ? 12 : h % 12).append(h < 12 ? " AM" : " PM");
        }
        label(c, L("Best hours") + ": " + sb, 14, TEXT, "sans-serif", 6);
        label(c, "Estimate from your past rides.", 12, SUBTLE, "sans-serif", 6);
    }

    static boolean unlocked = false;
    void askPin() {
        final String pin = sp.getString("pin", "");
        if (pin.isEmpty() || unlocked) return;
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(L("Enter your PIN")).setView(e).setCancelable(false)
            .setPositiveButton(L("Unlock"), (d, w) -> { if (hash(e.getText().toString()).equals(pin) || e.getText().toString().equals(pin)) unlocked = true; else askPin(); })
            .setNegativeButton(L("Exit"), (d, w) -> finish()).show();
    }
    @Override protected void onStop() { super.onStop(); unlocked = false; }

    void setBaseHere() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            Toast.makeText(this, L("Allow location, then tap again"), Toast.LENGTH_LONG).show();
            return;
        }
        android.location.Location l = AcceptService.lastLocation(this);
        if (l == null) { Toast.makeText(this, L("No location yet. Turn on GPS and try again."), Toast.LENGTH_LONG).show(); return; }
        sp.edit().putString("base_lat", String.valueOf(l.getLatitude())).putString("base_lon", String.valueOf(l.getLongitude())).apply();
        Toast.makeText(this, L("Base saved"), Toast.LENGTH_SHORT).show();
    }

    void settingsMenu() {
        LinearLayout root = newPage();
        label(root, "Settings", 24, TEXT, "sans-serif-medium", 0);
        label(root, "Choose which rides are accepted.", 14, SUBTLE, "sans-serif", 4);
        LinearLayout c = card(root, 16);
        c.setPadding(dp(16), dp(4), dp(16), dp(4));
        for (int o = 0; o < ORDER.length; o++) {
            final int i = ORDER[o], idx = i;
            if (o > 0) divider(c, 0);
            LinearLayout r = row();
            r.setPadding(0, dp(10), 0, dp(10));
            r.addView(badge(SECTION_ICONS[i], ACCENT_SOFT, ACCENT, 38, 0));
            LinearLayout t = col();
            t.setPadding(dp(12), 0, dp(8), 0);
            label(t, SECTIONS[i], 15, TEXT, "sans-serif-medium", 0);
            label(t, sectionSummary(i), 12, SUBTLE, "sans-serif", 0);
            r.addView(t, weight());
            ImageView chev = new ImageView(this);
            chev.setImageResource(R.drawable.ic_chevron_right);
            chev.setColorFilter(SUBTLE);
            r.addView(chev, new LinearLayout.LayoutParams(dp(24), dp(24)));
            r.setOnClickListener(v -> { settingsPage = idx; settings(); });
            c.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    void settingsSection(int i) {
        LinearLayout root = newPage();
        LinearLayout head = row();
        FrameLayout back = badge(R.drawable.ic_chevron_right, SURFACE, TEXT, 40, LINE);
        back.setRotation(180);
        back.setContentDescription(L("Back"));
        back.setOnClickListener(v -> { settingsPage = -1; settings(); });
        head.addView(back);
        TextView title = tv(SECTIONS[i], 20, TEXT, "sans-serif-medium");
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title, weight());
        root.addView(head);
        LinearLayout c = card(root, 16);
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        final Runnable[] save = new Runnable[1];
        final boolean[] redraw = {false};

        switch (i) {
            case 0: {
                final EditText[] f = new EditText[15]; // fare, pickup limit, drop limit, earnings per km, reject fare from, reject fare to
                label(c, "Distance unit", 14, TEXT, "sans-serif-medium", 0);
                segmented(c, new String[]{"Kilometers", "Meters"}, meters(sp) ? 1 : 0, 8, k -> {
                    saveFilters(f); // keep what was typed (in the unit shown until now), then redraw with values converted
                    sp.edit().putString("dist_unit", k == 1 ? "m" : "km").apply();
                    settingsSection(0);
                });
                f[0] = field(c, "Minimum fare (₹)", "Rides below this fare are skipped. Use 0 for no limit.", sp.getString("fare", "0"), num, 16);
                f[1] = field(c, F("Maximum pickup distance (%s)", unitName(sp)), "Rides with a farther pickup are skipped. Use 0 for no limit.", n2(toUi(sp, pref(sp, "dist", 0))), num, 16);
                f[2] = field(c, F("Maximum drop distance (%s)", unitName(sp)), "Rides that go farther than this are skipped. Use 0 for no limit.", n2(toUi(sp, pref(sp, "dropmax", 0))), num, 16);
                f[3] = field(c, "Minimum earnings per km (₹)", "Skips rides that pay less per km. Use 0 for no limit.", sp.getString("perkm", "0"), num, 16);
                f[4] = field(c, "Reject fares from (₹)", "Rides with a fare inside this range are skipped. Needs both values; use 0 for no range.", sp.getString("rejlo", "0"), num, 16);
                f[5] = field(c, "Reject fares up to (₹)", "Upper end of the reject range, for example 60 to 80.", sp.getString("rejhi", "0"), num, 16);
                f[6] = field(c, "Maximum fare (₹)", "Rides above this fare are skipped. Use 0 for no limit.", sp.getString("maxfare", "0"), num, 16);
                f[7] = field(c, F("Minimum trip distance, pickup + drop (%s)", unitName(sp)), "Skips short rides. Use 0 for no limit.", n2(toUi(sp, pref(sp, "mintrip", 0))), num, 16);
                f[8] = field(c, F("Maximum trip distance, pickup + drop (%s)", unitName(sp)), "Skips long rides. Use 0 for no limit.", n2(toUi(sp, pref(sp, "maxtrip", 0))), num, 16);
                f[9] = field(c, "Maximum ride time (minutes)", "Skips rides the app says take longer. Use 0 for no limit.", sp.getString("maxmins", "0"), num, 16);
                f[10] = field(c, "Minimum profit per ride (₹)", "Fare minus fuel. Uses your petrol price and mileage. Use 0 for no limit.", sp.getString("minprofit", "0"), num, 16);
                f[11] = field(c, "Minimum profit per km (₹)", "Fare minus fuel, per km of pickup + drop. Use 0 for no limit.", sp.getString("minnetkm", "0"), num, 16);
                f[12] = field(c, "Minimum profit per hour (₹)", "Fare minus fuel, over the ride time. Use 0 for no limit.", sp.getString("minhr", "0"), num, 16);
                f[13] = field(c, "Daily ride limit", "Stops accepting after this many rides today. Use 0 for no limit.", sp.getString("dayrides", "0"), num, 16);
                f[14] = field(c, F("Daily distance limit (%s)", unitName(sp)), "Stops accepting after this much pickup + drop today. Use 0 for no limit.", n2(toUi(sp, pref(sp, "daykm", 0))), num, 16);
                CheckBox goalStop = check("Pause auto-accept when the daily goal is reached", sp.getBoolean("goal_stop", false));
                c.addView(goalStop);
                CheckBox orMode = check("Accept if the minimum fare OR the minimum earnings per km is met", sp.getBoolean("or_mode", false));
                c.addView(orMode);
                label(c, "Off means both must be met. All other filters always apply.", 13, SUBTLE, "sans-serif", 0);
                save[0] = () -> { saveFilters(f); sp.edit().putBoolean("goal_stop", goalStop.isChecked()).putBoolean("or_mode", orMode.isChecked()).apply(); };
                break;
            }
            case 1: {
                CheckBox goOn = check("Go To: prefer rides heading to my areas", sp.getBoolean("goto_on", false));
                c.addView(goOn);
                EditText goAreas = field(c, "Go To areas", "Comma-separated place names that appear in drop addresses, for example Kukatpally, KPHB.", sp.getString("goto_areas", ""), InputType.TYPE_CLASS_TEXT, 4);
                CheckBox goStrict = check("Only accept rides that match Go To", sp.getBoolean("goto_strict", false));
                c.addView(goStrict);
                divider(c, 12);
                CheckBox stayOn = check("Stay In: prefer rides inside my area", sp.getBoolean("stay_on", false));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
                sl.topMargin = dp(12);
                c.addView(stayOn, sl);
                EditText stayAreas = field(c, "Stay In areas", "Place names. Both the pickup and the drop must be inside.", sp.getString("stay_areas", ""), InputType.TYPE_CLASS_TEXT, 4);
                CheckBox stayStrict = check("Only accept rides inside Stay In", sp.getBoolean("stay_strict", false));
                c.addView(stayStrict);
                label(c, "Then prefer", 14, TEXT, "sans-serif-medium", 14);
                final int[] sortSel = {Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "sort", 0)))};
                segmented(c, new String[]{"Nearest", "Best ₹/km", "Top fare", "Most kept"}, sortSel[0], 6, k -> sortSel[0] = k);
                label(c, "Priority: Go To first, then Stay In, then your choice below.", 13, SUBTLE, "sans-serif", 8);
                save[0] = () -> sp.edit().putBoolean("goto_on", goOn.isChecked()).putString("goto_areas", goAreas.getText().toString())
                    .putBoolean("goto_strict", goStrict.isChecked()).putBoolean("stay_on", stayOn.isChecked())
                    .putString("stay_areas", stayAreas.getText().toString()).putBoolean("stay_strict", stayStrict.isChecked())
                    .putInt("sort", sortSel[0]).apply();
                break;
            }
            case 2: {
                EditText delay = field(c, "Response delay (seconds)", "Wait this long before accepting.", n(pref(sp, "delay", 300) / 1000), num, 0);
                EditText near = field(c, F("Instant-accept radius (%s)", unitName(sp)), "Rides with pickup this close are accepted with no delay. Use 0 to turn off.", n2(toUi(sp, pref(sp, "near", 0))), num, 16);
                save[0] = () -> {
                    double secs = 0.3;
                    try { secs = Double.parseDouble(delay.getText().toString()); } catch (Exception ex) { /* keep default */ }
                    sp.edit().putString("delay", String.valueOf((long) (secs * 1000))).putString("near", n4(fromUi(sp, parse(near)))).apply();
                };
                break;
            }
            case 3: {
                String t0 = sp.getString("target", "8");
                EditText tBike = field(c, "Bike / scooty target (₹ per km)", "Rides at or above this are marked Worth it.", sp.getString("target_Bike", t0), num, 0);
                EditText tAuto = field(c, "Auto target (₹ per km)", "Rides at or above this are marked Worth it.", sp.getString("target_Auto", t0), num, 16);
                EditText tCar = field(c, "Car target (₹ per km)", "Rides at or above this are marked Worth it.", sp.getString("target_Car", t0), num, 16);
                save[0] = () -> sp.edit().putString("target_Bike", tBike.getText().toString()).putString("target_Auto", tAuto.getText().toString())
                    .putString("target_Car", tCar.getText().toString()).apply();
                break;
            }
            case 4: {
                label(c, "My vehicle", 14, TEXT, "sans-serif-medium", 0);
                String[] veh = {sp.getString("vehicle", "Bike")};
                segmented(c, VNAMES, Math.max(0, Arrays.asList(TYPES).indexOf(veh[0])), 8, k -> veh[0] = TYPES[k]);
                label(c, "Used when the ride type isn't shown on the request.", 13, SUBTLE, "sans-serif", 4);
                EditText petrol = field(c, "Fuel price (₹ per litre)", "Current fuel price in your city.", sp.getString("petrol", "105"), num, 16);
                EditText fixed = field(c, "Monthly fixed costs (₹)", "EMI, insurance, phone plan. Spread evenly across the days in Money.", sp.getString("fixed", "0"), num, 16);
                EditText maint = field(c, "Running cost per km (₹)", "Maintenance, tyres, oil, wear. Added to fuel in every profit figure. Use 0 to ignore.", sp.getString("maint", "0"), num, 16);
                save[0] = () -> sp.edit().putString("vehicle", veh[0]).putString("petrol", petrol.getText().toString())
                    .putString("fixed", fixed.getText().toString()).putString("maint", maint.getText().toString()).apply();
                break;
            }
            case 5: {
                EditText mBike = field(c, "Bike / scooty mileage (km per litre)", "Used to work out fuel cost per km.", sp.getString("mile_Bike", "45"), num, 0);
                EditText mAuto = field(c, "Auto mileage (km per litre)", "Used to work out fuel cost per km.", sp.getString("mile_Auto", "25"), num, 16);
                EditText mCar = field(c, "Car mileage (km per litre)", "Used to work out fuel cost per km.", sp.getString("mile_Car", "15"), num, 16);
                save[0] = () -> sp.edit().putString("mile_Bike", mBike.getText().toString()).putString("mile_Auto", mAuto.getText().toString())
                    .putString("mile_Car", mCar.getText().toString()).apply();
                break;
            }
            case 6: {
                EditText goal = field(c, "Daily earnings goal (₹)", "Shows progress on the home screen. Use 0 to hide.", sp.getString("goal", "0"), num, 0);
                CheckBox alert = check("Vibrate and beep for Worth it rides", sp.getBoolean("alert", true));
                CheckBox reject = check("Decline rides rated Low", sp.getBoolean("reject", false));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
                cl.topMargin = dp(12);
                c.addView(alert, cl);
                c.addView(reject, new LinearLayout.LayoutParams(-1, -2));
                label(c, "Taps Reject on Low rides. Off by default; a wrong match would lose you a ride.", 13, SUBTLE, "sans-serif", 0);
                save[0] = () -> sp.edit().putString("goal", goal.getText().toString()).putBoolean("alert", alert.isChecked())
                    .putBoolean("reject", reject.isChecked()).apply();
                break;
            }
            case 7: {
                label(c, "Language", 14, TEXT, "sans-serif-medium", 0);
                final String[] lang = {sp.getString("lang", "en")};
                Spinner langSpin = new Spinner(this);
                langSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LANG_NAMES));
                langSpin.setSelection(Math.max(0, Arrays.asList(LANGS).indexOf(lang[0])));
                langSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { lang[0] = LANGS[pos]; }
                    @Override public void onNothingSelected(AdapterView<?> p) { }
                });
                c.addView(langSpin);
                CheckBox darkBox = check("Dark mode", sp.getBoolean("dark", true));
                CheckBox bubble = check("Show the floating Vamsify bubble over driver apps", sp.getBoolean("bubble", true));
                CheckBox trip = check("Show trip details after accepting", sp.getBoolean("trip", true));
                CheckBox onehand = check("One-hand layout (main controls at the bottom)", sp.getBoolean("onehand", false));
                CheckBox notif = check("Pause and resume notification", sp.getBoolean("notif", true));
                CheckBox summary = check("Daily summary at 9 PM", sp.getBoolean("summary", true));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
                cl.topMargin = dp(12);
                c.addView(darkBox, cl);
                c.addView(bubble, new LinearLayout.LayoutParams(-1, -2));
                for (CheckBox cb : new CheckBox[]{trip, onehand, notif, summary}) c.addView(cb, new LinearLayout.LayoutParams(-1, -2));
                save[0] = () -> {
                    redraw[0] = darkBox.isChecked() != sp.getBoolean("dark", true) || !lang[0].equals(sp.getString("lang", "en"))
                        || onehand.isChecked() != sp.getBoolean("onehand", false);
                    sp.edit().putString("lang", lang[0]).putBoolean("dark", darkBox.isChecked()).putBoolean("bubble", bubble.isChecked()).putBoolean("trip", trip.isChecked())
                        .putBoolean("onehand", onehand.isChecked())
                        .putBoolean("notif", notif.isChecked()).putBoolean("summary", summary.isChecked()).apply();
                    if (notif.isChecked() || summary.isChecked()) askNotificationPermission();
                    scheduleSummary(this);
                };
                break;
            }
            case 9: { // chip style: every choice applies at once and the preview above updates
                final LinearLayout pv = col();
                HorizontalScrollView hs = new HorizontalScrollView(this);
                hs.setHorizontalScrollBarEnabled(false);
                hs.addView(pv);
                c.addView(hs);
                final Bitmap lb = logoBitmap();
                final Runnable[] refresh = new Runnable[1];
                refresh[0] = () -> {
                    pv.removeAllViews();
                    for (int v = 2; v >= 0; v--) {
                        ChipView cv = new ChipView(this, lb);
                        cv.bind(sampleCard(v), sp);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                        lp.topMargin = dp(v == 2 ? 0 : 8);
                        pv.addView(cv, lp);
                    }
                };
                refresh[0].run();
                label(c, "Size", 14, TEXT, "sans-serif-medium", 14);
                segmented(c, new String[]{"Small", "Medium", "Large", "Extra large"}, Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "chip_size", 1))), 6, k -> { sp.edit().putInt("chip_size", k).apply(); refresh[0].run(); });
                label(c, "Style", 14, TEXT, "sans-serif-medium", 12);
                segmented(c, ChipView.STYLES, Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "chip_style", 1))), 6, k -> { sp.edit().putInt("chip_style", k).apply(); refresh[0].run(); });
                label(c, "See-through", 14, TEXT, "sans-serif-medium", 12);
                segmented(c, new String[]{"Solid", "Soft", "Faint"}, Math.max(0, Math.min(2, PrefsGuard.getInt(sp, "chip_alpha", 0))), 6, k -> { sp.edit().putInt("chip_alpha", k).apply(); refresh[0].run(); });
                label(c, "Position", 14, TEXT, "sans-serif-medium", 12);
                segmented(c, new String[]{"Automatic", "Beside the distance"}, Math.max(0, Math.min(1, PrefsGuard.getInt(sp, "chip_pos", 0))), 6, k -> sp.edit().putInt("chip_pos", k).apply());
                label(c, "You can also tap a chip on a ride card to move it between these positions.", 13, SUBTLE, "sans-serif", 6);
                CheckBox logoBox = check("Show the logo", sp.getBoolean("chip_logo", true));
                logoBox.setOnCheckedChangeListener((b, on) -> { sp.edit().putBoolean("chip_logo", on).apply(); refresh[0].run(); });
                LinearLayout.LayoutParams lcl = new LinearLayout.LayoutParams(-1, -2);
                lcl.topMargin = dp(10);
                c.addView(logoBox, lcl);
                CheckBox whyBox = check("Show why a ride isn't auto-accepted", sp.getBoolean("chip_why", true));
                whyBox.setOnCheckedChangeListener((b, on) -> { sp.edit().putBoolean("chip_why", on).apply(); refresh[0].run(); });
                c.addView(whyBox, new LinearLayout.LayoutParams(-1, -2));
                button(c, "Preview on a sample screen", false, 8).setOnClickListener(v -> {
                    if (AcceptService.instance == null) Toast.makeText(this, L("Turn on the service first"), Toast.LENGTH_LONG).show();
                    else startActivity(new Intent(this, SampleActivity.class));
                });
                save[0] = () -> { };
                break;
            }
            case 10: { // accessibility
                final int[] ts = {Math.max(0, Math.min(3, sp.contains("text_scale") ? PrefsGuard.getInt(sp, "text_scale", 0) : (sp.getBoolean("large", false) ? 1 : 0)))};
                label(c, "Text size", 14, TEXT, "sans-serif-medium", 0);
                segmented(c, new String[]{"Normal", "Large", "Larger", "Largest"}, ts[0], 6, k -> ts[0] = k);
                CheckBox hc = check("High contrast text", sp.getBoolean("hc", false));
                CheckBox cb = check("Colour-blind symbols on every rating", sp.getBoolean("cb_symbols", true));
                CheckBox taps = check("Bigger buttons and tap areas", sp.getBoolean("big_taps", false));
                CheckBox flash = check("Flash the screen for each ride", sp.getBoolean("flash", false));
                CheckBox vib = check("Vibrate differently for Average and Low", sp.getBoolean("vib_verdict", false));
                CheckBox voice = check("Voice-first mode", sp.getBoolean("voice", false));
                LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-1, -2);
                cl2.topMargin = dp(10);
                c.addView(hc, cl2);
                for (CheckBox x : new CheckBox[]{cb, taps, flash, vib, voice}) c.addView(x, new LinearLayout.LayoutParams(-1, -2));
                label(c, "Voice-first mode speaks every ride, every accept and every on/off change, and closes the trip card on its own.", 13, SUBTLE, "sans-serif", 4);
                label(c, "Spoken alerts", 14, TEXT, "sans-serif-medium", 14);
                final int[] speak = {Math.max(0, Math.min(2, PrefsGuard.getInt(sp, "speak", 0)))};
                segmented(c, new String[]{"Off", "Worth it only", "Every ride"}, speak[0], 6, k -> speak[0] = k);
                label(c, "Spoken in the app language when the phone has that voice; otherwise in English.", 13, SUBTLE, "sans-serif", 6);
                button(c, "Test the alerts", false, 12).setOnClickListener(v -> { // saves the choices above, then plays Worth it, Average, Low in turn
                    save[0].run();
                    AcceptService svc = AcceptService.instance;
                    if (svc == null) Toast.makeText(this, L("Turn on the service first"), Toast.LENGTH_LONG).show();
                    else { svc.testAlerts(testVerdict); testVerdict = (testVerdict + 2) % 3; }
                });
                save[0] = () -> {
                    redraw[0] = redraw[0] || ts[0] != Math.max(0, Math.min(3, sp.contains("text_scale") ? PrefsGuard.getInt(sp, "text_scale", 0) : (sp.getBoolean("large", false) ? 1 : 0)))
                        || hc.isChecked() != sp.getBoolean("hc", false) || taps.isChecked() != sp.getBoolean("big_taps", false);
                    sp.edit().putInt("text_scale", ts[0]).putBoolean("hc", hc.isChecked()).putBoolean("cb_symbols", cb.isChecked())
                        .putBoolean("big_taps", taps.isChecked()).putBoolean("flash", flash.isChecked()).putBoolean("vib_verdict", vib.isChecked())
                        .putBoolean("voice", voice.isChecked()).putInt("speak", speak[0]).apply();
                };
                break;
            }
            case 11: { // help
                label(c, "Quick guide", 16, TEXT, "sans-serif-medium", 0);
                numbered(c, 1, L("Turn on Vamsify under Accessibility settings."), 10);
                numbered(c, 2, L("Pick your driver apps and your filters in Settings."), 10);
                numbered(c, 3, L("Tap Try a sample ride on Home to see the chips."), 10);
                numbered(c, 4, L("Go online in your driver app and keep the screen on."), 10);
                label(c, "What the chip colours mean", 14, TEXT, "sans-serif-medium", 14);
                String[] legend = {"✓  " + L("Worth it") + ": " + L("pays at least your target per km."), "~  " + L("Average") + ": " + L("within 25% of your target."), "✕  " + L("Low") + ": " + L("pays well below your target.")};
                int[] lcol = {OK, 0xFFE0A800, WARN};
                for (int q = 0; q < 3; q++) {
                    TextView t = label(c, legend[q], 14, lcol[q], "sans-serif-medium", 6);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                }
                healthCard(root);
                button(root, "Fix my setup", true, 12).setOnClickListener(v -> fixSetup());
                button(root, "Replay the setup tour", false, 8).setOnClickListener(v -> { step = 0; settingsPage = -1; onboard(); });
                save[0] = () -> { };
                break;
            }
            case 12: {
                label(c, "Leave a box empty to use the normal filter. Distances are in " + unitName(sp) + ".", 13, SUBTLE, "sans-serif", 0);
                final EditText[][] tf = new EditText[3][3];
                final String[] pk = {"fare", "dist", "perkm"};
                final String[] names = {"Minimum fare (₹)", "Maximum pickup distance", "Minimum earnings per km (₹)"};
                for (int sI = 0; sI < 3; sI++) {
                    label(c, SLOT_NAMES[sI], 14, TEXT, "sans-serif-medium", 16);
                    for (int kI = 0; kI < 3; kI++) {
                        String raw = sp.getString("tp_" + SLOTS[sI] + "_" + pk[kI], "");
                        if (kI == 1 && !raw.isEmpty()) raw = n2(toUi(sp, Double.parseDouble(raw)));
                        tf[sI][kI] = field(c, names[kI], "", raw, num, 8);
                    }
                }
                save[0] = () -> {
                    SharedPreferences.Editor ed = sp.edit();
                    for (int sI = 0; sI < 3; sI++) for (int kI = 0; kI < 3; kI++) {
                        String v = tf[sI][kI].getText().toString().trim();
                        if (kI == 1 && !v.isEmpty()) v = n4(fromUi(sp, parse(tf[sI][kI])));
                        ed.putString("tp_" + SLOTS[sI] + "_" + pk[kI], v);
                    }
                    ed.apply();
                };
                break;
            }
            case 13: {
                EditText from = field(c, "Shift starts at (hour, 0-23)", "Auto-accept only runs between the start and end hours. Leave both empty for all day.", sp.getString("shift_from", ""), num, 0);
                EditText to = field(c, "Shift ends at (hour, 0-23)", "", sp.getString("shift_to", ""), num, 8);
                EditText hrs = field(c, "Daily working hours limit", "Stops accepting this many hours after your first ride today. Use 0 for no limit.", sp.getString("maxhrs", "0"), num, 16);
                EditText cool = field(c, "Pause after each accept (seconds)", "Prevents accepting two rides at once. Default 4.", sp.getString("cool", "4"), num, 16);
                EditText rad = field(c, F("Stay within (%s) of my base", unitName(sp)), "Skips rides when your phone is farther than this from your base. Use 0 for no limit.", n2(toUi(sp, pref(sp, "radius", 0))), num, 16);
                button(c, sp.getString("base_lat", "").isEmpty() ? "Set my base to where I am now" : "Update my base to where I am now", false, 8).setOnClickListener(v -> setBaseHere());
                EditText score = field(c, "Minimum ride score (0-100)", "Score compares profit per km with your target; the target itself scores 50. Use 0 for no limit.", sp.getString("minscore", "0"), num, 16);
                EditText pin = field(c, "New app PIN (digits)", sp.getString("pin", "").isEmpty() ? "Asks for this PIN when the app opens." : "A PIN is set. Leave empty to keep it.", "", num, 16);
                if (!sp.getString("pin", "").isEmpty()) button(c, "Remove PIN", false, 8).setOnClickListener(v -> { sp.edit().remove("pin").apply(); settingsSection(13); });
                EditText brkEvery = field(c, "Remind me to take a break every (hours)", "Reminds you after this many hours of work without a break. Use 0 for none.", sp.getString("brk_every", "0"), num, 16);
                button(c, sp.getBoolean("break_on", false) ? "End break" : "Start break", false, 8).setOnClickListener(v -> { toggleBreak(); settingsSection(13); });
                label(c, L("Break time today") + ": " + breakMinutesToday() + " " + L("min") + (sp.getBoolean("break_on", false) ? "  ·  " + L("auto-accept is paused") : ""), 13, SUBTLE, "sans-serif", 6);
                save[0] = () -> sp.edit().putString("shift_from", from.getText().toString().trim()).putString("shift_to", to.getText().toString().trim())
                    .putString("maxhrs", hrs.getText().toString()).putString("cool", cool.getText().toString())
                    .putString("radius", n4(fromUi(sp, parse(rad)))).putString("minscore", score.getText().toString())
                    .putString("brk_every", brkEvery.getText().toString())
                    .putString("pin", pin.getText().toString().trim().isEmpty() ? sp.getString("pin", "") : hash(pin.getText().toString().trim())).apply();
                break;
            }
            case 14: {
                label(c, "Filter profiles", 14, TEXT, "sans-serif-medium", 0);
                label(c, "Save your current filters, then switch between them in one tap.", 13, SUBTLE, "sans-serif", 4);
                for (int pn = 1; pn <= 3; pn++) {
                    final int n = pn;
                    LinearLayout r = row();
                    Button sv = button(r, "Save " + n, false, 8), ld = button(r, sp.getString("prof_" + n, "").isEmpty() ? "Empty" : "Load " + n, false, 8);
                    sv.setOnClickListener(v -> { saveProfile(n); Toast.makeText(this, L("Saved"), Toast.LENGTH_SHORT).show(); settingsSection(14); });
                    ld.setOnClickListener(v -> { if (loadProfile(n)) Toast.makeText(this, L("Loaded"), Toast.LENGTH_SHORT).show(); });
                    c.addView(r, new LinearLayout.LayoutParams(-1, -2));
                }
                label(c, "Backup", 14, TEXT, "sans-serif-medium", 16);
                label(c, "Share everything (settings, rides, money) as text, and paste it back to restore.", 13, SUBTLE, "sans-serif", 4);
                button(c, "Share backup", false, 8).setOnClickListener(v -> {
                    Intent it = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, backupText());
                    startActivity(Intent.createChooser(it, "Backup"));
                });
                button(c, "Restore from backup", false, 8).setOnClickListener(v -> {
                    final EditText e = new EditText(this);
                    new AlertDialog.Builder(this).setMessage(L("Paste your backup")).setView(e)
                        .setNegativeButton(L("Cancel"), null)
                        .setPositiveButton(L("Restore"), (d, w) -> {
                            String err = restoreBackup(e.getText().toString());
                            Toast.makeText(this, err == null ? L("Restored") : L(err), Toast.LENGTH_SHORT).show();
                            if (err == null) recreate();
                        }).show();
                });
                save[0] = () -> { };
                break;
            }
            default: {
                CheckBox[] boxes = appChecks(c);
                save[0] = () -> sp.edit().putString("pkgs", pkgsFrom(boxes)).apply();
            }
        }

        button(root, i == 11 ? "Done" : "Save changes", true, 16).setOnClickListener(v -> {
            save[0].run();
            if (i != 11) Toast.makeText(this, L("Changes saved"), Toast.LENGTH_SHORT).show();
            if (redraw[0]) { recreate(); return; }
            settingsPage = -1;
            settings();
        });
    }

    // =====================================================================
    // shell
    // =====================================================================
    @Override protected void onCreate(Bundle b) {
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        PrefsGuard.sanitize(sp); // repair settings saved with another type by an older version
        applyTheme(sp);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(SURFACE);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));

        LinearLayout frame = col();
        content = new FrameLayout(this);
        frame.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = col();
        View line = new View(this);
        line.setBackgroundColor(LINE);
        bottom.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        nav = new LinearLayout(this);
        nav.setBackgroundColor(SURFACE);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        bottom.setElevation(dp(8));
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
            item.setOnClickListener(v -> { if (idx == 3) settingsPage = -1; show(idx); });
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
            nl.leftMargin = nl.rightMargin = dp(3);
            nav.addView(item, nl);
        }
        bottom.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        frame.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(frame);
        scheduleSummary(this);
        if (sp.getBoolean("onboarded", false)) show(0); else onboard();
        askPin();
    }

    /** Back goes up one level: settings page to the settings list, any tab to Home, a setup step to the previous one. Only Home exits. */
    @Override public void onBackPressed() {
        if (!sp.getBoolean("onboarded", false)) {          // first-run setup
            if (step > 0) { step--; onboard(); return; }
            super.onBackPressed();
            return;
        }
        if (tab == 3 && settingsPage >= 0) { settingsPage = -1; settings(); return; }
        if (tab != 0) { show(0); return; }
        super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        if (sp.getBoolean("onboarded", false)) show(tab); else onboard(); // refresh service status and history
    }
}
