package net.dvmn2.lightsetplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One light source: persistent settings plus runtime state (cached geometry, applied light levels).
 *
 * <p>Main thread only. Values are stored as given; validation and limits are applied by
 * {@code LightManager}. The runtime state is owned by {@code LightManager} and {@code LightBlockManager}.
 */
public final class LightSource {

    /** Maximum length of source and group names, characters. */
    public static final int MAX_NAME_LENGTH = 32;

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]{1," + MAX_NAME_LENGTH + "}$");

    private final String name;
    private final String key;
    private final LightType type;
    private final String world;

    private Vec3 position;
    private float yaw;
    private float pitch;
    private int brightness;
    private float angleDegrees;
    private int rangeBlocks;
    private int rays;
    private boolean enabled = true;
    private Effect effect = Effect.NONE;
    private @Nullable String group;
    private boolean beamEnabled = true;
    private @Nullable Vec3 secondPoint;

    private Geometry geometry = Geometry.EMPTY;
    private boolean geometryDirty = true;
    private Map<Long, Integer> applied = new HashMap<>();
    private double effectFactor = 1.0;
    private int lastEffectKey = -1;

    /**
     * Creates a source.
     *
     * @param name     unique name, see {@link #isValidName(String)}
     * @param type     kind of source
     * @param world    name of the world the source is in
     * @param position position, blocks
     */
    public LightSource(String name, LightType type, String world, Vec3 position) {
        this.name = name;
        this.key = keyOf(name);
        this.type = type;
        this.world = world;
        this.position = position;
    }

    /** Returns the lowercase lookup key of a source name. */
    public static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Returns whether a name matches the format allowed for sources and groups. */
    public static boolean isValidName(String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    // ---------- Persistent settings ----------

    /** Returns the display name. */
    public String getName() {
        return name;
    }

    /** Returns the lowercase lookup key of the name. */
    public String getKey() {
        return key;
    }

    /** Returns the kind of source. */
    public LightType getType() {
        return type;
    }

    /** Returns the name of the world the source is in. */
    public String getWorld() {
        return world;
    }

    /** Returns the position, blocks. */
    public Vec3 getPosition() {
        return position;
    }

    /** Sets the position, blocks. */
    public void setPosition(Vec3 position) {
        this.position = position;
    }

    /** Returns the yaw, degrees. */
    public float getYaw() {
        return yaw;
    }

    /** Returns the pitch, degrees. */
    public float getPitch() {
        return pitch;
    }

    /** Sets the rotation, degrees. */
    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** Returns the brightness, {@code 0..15}. */
    public int getBrightness() {
        return brightness;
    }

    /** Sets the brightness, {@code 0..15}. */
    public void setBrightness(int brightness) {
        this.brightness = brightness;
    }

    /** Returns the full cone angle of a spotlight, degrees. */
    public float getAngleDegrees() {
        return angleDegrees;
    }

    /** Sets the full cone angle of a spotlight, degrees. */
    public void setAngleDegrees(float angleDegrees) {
        this.angleDegrees = angleDegrees;
    }

    /** Returns the range of a spotlight, blocks. */
    public int getRangeBlocks() {
        return rangeBlocks;
    }

    /** Sets the range of a spotlight, blocks. */
    public void setRangeBlocks(int rangeBlocks) {
        this.rangeBlocks = rangeBlocks;
    }

    /** Returns the number of rays of a spotlight. */
    public int getRays() {
        return rays;
    }

    /** Sets the number of rays of a spotlight. */
    public void setRays(int rays) {
        this.rays = rays;
    }

    /** Returns whether the source is switched on. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Switches the source on or off. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Returns the brightness effect. */
    public Effect getEffect() {
        return effect;
    }

    /** Sets the brightness effect. */
    public void setEffect(Effect effect) {
        this.effect = effect;
    }

    /** Returns the group name, or {@code null} when the source is not in a group. */
    public @Nullable String getGroup() {
        return group;
    }

    /** Sets the group name, or {@code null} to leave the group. */
    public void setGroup(@Nullable String group) {
        this.group = group;
    }

    /** Returns whether the particle beam of a spotlight is shown. */
    public boolean isBeamEnabled() {
        return beamEnabled;
    }

    /** Shows or hides the particle beam of a spotlight. */
    public void setBeamEnabled(boolean beamEnabled) {
        this.beamEnabled = beamEnabled;
    }

    /** Returns the second point of a strip or area, or {@code null} when not set. */
    public @Nullable Vec3 getSecondPoint() {
        return secondPoint;
    }

    /** Sets the second point of a strip or area. */
    public void setSecondPoint(@Nullable Vec3 secondPoint) {
        this.secondPoint = secondPoint;
    }

    /** Returns the unit direction vector of the source rotation. */
    public Vec3 getDirection() {
        return Vec3.fromRotation(yaw, pitch);
    }

    // ---------- Runtime state ----------

    /** Returns the cached geometry. */
    public Geometry getGeometry() {
        return geometry;
    }

    /** Replaces the cached geometry. */
    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    /** Returns whether the cached geometry is outdated. */
    public boolean isGeometryDirty() {
        return geometryDirty;
    }

    /** Marks the cached geometry as outdated or up to date. */
    public void setGeometryDirty(boolean geometryDirty) {
        this.geometryDirty = geometryDirty;
    }

    /**
     * Returns the light levels this source currently contributes (packed position to level).
     * The map is owned by {@code LightBlockManager}; other callers MUST NOT modify it.
     */
    public Map<Long, Integer> getApplied() {
        return applied;
    }

    /** Replaces the contributed light levels. */
    public void setApplied(Map<Long, Integer> applied) {
        this.applied = applied;
    }

    /** Returns the number of light blocks this source currently contributes. */
    public int getBlockCount() {
        return applied.size();
    }

    /** Returns the current effect brightness multiplier, {@code 0..1}. */
    public double getEffectFactor() {
        return effectFactor;
    }

    /** Sets the current effect brightness multiplier, {@code 0..1}. */
    public void setEffectFactor(double effectFactor) {
        this.effectFactor = effectFactor;
    }

    /** Returns the last effect level that was applied, or {@code -1} when none. */
    public int getLastEffectKey() {
        return lastEffectKey;
    }

    /** Sets the last effect level that was applied. */
    public void setLastEffectKey(int lastEffectKey) {
        this.lastEffectKey = lastEffectKey;
    }
}
