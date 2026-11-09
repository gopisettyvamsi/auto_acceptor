package com.edvenswa.autoaccept;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Protects the app from settings saved by older versions under the same name but a different type
 * (an old build saved the chip position as text, the current one reads it as a number, and the mismatch crashed the app).
 * A wrong-typed setting is removed so its default applies, instead of throwing.
 */
final class PrefsGuard {
    static final Set<String> INTS = new HashSet<>(Arrays.asList(
        "chip_style", "chip_size", "chip_alpha", "chip_pos", "text_scale", "sort", "speak", "bx", "by"));
    static final Set<String> BOOLEANS = new HashSet<>(Arrays.asList(
        "on", "chip", "alert", "reject", "bubble", "trip", "notif", "summary", "dark", "onehand", "onboarded", "large",
        "vib_verdict", "flash", "hc", "cb_symbols", "big_taps", "voice", "chip_logo", "goto_on", "goto_strict", "stay_on", "stay_strict"));

    /** Removes every known setting whose stored type is not the type the app expects. */
    static void sanitize(SharedPreferences sp) {
        SharedPreferences.Editor ed = null;
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            boolean bad = (INTS.contains(k) && !(v instanceof Integer)) || (BOOLEANS.contains(k) && !(v instanceof Boolean));
            if (bad) {
                if (ed == null) ed = sp.edit();
                ed.remove(k);
            }
        }
        if (ed != null) ed.apply();
    }

    /** Reads a number setting; a value stored as another type counts as missing. */
    static int getInt(SharedPreferences sp, String key, int def) {
        try {
            return sp.getInt(key, def);
        } catch (ClassCastException ex) {
            sp.edit().remove(key).apply();
            return def;
        }
    }
}
