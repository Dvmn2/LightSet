package net.dvmn2.lightsetplugin;

import net.dvmn2.lightsetplugin.model.Effect;
import net.dvmn2.lightsetplugin.model.Geometry;
import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.util.Positions;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Owns the light blocks placed in the world.
 *
 * <p>Several sources may claim the same block; the highest level wins ({@code contributions}).
 * What is really placed lives in {@code placed}. Differences between the wanted and the placed state
 * are resolved from the {@code pending} queue with a per-tick budget. Blocks placed by a previous run
 * are kept in a registry and "adopted" when their chunk loads; the ones no source wants are removed.
 *
 * <p>Main thread only.
 */
public final class LightBlockManager {

    private static final long ORPHAN_CHECK_DELAY_TICKS = 40L;
    private static final int MAX_LIGHT_LEVEL = 15;

    private record PosKey(String world, long pos) {
    }

    private record ChunkRef(String world, long chunk) {
    }

    private record Due(PosKey key, long tick) {
    }

    private final Supplier<LightSetSettings> settings;
    private final Runnable onPlacedChanged;

    /** World to position to (source key to level). */
    private final Map<String, Map<Long, Map<String, Integer>>> contributions = new HashMap<>();
    /** World to position to level of the light block that is really placed. */
    private final Map<String, Map<Long, Integer>> placed = new HashMap<>();
    private final LinkedHashSet<PosKey> pending = new LinkedHashSet<>();
    /** Positions waiting for their chunk to load: world to chunk key to positions. */
    private final Map<String, Map<Long, Set<Long>>> deferred = new HashMap<>();
    /** Blocks of the previous run not yet adopted: world to chunk key to positions. */
    private final Map<String, Map<Long, List<Long>>> registry = new HashMap<>();
    private final LinkedHashSet<ChunkRef> adoptQueue = new LinkedHashSet<>();
    private final ArrayDeque<Due> orphanChecks = new ArrayDeque<>();
    private final BlockData[] lightData = new BlockData[MAX_LIGHT_LEVEL + 1];

    private int placedCount;

    /**
     * Creates the manager.
     *
     * @param settings        supplier of the current settings snapshot
     * @param onPlacedChanged called whenever the set of placed blocks changes (to schedule a save)
     */
    public LightBlockManager(Supplier<LightSetSettings> settings, Runnable onPlacedChanged) {
        this.settings = settings;
        this.onPlacedChanged = onPlacedChanged;
    }

    // ---------- Lifecycle ----------

    /**
     * Loads the registry of blocks placed by the previous run.
     *
     * @param data world name to packed positions
     */
    public void loadRegistry(Map<String, long[]> data) {
        for (Map.Entry<String, long[]> entry : data.entrySet()) {
            Map<Long, List<Long>> byChunk = registry.computeIfAbsent(entry.getKey(), world -> new HashMap<>());
            for (long pos : entry.getValue()) {
                byChunk.computeIfAbsent(Positions.chunkKeyOf(pos), chunk -> new ArrayList<>()).add(pos);
            }
        }
    }

    /**
     * Reacts to a chunk load: queues deferred positions and registry blocks of that chunk.
     *
     * @param world  world name
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     */
    public void onChunkLoad(String world, int chunkX, int chunkZ) {
        long chunkKey = Positions.chunkKey(chunkX, chunkZ);
        Map<Long, Set<Long>> deferredInWorld = deferred.get(world);
        if (deferredInWorld != null) {
            Set<Long> positions = deferredInWorld.remove(chunkKey);
            if (positions != null) {
                for (long pos : positions) {
                    pending.add(new PosKey(world, pos));
                }
            }
        }
        Map<Long, List<Long>> registryInWorld = registry.get(world);
        if (registryInWorld != null && registryInWorld.containsKey(chunkKey)) {
            adoptQueue.add(new ChunkRef(world, chunkKey));
        }
    }

    /** Returns the number of light blocks currently placed by the plugin. */
    public int getPlacedCount() {
        return placedCount;
    }

    /**
     * Makes a snapshot of every known light block position, including the ones not yet adopted.
     * Call on the main thread; the returned arrays are owned by the caller.
     *
     * @return world name to packed positions
     */
    public Map<String, long[]> snapshotPlaced() {
        Set<String> worlds = new HashSet<>(placed.keySet());
        worlds.addAll(registry.keySet());
        Map<String, long[]> result = new HashMap<>();
        for (String world : worlds) {
            Map<Long, Integer> placedInWorld = placed.get(world);
            Map<Long, List<Long>> registryInWorld = registry.get(world);
            List<Long> all = new ArrayList<>();
            if (placedInWorld != null) {
                all.addAll(placedInWorld.keySet());
            }
            if (registryInWorld != null) {
                for (List<Long> positions : registryInWorld.values()) {
                    all.addAll(positions);
                }
            }
            if (all.isEmpty()) {
                continue;
            }
            long[] array = new long[all.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = all.get(i);
            }
            result.put(world, array);
        }
        return result;
    }

    // ---------- Light levels ----------

    /**
     * Recomputes the light levels a source contributes from its cached geometry, brightness and effect.
     *
     * @param source source to synchronise
     */
    public void sync(LightSource source) {
        Map<Long, Integer> next = new HashMap<>();
        if (source.isEnabled() && source.getBrightness() > 0) {
            Geometry geometry = source.getGeometry();
            double effective = source.getBrightness()
                    * (source.getEffect() == Effect.NONE ? 1.0 : source.getEffectFactor());
            int cap = settings.get().limits().maxTotalBlocks();
            int added = 0;
            for (int i = 0; i < geometry.positions().length; i++) {
                int level = Math.min(MAX_LIGHT_LEVEL, (int) Math.round(effective * geometry.factors()[i]));
                if (level < 1) {
                    continue;
                }
                long pos = geometry.positions()[i];
                if (!source.getApplied().containsKey(pos)) {
                    if (placedCount + added >= cap) {
                        continue;
                    }
                    added++;
                }
                next.put(pos, level);
            }
        }
        diff(source, next);
    }

    /**
     * Withdraws everything a source contributes (used when the source is removed).
     *
     * @param source source to release
     */
    public void release(LightSource source) {
        diff(source, new HashMap<>());
    }

    // ---------- Per-tick work ----------

    /**
     * Adopts registry blocks of loaded chunks and queues due orphan checks.
     *
     * @param tick current tick counter of the plugin
     */
    public void processDelayed(long tick) {
        if (!adoptQueue.isEmpty()) {
            List<ChunkRef> refs = new ArrayList<>(adoptQueue);
            adoptQueue.clear();
            for (ChunkRef ref : refs) {
                adopt(ref, tick);
            }
        }
        while (!orphanChecks.isEmpty() && orphanChecks.peekFirst().tick() <= tick) {
            pending.add(orphanChecks.pollFirst().key());
        }
    }

    /** Places or removes light blocks from the pending queue, within the per-tick budget. */
    public void processPending() {
        int budget = settings.get().performance().blockUpdatesPerTick();
        Iterator<PosKey> it = pending.iterator();
        while (it.hasNext() && budget > 0) {
            PosKey key = it.next();
            it.remove();
            reconcile(key);
            budget--;
        }
    }

    // ---------- Internals ----------

    /** Adopts light blocks of the previous run that are found in a loaded chunk. */
    private void adopt(ChunkRef ref, long tick) {
        Map<Long, List<Long>> registryInWorld = registry.get(ref.world());
        if (registryInWorld == null) {
            return;
        }
        List<Long> list = registryInWorld.remove(ref.chunk());
        if (list == null) {
            return;
        }
        World world = Bukkit.getWorld(ref.world());
        if (world == null) {
            registryInWorld.put(ref.chunk(), list);
            return;
        }
        Map<Long, Integer> placedInWorld = placed.computeIfAbsent(ref.world(), name -> new HashMap<>());
        for (long pos : list) {
            int x = Positions.unpackX(pos);
            int z = Positions.unpackZ(pos);
            Chunk chunk = Positions.loadedChunk(world, x >> 4, z >> 4);
            if (chunk == null) {
                registryInWorld.computeIfAbsent(ref.chunk(), key -> new ArrayList<>()).add(pos);
                continue;
            }
            Block block = chunk.getBlock(x & 15, Positions.unpackY(pos), z & 15);
            if (block.getType() == Material.LIGHT && block.getBlockData() instanceof Light light) {
                if (placedInWorld.put(pos, light.getLevel()) == null) {
                    placedCount++;
                }
                // Sources need a moment to recalculate; check after that whether the block is still wanted.
                orphanChecks.add(new Due(new PosKey(ref.world(), pos), tick + ORPHAN_CHECK_DELAY_TICKS));
            }
        }
        onPlacedChanged.run();
    }

    /** Moves the contribution of a source to a new state and queues the positions that changed. */
    private void diff(LightSource source, Map<Long, Integer> next) {
        String sourceKey = source.getKey();
        Map<Long, Map<String, Integer>> contributionsInWorld =
                contributions.computeIfAbsent(source.getWorld(), world -> new HashMap<>());
        Map<Long, Integer> placedInWorld = placed.computeIfAbsent(source.getWorld(), world -> new HashMap<>());

        for (long pos : source.getApplied().keySet()) {
            if (next.containsKey(pos)) {
                continue;
            }
            Map<String, Integer> bySource = contributionsInWorld.get(pos);
            if (bySource != null) {
                bySource.remove(sourceKey);
                if (bySource.isEmpty()) {
                    contributionsInWorld.remove(pos);
                }
            }
            pending.add(new PosKey(source.getWorld(), pos));
        }
        for (Map.Entry<Long, Integer> entry : next.entrySet()) {
            long pos = entry.getKey();
            int level = entry.getValue();
            Integer old = source.getApplied().get(pos);
            if (old == null || old != level) {
                contributionsInWorld.computeIfAbsent(pos, key -> new HashMap<>(2)).put(sourceKey, level);
                pending.add(new PosKey(source.getWorld(), pos));
            } else {
                Integer current = placedInWorld.get(pos);
                if (current == null || current < level) {
                    pending.add(new PosKey(source.getWorld(), pos));
                }
            }
        }
        source.setApplied(next);
    }

    private int desiredLevel(String world, long pos) {
        Map<Long, Map<String, Integer>> contributionsInWorld = contributions.get(world);
        if (contributionsInWorld == null) {
            return 0;
        }
        Map<String, Integer> bySource = contributionsInWorld.get(pos);
        if (bySource == null) {
            return 0;
        }
        int max = 0;
        for (int level : bySource.values()) {
            max = Math.max(max, level);
        }
        return max;
    }

    /** Brings one world block to the wanted light level. */
    private void reconcile(PosKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null) {
            return;
        }
        long pos = key.pos();
        int x = Positions.unpackX(pos);
        int y = Positions.unpackY(pos);
        int z = Positions.unpackZ(pos);
        int desired = desiredLevel(key.world(), pos);
        Map<Long, Integer> placedInWorld = placed.computeIfAbsent(key.world(), name -> new HashMap<>());
        Integer currentObj = placedInWorld.get(pos);
        int current = currentObj == null ? 0 : currentObj;
        if (desired == current) {
            return;
        }

        Chunk chunk = Positions.loadedChunk(world, x >> 4, z >> 4);
        if (chunk == null) {
            deferred.computeIfAbsent(key.world(), name -> new HashMap<>())
                    .computeIfAbsent(Positions.chunkKey(x >> 4, z >> 4), chunkKey -> new HashSet<>())
                    .add(pos);
            return;
        }

        Block block = chunk.getBlock(x & 15, y, z & 15);
        Material type = block.getType();
        if (desired > 0) {
            if (type == Material.LIGHT) {
                if (!(block.getBlockData() instanceof Light light && light.getLevel() == desired)) {
                    block.setBlockData(lightData(desired), false);
                }
            } else if (type.isAir()) {
                block.setBlockData(lightData(desired), false);
            } else {
                // Another block took the position: there will be no light here.
                if (currentObj != null) {
                    placedInWorld.remove(pos);
                    placedCount--;
                    onPlacedChanged.run();
                }
                return;
            }
            if (currentObj == null) {
                placedCount++;
            }
            placedInWorld.put(pos, desired);
        } else {
            if (type == Material.LIGHT) {
                block.setType(Material.AIR, false);
            }
            placedInWorld.remove(pos);
            placedCount--;
        }
        onPlacedChanged.run();
    }

    private BlockData lightData(int level) {
        BlockData data = lightData[level];
        if (data == null) {
            data = Bukkit.createBlockData(Material.LIGHT, blockData -> ((Light) blockData).setLevel(level));
            lightData[level] = data;
        }
        return data;
    }
}
