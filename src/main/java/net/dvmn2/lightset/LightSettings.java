package net.dvmn2.lightset;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Значения из config.yml с проверкой диапазонов. {@link #reload()} перечитывает файл.
 */
public final class LightSettings {

    private final JavaPlugin plugin;

    private int maxRange;
    private float maxAngle;
    private int maxRays;
    private int maxBlocksPerSource;
    private int maxTotalBlocks;

    private int defaultBrightness;
    private float defaultAngle;
    private int defaultRange;
    private int defaultRays;

    private double distanceFalloff;
    private double edgeFalloff;

    private int stripSpacing;
    private int areaSpacing;

    private int sourceUpdatesPerTick;
    private int blockUpdatesPerTick;

    private int effectsInterval;
    private double flickerMin;
    private double flickerChance;
    private int pulsePeriod;
    private double pulseMin;
    private int blinkOn;
    private int blinkOff;

    private boolean beamDefault;
    private int beamInterval;
    private double beamView;
    private double beamStep;
    private Particle.DustOptions dust;

    private boolean modelEnabled;
    private float modelScale;
    private float modelViewRange;
    private float yawOffset;
    private float pitchOffset;
    private final Map<LightType, Material> modelMaterials = new EnumMap<>(LightType.class);
    private final Map<LightType, String> modelItemModels = new EnumMap<>(LightType.class);

    public LightSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        maxRange = clamp(c.getInt("limits.max-range", 320), 1, 2048);
        maxAngle = (float) clamp(c.getDouble("limits.max-angle", 160.0), 1.0, 179.0);
        maxRays = clamp(c.getInt("limits.max-rays", 400), 1, 5000);
        maxBlocksPerSource = clamp(c.getInt("limits.max-blocks-per-source", 1024), 1, 100000);
        maxTotalBlocks = clamp(c.getInt("limits.max-total-blocks", 30000), 1, 10000000);

        defaultBrightness = clamp(c.getInt("defaults.brightness", 15), 0, 15);
        defaultAngle = (float) clamp(c.getDouble("defaults.angle", 40.0), 1.0, maxAngle);
        defaultRange = clamp(c.getInt("defaults.range", 250), 1, maxRange);
        defaultRays = clamp(c.getInt("defaults.rays", 120), 1, maxRays);

        distanceFalloff = clamp(c.getDouble("spot.distance-falloff", 0.5), 0.0, 1.0);
        edgeFalloff = clamp(c.getDouble("spot.edge-falloff", 0.5), 0.0, 1.0);

        stripSpacing = clamp(c.getInt("shapes.strip-spacing", 4), 1, 64);
        areaSpacing = clamp(c.getInt("shapes.area-spacing", 6), 1, 64);

        sourceUpdatesPerTick = clamp(c.getInt("performance.source-updates-per-tick", 1), 1, 100);
        blockUpdatesPerTick = clamp(c.getInt("performance.block-updates-per-tick", 300), 1, 100000);

        effectsInterval = clamp(c.getInt("effects.interval-ticks", 4), 1, 100);
        flickerMin = clamp(c.getDouble("effects.flicker.min", 0.4), 0.0, 1.0);
        flickerChance = clamp(c.getDouble("effects.flicker.change-chance", 0.5), 0.0, 1.0);
        pulsePeriod = clamp(c.getInt("effects.pulse.period-ticks", 60), 4, 100000);
        pulseMin = clamp(c.getDouble("effects.pulse.min", 0.2), 0.0, 1.0);
        blinkOn = clamp(c.getInt("effects.blink.on-ticks", 20), 1, 100000);
        blinkOff = clamp(c.getInt("effects.blink.off-ticks", 20), 1, 100000);

        beamDefault = c.getBoolean("beam.enabled-by-default", true);
        beamInterval = clamp(c.getInt("beam.interval-ticks", 5), 1, 200);
        beamView = clamp(c.getDouble("beam.view-distance", 64.0), 4.0, 512.0);
        beamStep = clamp(c.getDouble("beam.step", 3.0), 0.5, 32.0);
        Color color = parseColor(c.getString("beam.color", "#FFF4C2"), Color.fromRGB(0xFFF4C2));
        float size = (float) clamp(c.getDouble("beam.size", 1.0), 0.1, 4.0);
        dust = new Particle.DustOptions(color, size);

        modelEnabled = c.getBoolean("model.enabled", true);
        modelScale = (float) clamp(c.getDouble("model.scale", 1.0), 0.05, 16.0);
        modelViewRange = (float) clamp(c.getDouble("model.view-range", 2.0), 0.1, 16.0);
        yawOffset = (float) c.getDouble("model.yaw-offset", 0.0);
        pitchOffset = (float) c.getDouble("model.pitch-offset", 0.0);
        modelMaterials.clear();
        modelItemModels.clear();
        for (LightType type : LightType.values()) {
            String base = "model.items." + type.key();
            Material def = defaultMaterial(type);
            Material m = Material.matchMaterial(c.getString(base + ".material", def.name()));
            if (m == null || !m.isItem() || m.isAir()) {
                m = def;
            }
            modelMaterials.put(type, m);
            modelItemModels.put(type, c.getString(base + ".item-model", ""));
        }
    }

    private static Material defaultMaterial(LightType type) {
        return switch (type) {
            case SPOT -> Material.SPYGLASS;
            case LAMP -> Material.SEA_LANTERN;
            case STRIP -> Material.END_ROD;
            case AREA -> Material.SHROOMLIGHT;
        };
    }

    private static Color parseColor(String s, Color def) {
        if (s == null) {
            return def;
        }
        try {
            String hex = s.startsWith("#") ? s.substring(1) : s;
            return Color.fromRGB(Integer.parseInt(hex.trim(), 16));
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Предмет для item_display выбранного типа.
     */
    public ItemStack modelItem(LightType type) {
        ItemStack item = new ItemStack(modelMaterials.getOrDefault(type, Material.SEA_LANTERN));
        String model = modelItemModels.get(type);
        if (model != null && !model.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(model.trim());
            if (key != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setItemModel(key);
                    item.setItemMeta(meta);
                }
            }
        }
        return item;
    }

    public int maxRange() {
        return maxRange;
    }

    public float maxAngle() {
        return maxAngle;
    }

    public int maxRays() {
        return maxRays;
    }

    public int maxBlocksPerSource() {
        return maxBlocksPerSource;
    }

    public int maxTotalBlocks() {
        return maxTotalBlocks;
    }

    public int defaultBrightness() {
        return defaultBrightness;
    }

    public float defaultAngle() {
        return defaultAngle;
    }

    public int defaultRange() {
        return defaultRange;
    }

    public int defaultRays() {
        return defaultRays;
    }

    public double distanceFalloff() {
        return distanceFalloff;
    }

    public double edgeFalloff() {
        return edgeFalloff;
    }

    public int stripSpacing() {
        return stripSpacing;
    }

    public int areaSpacing() {
        return areaSpacing;
    }

    public int sourceUpdatesPerTick() {
        return sourceUpdatesPerTick;
    }

    public int blockUpdatesPerTick() {
        return blockUpdatesPerTick;
    }

    public int effectsInterval() {
        return effectsInterval;
    }

    public double flickerMin() {
        return flickerMin;
    }

    public double flickerChance() {
        return flickerChance;
    }

    public int pulsePeriod() {
        return pulsePeriod;
    }

    public double pulseMin() {
        return pulseMin;
    }

    public int blinkOn() {
        return blinkOn;
    }

    public int blinkOff() {
        return blinkOff;
    }

    public boolean beamDefault() {
        return beamDefault;
    }

    public int beamInterval() {
        return beamInterval;
    }

    public double beamView() {
        return beamView;
    }

    public double beamStep() {
        return beamStep;
    }

    public Particle.DustOptions dust() {
        return dust;
    }

    public boolean modelEnabled() {
        return modelEnabled;
    }

    public float modelScale() {
        return modelScale;
    }

    public float modelViewRange() {
        return modelViewRange;
    }

    public float yawOffset() {
        return yawOffset;
    }

    public float pitchOffset() {
        return pitchOffset;
    }
}
