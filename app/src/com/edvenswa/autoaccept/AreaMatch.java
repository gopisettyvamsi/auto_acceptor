package com.edvenswa.autoaccept;

import java.util.Locale;

/** Matches a ride's address against the place names a driver listed for Go To / Stay In. */
final class AreaMatch {
    /** True if the address contains any of the comma-separated place names (case-insensitive). An empty list never matches. */
    static boolean hasAny(String address, String csv) {
        if (address == null || csv == null) return false;
        String a = address.toLowerCase(Locale.ROOT);
        for (String part : csv.split("[,\\n]")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (!p.isEmpty() && a.contains(p)) return true;
        }
        return false;
    }
}
