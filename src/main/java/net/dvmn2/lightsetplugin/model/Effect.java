package net.dvmn2.lightsetplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Brightness effect of a light source.
 */
public enum Effect {
    /** No effect: constant brightness. */
    NONE,
    /** Random brightness changes. */
    FLICKER,
    /** Smooth sine-wave brightness changes. */
    PULSE,
    /** Alternating fully on and fully off. */
    BLINK;

    /** Returns the lowercase key used in commands and data files. */
    public String getKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds an effect by its key.
     *
     * @param key key, case-insensitive
     * @return the effect, or {@code null} when the key is unknown
     */
    public static @Nullable Effect fromKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        for (Effect effect : values()) {
            if (effect.name().equalsIgnoreCase(key)) {
                return effect;
            }
        }
        return null;
    }
}
