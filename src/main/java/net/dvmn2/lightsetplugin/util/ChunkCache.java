package net.dvmn2.lightsetplugin.util;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Block access without loading chunks, with a chunk cache that lives for one recalculation.
 *
 * <p>Main thread only. Reading a block of an unloaded chunk returns {@code null}; callers then
 * call {@link #markTruncated()} so the result is known to be incomplete.
 */
public final class ChunkCache {

    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNK_MASK = 15;

    private final World world;
    private final int minY;
    private final int maxY;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private boolean truncated;

    /**
     * Creates a cache for one world.
     *
     * @param world world to read blocks from
     */
    public ChunkCache(World world) {
        this.world = world;
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
    }

    /** Returns whether the result built on this cache is incomplete. */
    public boolean isTruncated() {
        return truncated;
    }

    /** Marks the result built on this cache as incomplete. */
    public void markTruncated() {
        truncated = true;
    }

    /** Returns whether a block Y lies inside the world height range. */
    public boolean isInside(int y) {
        return y >= minY && y < maxY;
    }

    /**
     * Reads the type of a block.
     *
     * @return the material, or {@code null} when the chunk is not loaded
     */
    public @Nullable Material get(int x, int y, int z) {
        int chunkX = x >> CHUNK_SHIFT;
        int chunkZ = z >> CHUNK_SHIFT;
        long key = Positions.chunkKey(chunkX, chunkZ);
        Chunk chunk;
        if (chunks.containsKey(key)) {
            chunk = chunks.get(key);
        } else {
            chunk = Positions.loadedChunk(world, chunkX, chunkZ);
            chunks.put(key, chunk);
        }
        if (chunk == null) {
            return null;
        }
        return chunk.getBlock(x & CHUNK_MASK, y, z & CHUNK_MASK).getType();
    }
}
