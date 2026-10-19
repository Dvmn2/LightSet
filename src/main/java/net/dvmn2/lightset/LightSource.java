package net.dvmn2.lightset;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Один источник света: сохраняемые настройки + рантайм-состояние (кэш геометрии,
 * применённые световые блоки, id модели). Изменяется только из основного потока.
 */
public final class LightSource {

    public final String name;
    LightType type;
    String world;
    double x, y, z;
    float yaw, pitch;
    int brightness = 15;
    float angle = 40f;
    int range = 250;
    int rays = 120;
    boolean enabled = true;
    Effect effect = Effect.NONE;
    String group;
    boolean beam = true;
    boolean hasPos2;
    double x2, y2, z2;

    // ---- рантайм (не сохраняется) ----
    UUID displayId;
    long[] hitPos = new long[0];
    float[] hitFactor = new float[0];
    double beamLength;
    boolean truncated;
    boolean geometryDirty = true;
    Map<Long, Integer> applied = new HashMap<>();
    double effectFactor = 1.0;
    int lastEffectKey = -1;

    public LightSource(String name, LightType type) {
        this.name = name;
        this.type = type;
    }

    public LightType type() {
        return type;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public int brightness() {
        return brightness;
    }

    public float angle() {
        return angle;
    }

    public int range() {
        return range;
    }

    public int rays() {
        return rays;
    }

    public boolean enabled() {
        return enabled;
    }

    public Effect effect() {
        return effect;
    }

    public String group() {
        return group;
    }

    public boolean beam() {
        return beam;
    }

    public boolean hasPos2() {
        return hasPos2;
    }

    public double x2() {
        return x2;
    }

    public double y2() {
        return y2;
    }

    public double z2() {
        return z2;
    }

    /**
     * Число световых блоков, которые источник сейчас держит в мире.
     */
    public int blockCount() {
        return applied.size();
    }

    /**
     * Единичный вектор направления (как у Location#getDirection).
     */
    double[] direction() {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        return new double[]{-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp};
    }

    void save(ConfigurationSection c) {
        c.set("type", type.key());
        c.set("world", world);
        c.set("x", x);
        c.set("y", y);
        c.set("z", z);
        c.set("yaw", (double) yaw);
        c.set("pitch", (double) pitch);
        c.set("brightness", brightness);
        c.set("angle", (double) angle);
        c.set("range", range);
        c.set("rays", rays);
        c.set("enabled", enabled);
        c.set("effect", effect.key());
        c.set("group", group);
        c.set("beam", beam);
        if (hasPos2) {
            c.set("x2", x2);
            c.set("y2", y2);
            c.set("z2", z2);
        }
    }

    static LightSource load(String name, ConfigurationSection c) {
        String world = c.getString("world");
        if (world == null) {
            return null;
        }
        LightType type = LightType.fromKey(c.getString("type", "spot"));
        if (type == null) {
            type = LightType.SPOT;
        }
        LightSource s = new LightSource(name, type);
        s.world = world;
        s.x = c.getDouble("x");
        s.y = c.getDouble("y");
        s.z = c.getDouble("z");
        s.yaw = (float) c.getDouble("yaw");
        s.pitch = (float) c.getDouble("pitch");
        s.brightness = Math.max(0, Math.min(15, c.getInt("brightness", 15)));
        s.angle = (float) c.getDouble("angle", 40.0);
        s.range = Math.max(1, c.getInt("range", 250));
        s.rays = Math.max(1, c.getInt("rays", 120));
        s.enabled = c.getBoolean("enabled", true);
        Effect effect = Effect.fromKey(c.getString("effect", "none"));
        s.effect = effect == null ? Effect.NONE : effect;
        String group = c.getString("group");
        s.group = (group == null || group.isBlank()) ? null : group;
        s.beam = c.getBoolean("beam", true);
        if (c.contains("x2")) {
            s.hasPos2 = true;
            s.x2 = c.getDouble("x2");
            s.y2 = c.getDouble("y2");
            s.z2 = c.getDouble("z2");
        }
        return s;
    }
}
