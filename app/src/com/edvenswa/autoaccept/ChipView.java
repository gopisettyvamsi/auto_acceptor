package com.edvenswa.autoaccept;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * The earnings chip: a dark pill with the logo, the verdict and the numbers.
 * The driver chooses its size, style, transparency and whether it shows the logo and a symbol beside the verdict
 * (Settings > Chip style). The same class draws the real chips over a driver app and the preview in Settings.
 */
class ChipView extends LinearLayout {
    static final String[] STYLES = {"Compact", "Standard", "Detailed", "Simple"};
    static final float[] SIZES = {0.85f, 1f, 1.25f, 1.5f};
    static final int[] ALPHAS = {0xF2, 0xB3, 0x80};
    static final String[] SYMBOLS = {"✕", "~", "✓"}; // low, average, worth it: shape as well as colour, for colour-blind drivers
    static final int[] MIN_WIDTH_DP = {150, 214, 250, 190};

    String key = ""; // which card this chip belongs to
    float fit = 1f;  // 1 = the size the driver chose; smaller when the free space on the card is tight
    private AcceptService.Card lastCard;
    private SharedPreferences lastSp;
    final ImageView logo;
    final TextView main, verdict, keep, status;
    private final Bitmap logoBmp;

    ChipView(Context ctx, Bitmap logoBmp) {
        super(ctx);
        this.logoBmp = logoBmp;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        logo = new ImageView(ctx);
        if (logoBmp != null) logo.setImageBitmap(logoBmp);
        addView(logo);
        main = new TextView(ctx);
        main.setTextColor(Color.WHITE);
        main.setTypeface(Typeface.DEFAULT_BOLD);
        addView(main);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        verdict = new TextView(ctx);
        verdict.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        keep = new TextView(ctx);
        keep.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(verdict);
        col.addView(keep);
        status = new TextView(ctx);
        status.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        status.setVisibility(GONE);
        col.addView(status);
        addView(col);
    }

    private float density() { return getResources().getDisplayMetrics().density; }

    /** Natural size at the driver's chosen scale, in pixels. */
    int[] naturalSize() {
        fit = 1f;
        if (lastCard != null) bind(lastCard, lastSp);
        measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return new int[]{getMeasuredWidth(), getMeasuredHeight()};
    }

    /** Shrinks the text step by step until the chip fits in availW x maxH (maxH <= 0 means any height). Never below 70%. */
    void fitTo(int availW, int maxH) {
        for (float f : new float[]{1f, 0.92f, 0.85f, 0.78f, 0.7f}) {
            fit = f;
            if (lastCard != null) bind(lastCard, lastSp);
            measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (getMeasuredWidth() <= availW && (maxH <= 0 || getMeasuredHeight() <= maxH)) return;
        }
    }

    /** The line saying why a ride is, or is not, being auto-accepted. Hidden when the driver turns it off. */
    private void applyStatus(AcceptService.Card c, SharedPreferences sp, float s) {
        boolean show = sp.getBoolean("chip_why", true) && c.status != null && !c.status.isEmpty();
        status.setVisibility(show ? VISIBLE : GONE);
        if (!show) return;
        status.setText(c.status);
        status.setTextSize(9f * s);
        status.setTextColor(c.statusGood ? 0xFF5BD68A : 0xFFFFB74D);
        status.setMaxWidth((int) (150 * s * density())); // long text wraps to a second line instead of widening the chip over the card
    }

    void bind(AcceptService.Card c, SharedPreferences sp) {
        lastCard = c; lastSp = sp;
        float d = density();
        int style = Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "chip_style", 1)));
        float s = SIZES[Math.max(0, Math.min(3, PrefsGuard.getInt(sp, "chip_size", 1)))] * fit;
        int alpha = ALPHAS[Math.max(0, Math.min(2, PrefsGuard.getInt(sp, "chip_alpha", 0)))];
        applyStatus(c, sp, s);
        boolean showLogo = sp.getBoolean("chip_logo", true), symbols = sp.getBoolean("cb_symbols", true);

        setMinimumWidth((int) (MIN_WIDTH_DP[style] * s * d));
        setPadding((int) ((showLogo ? 6 : 14) * s * d), (int) (3 * s * d), (int) (16 * s * d), (int) (3 * s * d));
        logo.setVisibility(showLogo && logoBmp != null ? VISIBLE : GONE);
        logo.setLayoutParams(new LayoutParams((int) (22 * s * d), (int) (22 * s * d)));
        main.setPadding((int) ((showLogo ? 8 : 0) * s * d), 0, (int) (14 * s * d), 0);

        int[] ring = {0xFFEF5350, 0xFFFFC107, 0xFF2EAD5B};      // low, average, worth it
        int[] word = {0xFFFF8A80, 0xFFFFD54F, 0xFF5BD68A};
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((alpha << 24) | 0x121212);
        bg.setCornerRadius(20 * s * d);
        bg.setStroke((int) (1.5f * d), c.verdict < 0 ? 0xFF98A2B3 : ring[c.verdict]);
        setBackground(bg);

        if (c.verdict < 0) {
            main.setTextSize(16 * s);
            setContentDescription("₹" + Math.round(c.fare));
            main.setText("₹" + Math.round(c.fare));
            verdict.setTextSize(9.5f * s);
            verdict.setText(I18n.t(sp, "No distance"));
            verdict.setTextColor(0xFFE2E8F0);
            keep.setText("");
            keep.setVisibility(GONE);
            return;
        }

        String sym = symbols ? SYMBOLS[c.verdict] + " " : "";
        String vword = sym + I18n.t(sp, AcceptService.CHIP_NAME[c.verdict]);
        double kept = c.fare - c.fuel;
        String keepTxt = I18n.t(sp, "Keep") + " ₹" + Math.round(kept);
        String perKm = "₹" + String.format(Locale.US, "%.2f", c.perKm) + "/km";
        int vcol = word[c.verdict], kcol = kept >= 0 ? 0xFF5BD68A : 0xFFFF8A80;
        String tag = c.tag.isEmpty() ? "" : "  ·  " + c.tag;

        setContentDescription(I18n.t(sp, AcceptService.CHIP_NAME[c.verdict]) + ", " + perKm + ", " + keepTxt);
        keep.setVisibility(VISIBLE);
        verdict.setVisibility(VISIBLE);
        switch (style) {
            case 0: // compact: the rate and the verdict, nothing else
                main.setTextSize(16 * s); main.setText(perKm);
                verdict.setTextSize(11 * s); verdict.setText(vword + tag);
                keep.setText(""); keep.setVisibility(GONE);
                break;
            case 2: // detailed: adds the fare and the fuel cost
                main.setTextSize(16 * s); main.setText(perKm);
                verdict.setTextSize(9.5f * s);
                verdict.setText(MainActivity.fmtDist(sp, c.dist) + " " + I18n.t(sp, "total") + "  ·  " + vword + tag);
                keep.setTextSize(11 * s);
                keep.setText(keepTxt + "  ·  " + I18n.t(sp, "fuel") + " ₹" + Math.round(c.fuel) + "  ·  " + I18n.t(sp, "Fare") + " ₹" + Math.round(c.fare));
                break;
            case 3: // simple: one big word and a colour, readable in a glance
                main.setTextSize(19 * s); main.setText(vword); main.setTextColor(vcol);
                verdict.setTextSize(11 * s); verdict.setText(perKm); verdict.setTextColor(Color.WHITE);
                keep.setTextSize(11 * s); keep.setText(keepTxt); keep.setTextColor(kcol);
                return;
            default: // standard
                main.setTextSize(16 * s); main.setText(perKm);
                verdict.setTextSize(9.5f * s);
                verdict.setText(MainActivity.fmtDist(sp, c.dist) + " " + I18n.t(sp, "total") + "  ·  " + vword + tag);
                keep.setTextSize(11 * s);
                keep.setText(keepTxt + "  ·  " + I18n.t(sp, "fuel") + " ₹" + Math.round(c.fuel));
        }
        main.setTextColor(Color.WHITE);
        verdict.setTextColor(vcol);
        keep.setTextColor(kcol);
    }
}
