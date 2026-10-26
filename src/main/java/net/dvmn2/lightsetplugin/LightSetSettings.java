package net.dvmn2.lightsetplugin;

import net.dvmn2.lightsetplugin.model.LightType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Immutable snapshot of {@code config.yml}. Built by {@link #parse(ConfigurationSection, Logger)};
 * invalid values are reported with a warning and replaced by the default.
 *
 * @param language    {@code ru}, {@code en} or {@code auto}
 * @param limits      hard limits
 * @param defaults    values for new sources
 * @param spot        spotlight falloff
 * @param shapes      strip and area spacing
 * @param performance per-tick budgets
 * @param effects     effect parameters
 * @param beam        particle beam
 * @param model       visible model
 */
public record LightSetSettings(
        String language,
        Limits limits,
        Defaults defaults,
        Spot spot,
        Shapes shapes,
        Performance performance,
        Effects effects,
        Beam beam,
        Model model) {

    private static final String ROOT = "settings.";
    private static final String DEFAULT_LANGUAGE = "auto";
    private static final String DEFAULT_COLOR = "#FFF4C2";
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final int DEFAULT_COLOR_RGB = 0xFFF4C2;

    /**
     * Hard limits.
     *
     * @param maxRangeBlocks       maximum spotlight range, blocks
     * @param maxAngleDegrees      maximum full cone angle, degrees
     * @param maxRays              maximum rays per spotlight
     * @param maxBlocksPerSource   maximum light blocks per source
     * @param maxTotalBlocks       maximum light blocks on the server
     */
    public record Limits(int maxRangeBlocks, float maxAngleDegrees, int maxRays, int maxBlocksPerSource,
                         int maxTotalBlocks) {
    }

    /**
     * Values for new sources.
     *
     * @param brightness   brightness {@code 0..15}
     * @param angleDegrees full cone angle, degrees
     * @param rangeBlocks  range, blocks
     * @param rays         rays per spotlight
     */
    public record Defaults(int brightness, float angleDegrees, int rangeBlocks, int rays) {
    }

    /**
     * Spotlight falloff, {@code 0..1}.
     *
     * @param distanceFalloff fade at the maximum range
     * @param edgeFalloff     fade at the cone edge
     */
    public record Spot(double distanceFalloff, double edgeFalloff) {
    }

    /**
     * Spacing of light blocks for strips and areas.
     *
     * @param stripSpacingBlocks distance between blocks along a strip, blocks
     * @param areaSpacingBlocks  distance between blocks inside an area, blocks
     */
    public record Shapes(int stripSpacingBlocks, int areaSpacingBlocks) {
    }

    /**
     * Per-tick work budgets.
     *
     * @param sourceUpdatesPerTick sources recalculated per tick
     * @param blockUpdatesPerTick  light blocks placed or removed per tick
     */
    public record Performance(int sourceUpdatesPerTick, int blockUpdatesPerTick) {
    }

    /**
     * Effect parameters.
     *
     * @param intervalTicks how often effects are evaluated, ticks
     * @param flicker       flicker parameters
     * @param pulse         pulse parameters
     * @param blink         blink parameters
     */
    public record Effects(int intervalTicks, Flicker flicker, Pulse pulse, Blink blink) {
    }

    /**
     * Flicker effect.
     *
     * @param min          lowest brightness multiplier
     * @param changeChance chance to pick a new brightness on each evaluation
     */
    public record Flicker(double min, double changeChance) {
    }

    /**
     * Pulse effect.
     *
     * @param periodTicks length of one pulse, ticks
     * @param min         lowest brightness multiplier
     */
    public record Pulse(int periodTicks, double min) {
    }

    /**
     * Blink effect.
     *
     * @param onTicks  time the light stays on, ticks
     * @param offTicks time the light stays off, ticks
     */
    public record Blink(int onTicks, int offTicks) {
    }

    /**
     * Particle beam.
     *
     * @param enabledByDefault   whether new spotlights show the beam
     * @param intervalTicks      redraw interval, ticks
     * @param viewDistanceBlocks players farther than this do not see particles, blocks
     * @param stepBlocks         distance between particles, blocks
     * @param dust               particle colour and size
     */
    public record Beam(boolean enabledByDefault, int intervalTicks, double viewDistanceBlocks, double stepBlocks,
                       Particle.DustOptions dust) {
    }

    /**
     * Visible model.
     *
     * @param enabled              whether models are spawned
     * @param scale                model scale
     * @param viewRangeMultiplier  view range multiplier, 1.0 = 64 blocks
     * @param yawOffsetDegrees     yaw correction, degrees
     * @param pitchOffsetDegrees   pitch correction, degrees
     * @param items                item shown per source type
     */
    public record Model(boolean enabled, float scale, float viewRangeMultiplier, float yawOffsetDegrees,
                        float pitchOffsetDegrees, Map<LightType, ModelItem> items) {

        /** Copies the item map so that the snapshot stays immutable. */
        public Model {
            items = Map.copyOf(items);
        }
    }

    /**
     * Item that represents a source type.
     *
     * @param material  item material
     * @param itemModel optional resource pack model, or {@code null}
     */
    public record ModelItem(Material material, @Nullable NamespacedKey itemModel) {
    }

    /**
     * Reads a snapshot from the configuration.
     *
     * @param config plugin configuration
     * @param log    logger for warnings about invalid values
     * @return the snapshot
     */
    public static LightSetSettings parse(ConfigurationSection config, Logger log) {
        Reader in = new Reader(config, log);

        String language = in.string(ROOT + "language", DEFAULT_LANGUAGE).toLowerCase(Locale.ROOT);
        if (!language.equals("ru") && !language.equals("en") && !language.equals("auto")) {
            log.warning(ROOT + "language must be ru, en or auto, got '" + language + "'; using "
                    + DEFAULT_LANGUAGE);
            language = DEFAULT_LANGUAGE;
        }

        Limits limits = new Limits(
                in.integer("limits.max-range-blocks", 320, 1, 2048),
                (float) in.decimal("limits.max-angle-degrees", 160.0, 1.0, 179.0),
                in.integer("limits.max-rays", 400, 1, 5000),
                in.integer("limits.max-blocks-per-source", 1024, 1, 100000),
                in.integer("limits.max-total-blocks", 30000, 1, 10000000));

        Defaults defaults = new Defaults(
                in.integer("defaults.brightness", 15, 0, 15),
                (float) in.decimal("defaults.angle-degrees", 40.0, 1.0, limits.maxAngleDegrees()),
                in.integer("defaults.range-blocks", 250, 1, limits.maxRangeBlocks()),
                in.integer("defaults.rays", 120, 1, limits.maxRays()));

        Spot spot = new Spot(
                in.decimal("spot.distance-falloff", 0.5, 0.0, 1.0),
                in.decimal("spot.edge-falloff", 0.5, 0.0, 1.0));

        Shapes shapes = new Shapes(
                in.integer("shapes.strip-spacing-blocks", 4, 1, 64),
                in.integer("shapes.area-spacing-blocks", 6, 1, 64));

        Performance performance = new Performance(
                in.integer("performance.source-updates-per-tick", 1, 1, 100),
                in.integer("performance.block-updates-per-tick", 300, 1, 100000));

        Effects effects = new Effects(
                in.integer("effects.interval-ticks", 4, 1, 100),
                new Flicker(
                        in.decimal("effects.flicker.min", 0.4, 0.0, 1.0),
                        in.decimal("effects.flicker.change-chance", 0.5, 0.0, 1.0)),
                new Pulse(
                        in.integer("effects.pulse.period-ticks", 60, 4, 100000),
                        in.decimal("effects.pulse.min", 0.2, 0.0, 1.0)),
                new Blink(
                        in.integer("effects.blink.on-ticks", 20, 1, 100000),
                        in.integer("effects.blink.off-ticks", 20, 1, 100000)));

        Beam beam = new Beam(
                in.bool("beam.enabled-by-default", true),
                in.integer("beam.interval-ticks", 5, 1, 200),
                in.decimal("beam.view-distance-blocks", 64.0, 4.0, 512.0),
                in.decimal("beam.step-blocks", 3.0, 0.5, 32.0),
                new Particle.DustOptions(in.color("beam.color"), (float) in.decimal("beam.size", 1.0, 0.1, 4.0)));

        Model model = new Model(
                in.bool("model.enabled", true),
                (float) in.decimal("model.scale", 1.0, 0.05, 16.0),
                (float) in.decimal("model.view-range-multiplier", 2.0, 0.1, 16.0),
                (float) in.decimal("model.yaw-offset-degrees", 0.0, -360.0, 360.0),
                (float) in.decimal("model.pitch-offset-degrees", 0.0, -360.0, 360.0),
                in.modelItems());

        return new LightSetSettings(language, limits, defaults, spot, shapes, performance, effects, beam, model);
    }

    /**
     * Reads typed values from a configuration section and reports invalid ones.
     */
    private static final class Reader {

        private final ConfigurationSection config;
        private final Logger log;

        Reader(ConfigurationSection config, Logger log) {
            this.config = config;
            this.log = log;
        }

        int integer(String path, int def, int min, int max) {
            int fallback = Math.max(min, Math.min(max, def));
            Object raw = config.get(path);
            if (raw == null) {
                return fallback;
            }
            if (!(raw instanceof Number number)) {
                log.warning(path + " must be a number, got '" + raw + "'; using " + fallback);
                return fallback;
            }
            long value = number.longValue();
            if (value < min || value > max) {
                log.warning(path + " must be in " + min + ".." + max + ", got " + value + "; using " + fallback);
                return fallback;
            }
            return (int) value;
        }

        double decimal(String path, double def, double min, double max) {
            double fallback = Math.max(min, Math.min(max, def));
            Object raw = config.get(path);
            if (raw == null) {
                return fallback;
            }
            if (!(raw instanceof Number number)) {
                log.warning(path + " must be a number, got '" + raw + "'; using " + fallback);
                return fallback;
            }
            double value = number.doubleValue();
            if (!Double.isFinite(value) || value < min || value > max) {
                log.warning(path + " must be in " + min + ".." + max + ", got " + value + "; using " + fallback);
                return fallback;
            }
            return value;
        }

        boolean bool(String path, boolean def) {
            Object raw = config.get(path);
            if (raw == null) {
                return def;
            }
            if (!(raw instanceof Boolean value)) {
                log.warning(path + " must be true or false, got '" + raw + "'; using " + def);
                return def;
            }
            return value;
        }

        String string(String path, String def) {
            Object raw = config.get(path);
            if (raw == null) {
                return def;
            }
            return String.valueOf(raw);
        }

        Color color(String path) {
            String raw = string(path, DEFAULT_COLOR).trim();
            if (!COLOR_PATTERN.matcher(raw).matches()) {
                log.warning(path + " must look like #RRGGBB, got '" + raw + "'; using " + DEFAULT_COLOR);
                return Color.fromRGB(DEFAULT_COLOR_RGB);
            }
            return Color.fromRGB(Integer.parseInt(raw.substring(1), 16));
        }

        Map<LightType, ModelItem> modelItems() {
            Map<LightType, ModelItem> items = new EnumMap<>(LightType.class);
            for (LightType type : LightType.values()) {
                String base = "model.items." + type.getKey();
                Material def = defaultMaterial(type);
                items.put(type, new ModelItem(material(base + ".material", def), itemModel(base + ".item-model")));
            }
            return items;
        }

        private Material material(String path, Material def) {
            String raw = string(path, def.name()).trim();
            Material material = Material.matchMaterial(raw);
            if (material == null || !material.isItem() || material.isAir()) {
                log.warning(path + " is not a valid item material: '" + raw + "'; using " + def.name());
                return def;
            }
            return material;
        }

        private @Nullable NamespacedKey itemModel(String path) {
            String raw = string(path, "").trim();
            if (raw.isEmpty()) {
                return null;
            }
            NamespacedKey key = NamespacedKey.fromString(raw);
            if (key == null) {
                log.warning(path + " must look like namespace:path, got '" + raw + "'; no item model used");
            }
            return key;
        }

        private static Material defaultMaterial(LightType type) {
            return switch (type) {
                case SPOT -> Material.SPYGLASS;
                case LAMP -> Material.SEA_LANTERN;
                case STRIP -> Material.END_ROD;
                case AREA -> Material.SHROOMLIGHT;
            };
        }
    }
}
