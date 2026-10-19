package net.dvmn2.lightset;

import java.util.Locale;

/**
 * Эффект яркости источника.
 */
public enum Effect {
    NONE, FLICKER, PULSE, BLINK;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Effect fromKey(String key) {
        if (key == null) {
            return null;
        }
        try {
            return valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
