package net.dvmn2.lightsetplugin;

import net.dvmn2.lightsetplugin.model.CreateResult;
import net.dvmn2.lightsetplugin.model.Effect;
import net.dvmn2.lightsetplugin.model.GroupResult;
import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.model.LightType;
import net.dvmn2.lightsetplugin.model.MoveResult;
import net.dvmn2.lightsetplugin.model.Vec3;
import net.dvmn2.lightsetplugin.storage.LightStore;
import net.dvmn2.lightsetplugin.storage.dto.LightDto;
import net.dvmn2.lightsetplugin.util.Angles;
import net.dvmn2.lightsetplugin.util.Positions;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Owns the light sources and drives their update loop.
 *
 * <p>Every source caches its geometry (positions and brightness factors). Changing brightness, effect or
 * the on/off state does not need new rays: only the light levels are recomputed. Heavy work is done in
 * small portions per tick (see {@code performance.*} in the config).
 *
 * <p>Main thread only.
 */
public final class LightManager {

    private static final int CHUNK_SIZE_BLOCKS = 16;
    private static final int CHUNK_SHIFT = 4;
    private static final int MAX_BRIGHTNESS = 15;
    private static final int DELAYED_PERIOD_TICKS = 5;
    private static final double EPSILON = 1.0E-6;
    private static final double EFFECT_KEY_SCALE = 2.0;
    private static final double CENTER_OFFSET = 0.5;

    private final Plugin plugin;
    private final Supplier<LightSetSettings> settings;
    private final LightGeometry geometry;
    private final LightBlockManager blocks;
    private final ModelManager models;
    private final EffectCalculator effects;
    private final BeamRenderer beams;
    private final PersistenceManager persistence;
    private final Map<String, LightSource> sources = new LinkedHashMap<>();
    private final LinkedHashSet<String> dirtyQueue = new LinkedHashSet<>();
    private final LinkedHashSet<String> delayedDirty = new LinkedHashSet<>();

    private long tick;
    private BukkitTask mainTask;

    /**
     * Creates the manager.
     *
     * @param plugin      owner of the scheduled task
     * @param settings    supplier of the current settings snapshot
     * @param geometry    geometry calculator
     * @param blocks      owner of the light blocks in the world
     * @param models      owner of the visible models
     * @param effects     effect calculator
     * @param beams       particle beam renderer
     * @param persistence decides when data is written to disk
     */
    public LightManager(Plugin plugin, Supplier<LightSetSettings> settings, LightGeometry geometry,
                        LightBlockManager blocks, ModelManager models, EffectCalculator effects,
                        BeamRenderer beams, PersistenceManager persistence) {
        this.plugin = plugin;
        this.settings = settings;
        this.geometry = geometry;
        this.blocks = blocks;
        this.models = models;
        this.effects = effects;
        this.beams = beams;
        this.persistence = persistence;
    }

    // ---------- Lifecycle ----------

    /**
     * Registers loaded sources and starts the update loop.
     *
     * @param loaded sources read from disk
     */
    public void start(List<LightSource> loaded) {
        for (LightSource source : loaded) {
            sources.put(source.getKey(), source);
            dirtyQueue.add(source.getKey());
        }
        for (World world : Bukkit.getWorlds()) {
            models.removeOrphans(world);
            for (Chunk chunk : world.getLoadedChunks()) {
                onChunkLoad(world.getName(), chunk.getX(), chunk.getZ());
            }
        }
        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 1L, 1L);
    }

    /** Stops the update loop and removes the models. Light blocks stay in the world. */
    public void shutdown() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
        models.removeAll();
    }

    /** Rebuilds models and geometry of every source after the settings changed. */
    public void onSettingsReloaded() {
        for (LightSource source : sources.values()) {
            models.remove(source);
            models.ensure(source);
            source.setGeometryDirty(true);
            dirtyQueue.add(source.getKey());
        }
    }

    /** Makes a snapshot of all sources for writing to disk. */
    public List<LightDto> snapshotLights() {
        return LightStore.snapshot(sources.values());
    }

    // ---------- Queries ----------

    /** Returns a read-only view of all sources, in creation order. */
    public Collection<LightSource> getAll() {
        return Collections.unmodifiableCollection(sources.values());
    }

    /** Finds a source by name, case-insensitively. */
    public @Nullable LightSource get(String name) {
        return sources.get(LightSource.keyOf(name));
    }

    /** Returns the names of all sources. */
    public List<String> getNames() {
        List<String> names = new ArrayList<>(sources.size());
        for (LightSource source : sources.values()) {
            names.add(source.getName());
        }
        return names;
    }

    /** Returns the names of all groups, sorted. */
    public Set<String> getGroupNames() {
        Set<String> groups = new TreeSet<>();
        for (LightSource source : sources.values()) {
            if (source.getGroup() != null) {
                groups.add(source.getGroup());
            }
        }
        return groups;
    }

    /** Returns the members of a group, matched case-insensitively. */
    public List<LightSource> getGroupMembers(String group) {
        List<LightSource> members = new ArrayList<>();
        for (LightSource source : sources.values()) {
            if (source.getGroup() != null && source.getGroup().equalsIgnoreCase(group)) {
                members.add(source);
            }
        }
        return members;
    }

    // ---------- Commands ----------

    /**
     * Creates a source at a location, facing the same way.
     *
     * @param name source name
     * @param type source type
     * @param loc  location; its block centre is used as the position
     * @return the outcome
     */
    public CreateResult create(String name, LightType type, Location loc) {
        if (!LightSource.isValidName(name)) {
            return CreateResult.INVALID_NAME;
        }
        if (sources.containsKey(LightSource.keyOf(name))) {
            return CreateResult.NAME_EXISTS;
        }
        LightSetSettings cfg = settings.get();
        LightSource source = new LightSource(name, type, loc.getWorld().getName(), centerOf(loc));
        source.setRotation(Angles.normalizeYaw(loc.getYaw()), Angles.clampPitch(loc.getPitch()));
        source.setBrightness(cfg.defaults().brightness());
        source.setAngleDegrees(cfg.defaults().angleDegrees());
        source.setRangeBlocks(cfg.defaults().rangeBlocks());
        source.setRays(cfg.defaults().rays());
        source.setBeamEnabled(cfg.beam().enabledByDefault());
        sources.put(source.getKey(), source);
        models.ensure(source);
        changed(source, true);
        return CreateResult.SUCCESS;
    }

    /** Removes a source together with its light blocks and model. */
    public void remove(LightSource source) {
        blocks.release(source);
        models.remove(source);
        sources.remove(source.getKey());
        dirtyQueue.remove(source.getKey());
        delayedDirty.remove(source.getKey());
        persistence.markLightsDirty();
    }

    /** Switches a source on or off. */
    public void setEnabled(LightSource source, boolean enabled) {
        source.setEnabled(enabled);
        changed(source, false);
    }

    /** Sets the brightness of a source, limited to {@code 0..15}. */
    public void setBrightness(LightSource source, int brightness) {
        source.setBrightness(Math.max(0, Math.min(MAX_BRIGHTNESS, brightness)));
        source.setLastEffectKey(-1);
        changed(source, false);
    }

    /** Rotates a source; angles are normalised. */
    public void setDirection(LightSource source, float yaw, float pitch) {
        source.setRotation(Angles.normalizeYaw(yaw), Angles.clampPitch(pitch));
        models.ensure(source);
        changed(source, true);
    }

    /** Turns a source towards a point. A point at the source itself is ignored. */
    public void aimAt(LightSource source, double x, double y, double z) {
        Vec3 pos = source.getPosition();
        double dx = x - pos.x();
        double dy = y - pos.y();
        double dz = z - pos.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < EPSILON && Math.abs(dy) < EPSILON) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        setDirection(source, yaw, pitch);
    }

    /** Moves a source inside its world. */
    public void move(LightSource source, double x, double y, double z) {
        source.setPosition(new Vec3(x, y, z));
        models.ensure(source);
        changed(source, true);
    }

    /**
     * Moves a source to the block centre of a location.
     *
     * @return {@link MoveResult#OTHER_WORLD} when the location is in another world
     */
    public MoveResult moveTo(LightSource source, Location loc) {
        if (!loc.getWorld().getName().equals(source.getWorld())) {
            return MoveResult.OTHER_WORLD;
        }
        Vec3 center = centerOf(loc);
        move(source, center.x(), center.y(), center.z());
        return MoveResult.SUCCESS;
    }

    /** Sets the second point of a strip or area. */
    public void setSecondPoint(LightSource source, double x, double y, double z) {
        source.setSecondPoint(new Vec3(x, y, z));
        changed(source, true);
    }

    /** Sets the second point of a strip or area to the block centre of a location. */
    public void setSecondPointTo(LightSource source, Location loc) {
        Vec3 center = centerOf(loc);
        setSecondPoint(source, center.x(), center.y(), center.z());
    }

    /** Sets the cone angle of a spotlight, limited by {@code limits.max-angle-degrees}. */
    public void setAngle(LightSource source, float angleDegrees) {
        source.setAngleDegrees(Math.max(1f, Math.min(settings.get().limits().maxAngleDegrees(), angleDegrees)));
        changed(source, true);
    }

    /** Sets the range of a spotlight, limited by {@code limits.max-range-blocks}. */
    public void setRange(LightSource source, int rangeBlocks) {
        source.setRangeBlocks(Math.max(1, Math.min(settings.get().limits().maxRangeBlocks(), rangeBlocks)));
        changed(source, true);
    }

    /** Sets the ray count of a spotlight, limited by {@code limits.max-rays}. */
    public void setRays(LightSource source, int rays) {
        source.setRays(Math.max(1, Math.min(settings.get().limits().maxRays(), rays)));
        changed(source, true);
    }

    /** Shows or hides the particle beam of a spotlight. */
    public void setBeam(LightSource source, boolean enabled) {
        source.setBeamEnabled(enabled);
        persistence.markLightsDirty();
    }

    /** Sets the brightness effect of a source. */
    public void setEffect(LightSource source, Effect effect) {
        source.setEffect(effect);
        source.setEffectFactor(1.0);
        source.setLastEffectKey(-1);
        changed(source, false);
    }

    /**
     * Puts a source into a group or takes it out.
     *
     * @param group group name, or {@code null} to leave the group
     * @return {@link GroupResult#INVALID_NAME} when the name has an invalid format
     */
    public GroupResult setGroup(LightSource source, @Nullable String group) {
        if (group != null && !LightSource.isValidName(group)) {
            return GroupResult.INVALID_NAME;
        }
        source.setGroup(group);
        persistence.markLightsDirty();
        return GroupResult.SUCCESS;
    }

    // ---------- Chunk events ----------

    /**
     * Reacts to a chunk load: restores light blocks, models and geometry that depend on the chunk.
     *
     * @param world  world name
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     */
    public void onChunkLoad(String world, int chunkX, int chunkZ) {
        blocks.onChunkLoad(world, chunkX, chunkZ);
        for (LightSource source : sources.values()) {
            if (!source.getWorld().equals(world)) {
                continue;
            }
            int sourceChunkX = Positions.floor(source.getPosition().x()) >> CHUNK_SHIFT;
            int sourceChunkZ = Positions.floor(source.getPosition().z()) >> CHUNK_SHIFT;
            boolean own = sourceChunkX == chunkX && sourceChunkZ == chunkZ;
            boolean near = source.getGeometry().truncated()
                    && Math.max(Math.abs(chunkX - sourceChunkX), Math.abs(chunkZ - sourceChunkZ)) * CHUNK_SIZE_BLOCKS
                    <= reachOf(source) + CHUNK_SIZE_BLOCKS;
            if (own || near) {
                delayedDirty.add(source.getKey());
            }
        }
    }

    // ---------- Update loop ----------

    private void onTick() {
        tick++;
        LightSetSettings cfg = settings.get();
        if (tick % DELAYED_PERIOD_TICKS == 0) {
            processDelayed();
        }
        if (tick % cfg.effects().intervalTicks() == 0) {
            tickEffects();
        }
        if (tick % cfg.beam().intervalTicks() == 0) {
            beams.render(sources.values());
        }
        processSources(cfg.performance().sourceUpdatesPerTick());
        blocks.processPending();
    }

    private void processDelayed() {
        blocks.processDelayed(tick);
        if (delayedDirty.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(delayedDirty);
        delayedDirty.clear();
        for (String key : keys) {
            LightSource source = sources.get(key);
            if (source == null) {
                continue;
            }
            models.ensure(source);
            source.setGeometryDirty(true);
            dirtyQueue.add(key);
        }
    }

    private void processSources(int budget) {
        int left = budget;
        Iterator<String> it = dirtyQueue.iterator();
        while (it.hasNext() && left > 0) {
            String key = it.next();
            it.remove();
            LightSource source = sources.get(key);
            if (source != null && processSource(source)) {
                left--;
            }
        }
    }

    /** Returns {@code true} when heavy work (ray tracing) was done. */
    private boolean processSource(LightSource source) {
        World world = Bukkit.getWorld(source.getWorld());
        if (world == null) {
            return false;
        }
        Vec3 pos = source.getPosition();
        if (Positions.loadedChunk(world, Positions.floor(pos.x()) >> CHUNK_SHIFT,
                Positions.floor(pos.z()) >> CHUNK_SHIFT) == null) {
            return false;
        }
        boolean heavy = false;
        if (source.isGeometryDirty()) {
            source.setGeometry(geometry.compute(source, world));
            source.setGeometryDirty(false);
            heavy = true;
        }
        blocks.sync(source);
        return heavy;
    }

    private void tickEffects() {
        for (LightSource source : sources.values()) {
            if (source.getEffect() == Effect.NONE || !source.isEnabled()) {
                continue;
            }
            source.setEffectFactor(effects.compute(source, tick));
            int effectKey = (int) Math.round(source.getBrightness() * source.getEffectFactor() * EFFECT_KEY_SCALE);
            if (effectKey != source.getLastEffectKey()) {
                source.setLastEffectKey(effectKey);
                dirtyQueue.add(source.getKey());
            }
        }
    }

    // ---------- Helpers ----------

    private void changed(LightSource source, boolean geometryChanged) {
        if (geometryChanged) {
            source.setGeometryDirty(true);
        }
        dirtyQueue.add(source.getKey());
        persistence.markLightsDirty();
    }

    private int reachOf(LightSource source) {
        if (source.getType() == LightType.SPOT) {
            return Math.min(source.getRangeBlocks(), settings.get().limits().maxRangeBlocks());
        }
        Vec3 second = source.getSecondPoint();
        if (second == null) {
            return CHUNK_SIZE_BLOCKS;
        }
        Vec3 pos = source.getPosition();
        return (int) Math.max(Math.abs(second.x() - pos.x()), Math.abs(second.z() - pos.z())) + CHUNK_SIZE_BLOCKS;
    }

    private static Vec3 centerOf(Location loc) {
        return new Vec3(
                Math.floor(loc.getX()) + CENTER_OFFSET,
                Math.floor(loc.getY()) + CENTER_OFFSET,
                Math.floor(loc.getZ()) + CENTER_OFFSET);
    }
}
