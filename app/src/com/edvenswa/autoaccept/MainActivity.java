package com.edvenswa.autoaccept;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {
    static final String PREFS = "p";
    static final String DEFAULT_PKGS = "com.rapido.rider,com.rapido.captain,com.ubercab.driver,com.olacabs.oladriver";

    // single accent, neutral greys, 8dp spacing grid
    static final int ACCENT = 0xFF1F4FD8, BG = 0xFFF5F6F8, SURFACE = Color.WHITE, LINE = 0xFFE2E5EA,
            TEXT = 0xFF14171F, SUBTLE = 0xFF5F6673, OK = 0xFF1E8E4E, WARN = 0xFFB3261E;

    SharedPreferences sp;
    Switch on;
    EditText fare, dist, delay;
    TextView statusTitle, statusBody, log;
    View statusDot;

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    TextView label(ViewGroup p, String s, int sp, int color, String family, int top) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        p.addView(t, lp);
        return t;
    }

    void section(LinearLayout root, String s) {
        TextView t = label(root, s.toUpperCase(), 12, SUBTLE, "sans-serif-medium", 24);
        t.setLetterSpacing(0.08f);
        ((LinearLayout.LayoutParams) t.getLayoutParams()).bottomMargin = dp(8);
    }

    LinearLayout card(LinearLayout root) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(shape(SURFACE, 12, LINE));
        root.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    EditText field(LinearLayout p, String name, String help, String val, int type, int top) {
        label(p, name, 14, TEXT, "sans-serif-medium", top);
        EditText e = new EditText(this);
        e.setInputType(type); e.setText(val); e.setSingleLine(true);
        e.setTextColor(TEXT); e.setTextSize(16);
        e.setBackground(shape(SURFACE, 8, 0xFFC4C9D2));
        e.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(8);
        p.addView(e, lp);
        label(p, help, 13, SUBTLE, "sans-serif", 4);
        return e;
    }

    Button button(LinearLayout p, String s, boolean primary, int top) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? Color.WHITE : ACCENT);
        b.setBackground(primary ? shape(ACCENT, 8, 0) : shape(SURFACE, 8, ACCENT));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(top);
        p.addView(b, lp);
        return b;
    }

    // user-facing app names -> internal package ids (never shown on screen)
    static final String[][] APPS = {
        {"Rapido", "com.rapido.rider,com.rapido.captain"},
        {"Uber Driver", "com.ubercab.driver"},
        {"Ola Driver", "com.olacabs.oladriver"}};
    CheckBox[] appBoxes = new CheckBox[APPS.length];

    void save() {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < APPS.length; i++) if (appBoxes[i].isChecked()) p.append(APPS[i][1]).append(',');
        double secs = 0.3;
        try { secs = Double.parseDouble(delay.getText().toString()); } catch (Exception e) { }
        sp.edit().putBoolean("on", on.isChecked())
            .putString("fare", fare.getText().toString())
            .putString("dist", dist.getText().toString())
            .putString("delay", String.valueOf((long) (secs * 1000)))
            .putString("pkgs", p.toString()).apply();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        sv.addView(root);

        TextView title = label(root, "Auto Acceptor", 24, TEXT, "sans-serif-medium", 0);
        label(root, "Automatically accept ride requests that match your filters.", 14, SUBTLE, "sans-serif", 4);

        // service status
        section(root, "Service");
        LinearLayout st = card(root);
        st.setOrientation(LinearLayout.HORIZONTAL);
        st.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        st.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        statusTitle = label(col, "", 16, TEXT, "sans-serif-medium", 0);
        statusBody = label(col, "", 13, SUBTLE, "sans-serif", 2);
        st.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chev = new TextView(this);
        chev.setText("›"); chev.setTextSize(24); chev.setTextColor(SUBTLE);
        st.addView(chev);
        st.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // master switch
        section(root, "Auto-accept");
        LinearLayout m = card(root);
        on = new Switch(this);
        on.setText("Accept matching rides"); on.setTextSize(16); on.setTextColor(TEXT);
        on.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        on.setChecked(sp.getBoolean("on", false));
        m.addView(on);
        label(m, "When off, rides are never tapped on your behalf.", 13, SUBTLE, "sans-serif", 4);

        // filters
        section(root, "Ride filters");
        LinearLayout f = card(root);
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        fare = field(f, "Minimum fare (₹)", "Rides below this fare are skipped. Use 0 for no limit.", sp.getString("fare", "0"), num, 0);
        dist = field(f, "Maximum pickup distance (km)", "Rides with a farther pickup are skipped. Use 0 for no limit.", sp.getString("dist", "0"), num, 20);
        delay = field(f, "Response delay (seconds)", "Wait this long before accepting.",
            String.valueOf(Double.parseDouble(sp.getString("delay", "300")) / 1000), num, 20);

        // apps
        section(root, "Driver apps");
        LinearLayout a = card(root);
        String saved = sp.getString("pkgs", DEFAULT_PKGS);
        for (int i = 0; i < APPS.length; i++) {
            CheckBox c = new CheckBox(this);
            c.setText(APPS[i][0]); c.setTextSize(16); c.setTextColor(TEXT);
            c.setChecked(saved.contains(APPS[i][1].split(",")[0]));
            appBoxes[i] = c;
            a.addView(c);
        }

        Button save = button(root, "Save changes", true, 24);
        save.setOnClickListener(v -> { save(); Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show(); });
        on.setOnCheckedChangeListener((v, c) -> save());

        // hidden troubleshooting: long-press the title (support use only)
        log = label(root, "", 11, SUBTLE, "sans-serif", 24);
        log.setTypeface(Typeface.MONOSPACE);
        title.setOnLongClickListener(v -> {
            log.setText("service connected: " + AcceptService.connected + "\nevents: " + AcceptService.events
                + "\nlast app: " + AcceptService.lastPkg + "\n\n" + sp.getString("dump", "nothing read yet"));
            return true;
        });

        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        boolean ok = AcceptService.connected;
        statusDot.setBackground(shape(ok ? OK : WARN, 5, 0));
        statusTitle.setText(ok ? "Service running" : "Service not enabled");
        statusBody.setText(ok ? "Accessibility access is active." : "Tap to enable Auto Acceptor in Accessibility settings.");
    }
}
