package net.dvmn2.lightsetplugin.util;

/**
 * Stateless helpers for rotation angles, in degrees.
 */
public final class Angles {

    private static final float HALF_TURN_DEGREES = 180f;
    private static final float FULL_TURN_DEGREES = 360f;
    private static final float MAX_PITCH_DEGREES = 90f;

    private Angles() {
    }

    /** Wraps a yaw into the range {@code (-180, 180]}. */
    public static float normalizeYaw(float yaw) {
        float wrapped = yaw % FULL_TURN_DEGREES;
        if (wrapped > HALF_TURN_DEGREES) {
            wrapped -= FULL_TURN_DEGREES;
        } else if (wrapped <= -HALF_TURN_DEGREES) {
            wrapped += FULL_TURN_DEGREES;
        }
        return wrapped;
    }

    /** Limits a pitch to the range {@code [-90, 90]}. */
    public static float clampPitch(float pitch) {
        return Math.max(-MAX_PITCH_DEGREES, Math.min(MAX_PITCH_DEGREES, pitch));
    }
}
