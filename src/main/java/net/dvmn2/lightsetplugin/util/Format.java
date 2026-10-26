package net.dvmn2.lightsetplugin.util;

/**
 * Stateless helpers that turn numbers into text for chat messages.
 */
public final class Format {

    private static final double DECIMAL_SCALE = 10.0;

    private Format() {
    }

    /** Rounds a number to one decimal place and returns it as text (locale independent). */
    public static String decimal(double value) {
        return String.valueOf(Math.round(value * DECIMAL_SCALE) / DECIMAL_SCALE);
    }
}
