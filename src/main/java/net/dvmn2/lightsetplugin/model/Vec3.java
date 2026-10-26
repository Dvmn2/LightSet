package net.dvmn2.lightsetplugin.model;

/**
 * Immutable three-dimensional vector or point, in blocks.
 *
 * @param x X component
 * @param y Y component
 * @param z Z component
 */
public record Vec3(double x, double y, double z) {

    /**
     * Builds the unit direction vector of a rotation (same convention as {@code Location#getDirection}).
     *
     * @param yaw   yaw, degrees
     * @param pitch pitch, degrees
     * @return unit vector
     */
    public static Vec3 fromRotation(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
    }
}
