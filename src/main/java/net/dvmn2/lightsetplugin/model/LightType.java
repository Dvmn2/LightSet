package net.dvmn2.lightsetplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Kind of light source.
 */
public enum LightType {
    /** Directional spotlight: rays inside a cone, light on the surfaces they hit. */
    SPOT,
    /** Point lamp: a single light block. */
    LAMP,
    /** Line of light between two points. */
    STRIP,
    /** Box-shaped area filled with a grid of light blocks. */
    AREA;

    /** Returns the lowercase key used in commands, config and data files. */
    public String getKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds a type by its key.
     *
     * @param key key, case-insensitive
     * @return the type, or {@code null} when the key is unknown
     */
    public static @Nullable LightType fromKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        for (LightType type : values()) {
            if (type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
