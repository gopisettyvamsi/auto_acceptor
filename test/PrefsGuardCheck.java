package com.edvenswa.autoaccept;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reproduces the crash: an old build saved "chip_pos" as text ("bottom"); the current build reads it as a number.
 *
 * Run: javac -encoding UTF-8 -cp "$ANDROID_JAR" -d out app/src/com/edvenswa/autoaccept/PrefsGuard.java test/PrefsGuardCheck.java
 *      java -ea -cp "out:$ANDROID_JAR" com.edvenswa.autoaccept.PrefsGuardCheck
 */
public class PrefsGuardCheck {
    /** A map-backed SharedPreferences that behaves like Android's: getInt on a text value throws ClassCastException. */
    static class Fake implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();

        public Map<String, ?> getAll() { return new HashMap<>(m); }
        public String getString(String k, String d) { Object v = m.get(k); return v == null ? d : (String) v; }
        public Set<String> getStringSet(String k, Set<String> d) { return d; }
        public int getInt(String k, int d) { Object v = m.get(k); return v == null ? d : (Integer) v; }
        public long getLong(String k, long d) { return d; }
        public float getFloat(String k, float d) { return d; }
        public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v == null ? d : (Boolean) v; }
        public boolean contains(String k) { return m.containsKey(k); }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        public Editor edit() {
            return new Editor() {
                public Editor putString(String k, String v) { m.put(k, v); return this; }
                public Editor putStringSet(String k, Set<String> v) { return this; }
                public Editor putInt(String k, int v) { m.put(k, v); return this; }
                public Editor putLong(String k, long v) { return this; }
                public Editor putFloat(String k, float v) { return this; }
                public Editor putBoolean(String k, boolean v) { m.put(k, v); return this; }
                public Editor remove(String k) { m.remove(k); return this; }
                public Editor clear() { m.clear(); return this; }
                public boolean commit() { return true; }
                public void apply() { }
            };
        }
    }

    public static void main(String[] args) {
        // the bug, as it was on the phone
        Fake sp = new Fake();
        sp.m.put("chip_pos", "bottom");
        boolean crashed = false;
        try { sp.getInt("chip_pos", 0); } catch (ClassCastException ex) { crashed = true; }
        assert crashed : "the fake must reproduce the crash";

        // the guarded read survives and falls back to the default
        assert PrefsGuard.getInt(sp, "chip_pos", 0) == 0 : "guarded read returns the default";
        assert !sp.m.containsKey("chip_pos") : "the bad value is removed";

        // startup repair: wrong types removed, right types and unknown keys untouched
        Fake s2 = new Fake();
        s2.m.put("chip_pos", "top");          // text where a number is expected
        s2.m.put("speak", "yes");             // text where a number is expected
        s2.m.put("flash", 1);                 // number where a boolean is expected
        s2.m.put("chip_style", 2);            // fine
        s2.m.put("on", true);                 // fine
        s2.m.put("fare", "40");               // a text setting by design
        s2.m.put("chip_dx", 12);              // leftover from an old build, not checked
        PrefsGuard.sanitize(s2);
        assert !s2.m.containsKey("chip_pos") && !s2.m.containsKey("speak") && !s2.m.containsKey("flash") : "wrong types removed";
        assert (Integer) s2.m.get("chip_style") == 2 && (Boolean) s2.m.get("on") && "40".equals(s2.m.get("fare")) : "good values kept";
        assert s2.m.containsKey("chip_dx") : "unknown keys are left alone";

        // after the repair every read works
        assert PrefsGuard.getInt(s2, "chip_pos", 0) == 0 && PrefsGuard.getInt(s2, "chip_style", 1) == 2;
        System.out.println("PrefsGuard OK");
    }
}
