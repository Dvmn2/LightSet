package net.dvmn2.lightsetplugin.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.dvmn2.lightsetplugin.model.Effect;
import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.model.LightType;
import net.dvmn2.lightsetplugin.model.Vec3;
import net.dvmn2.lightsetplugin.storage.dto.LightDto;
import net.dvmn2.lightsetplugin.storage.dto.LightsFileDto;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and writes {@code lights.json}, the list of light sources.
 *
 * <p>Format: a {@link LightsFileDto} serialized as JSON with a {@code version} field.
 * Unknown fields are ignored and missing fields get the defaults of {@link LightDto}.
 * {@link #load()} runs on the main thread in {@code onEnable}; {@link #write(long, List)} is thread safe.
 */
public final class LightStore {

    /** Version of the {@code lights.json} format written by this class. */
    public static final int FORMAT_VERSION = 1;

    private static final String FILE_NAME = "lights.json";
    private static final int MAX_BRIGHTNESS = 15;
    private static final float MIN_ANGLE_DEGREES = 1f;
    private static final float MAX_ANGLE_DEGREES = 179f;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Logger log;
    private final LatestWriter writer;

    /**
     * Creates a store.
     *
     * @param dataFolder plugin data folder
     * @param log        plugin logger
     */
    public LightStore(Path dataFolder, Logger log) {
        this.file = dataFolder.resolve(FILE_NAME);
        this.log = log;
        this.writer = new LatestWriter(file, log);
    }

    /**
     * Loads all valid sources. Invalid entries are skipped with a warning; an unreadable file is
     * renamed to {@code lights.json.corrupt} so that it is not overwritten.
     *
     * @return loaded sources, never {@code null}
     */
    public List<LightSource> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        LightsFileDto dto;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            dto = GSON.fromJson(reader, LightsFileDto.class);
        } catch (IOException | JsonParseException ex) {
            log.log(Level.SEVERE, "Failed to read " + FILE_NAME + "; it will be kept as " + FILE_NAME + ".corrupt", ex);
            quarantine();
            return List.of();
        }
        if (dto == null || dto.lights == null) {
            return List.of();
        }
        if (dto.version > FORMAT_VERSION) {
            log.warning(FILE_NAME + " has version " + dto.version + ", newer than supported " + FORMAT_VERSION
                    + "; unknown data will be lost on the next save");
        }
        List<LightSource> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (LightDto entry : dto.lights) {
            LightSource source = toSource(entry);
            if (source == null) {
                continue;
            }
            if (!keys.add(source.getKey())) {
                log.warning(FILE_NAME + ": duplicate light name '" + source.getName() + "', entry skipped");
                continue;
            }
            result.add(source);
        }
        return result;
    }

    /**
     * Converts sources into an immutable-by-convention snapshot that can be written off the main thread.
     * Call on the main thread.
     */
    public static List<LightDto> snapshot(Collection<LightSource> sources) {
        List<LightDto> list = new ArrayList<>(sources.size());
        for (LightSource source : sources) {
            list.add(toDto(source));
        }
        return list;
    }

    /** Takes the ticket for the next snapshot. Call on the main thread before {@link #write(long, List)}. */
    public long nextTicket() {
        return writer.nextTicket();
    }

    /**
     * Writes a snapshot atomically. Safe to call from any thread.
     *
     * @param ticket   ticket from {@link #nextTicket()}; an older snapshot never replaces a newer one
     * @param snapshot snapshot made by {@link #snapshot(Collection)}
     */
    public void write(long ticket, List<LightDto> snapshot) {
        LightsFileDto dto = new LightsFileDto();
        dto.version = FORMAT_VERSION;
        dto.lights = snapshot;
        writer.write(ticket, GSON.toJson(dto).getBytes(StandardCharsets.UTF_8));
    }

    private @Nullable LightSource toSource(@Nullable LightDto entry) {
        if (entry == null) {
            log.warning(FILE_NAME + ": empty entry skipped");
            return null;
        }
        String name = entry.name;
        if (name == null || !LightSource.isValidName(name)) {
            log.warning(FILE_NAME + ": entry with invalid name '" + name + "' skipped");
            return null;
        }
        if (entry.world == null || entry.world.isBlank()) {
            log.warning(FILE_NAME + ": light '" + name + "' has no world, skipped");
            return null;
        }
        if (!isFinite(entry.x, entry.y, entry.z) || !Float.isFinite(entry.yaw) || !Float.isFinite(entry.pitch)
                || !Float.isFinite(entry.angle)) {
            log.warning(FILE_NAME + ": light '" + name + "' has invalid numbers, skipped");
            return null;
        }
        LightType type = LightType.fromKey(entry.type);
        if (type == null) {
            log.warning(FILE_NAME + ": light '" + name + "' has unknown type '" + entry.type + "'; using spot");
            type = LightType.SPOT;
        }
        LightSource source = new LightSource(name, type, entry.world, new Vec3(entry.x, entry.y, entry.z));
        source.setRotation(entry.yaw, entry.pitch);
        source.setBrightness(Math.max(0, Math.min(MAX_BRIGHTNESS, entry.brightness)));
        source.setAngleDegrees(Math.max(MIN_ANGLE_DEGREES, Math.min(MAX_ANGLE_DEGREES, entry.angle)));
        source.setRangeBlocks(Math.max(1, entry.range));
        source.setRays(Math.max(1, entry.rays));
        source.setEnabled(entry.enabled);
        source.setEffect(readEffect(name, entry.effect));
        source.setGroup(readGroup(name, entry.group));
        source.setBeamEnabled(entry.beam);
        if (entry.x2 != null && entry.y2 != null && entry.z2 != null && isFinite(entry.x2, entry.y2, entry.z2)) {
            source.setSecondPoint(new Vec3(entry.x2, entry.y2, entry.z2));
        }
        return source;
    }

    private Effect readEffect(String name, @Nullable String key) {
        Effect effect = Effect.fromKey(key);
        if (effect == null) {
            log.warning(FILE_NAME + ": light '" + name + "' has unknown effect '" + key + "'; using none");
            return Effect.NONE;
        }
        return effect;
    }

    private @Nullable String readGroup(String name, @Nullable String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        if (!LightSource.isValidName(group)) {
            log.warning(FILE_NAME + ": light '" + name + "' has invalid group '" + group + "'; group cleared");
            return null;
        }
        return group;
    }

    private static boolean isFinite(double a, double b, double c) {
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c);
    }

    private static LightDto toDto(LightSource source) {
        LightDto dto = new LightDto();
        dto.name = source.getName();
        dto.type = source.getType().getKey();
        dto.world = source.getWorld();
        dto.x = source.getPosition().x();
        dto.y = source.getPosition().y();
        dto.z = source.getPosition().z();
        dto.yaw = source.getYaw();
        dto.pitch = source.getPitch();
        dto.brightness = source.getBrightness();
        dto.angle = source.getAngleDegrees();
        dto.range = source.getRangeBlocks();
        dto.rays = source.getRays();
        dto.enabled = source.isEnabled();
        dto.effect = source.getEffect().getKey();
        dto.group = source.getGroup();
        dto.beam = source.isBeamEnabled();
        Vec3 second = source.getSecondPoint();
        if (second != null) {
            dto.x2 = second.x();
            dto.y2 = second.y();
            dto.z2 = second.z();
        }
        return dto;
    }

    private void quarantine() {
        try {
            Files.move(file, file.resolveSibling(FILE_NAME + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to move the unreadable " + FILE_NAME + " aside", ex);
        }
    }
}
