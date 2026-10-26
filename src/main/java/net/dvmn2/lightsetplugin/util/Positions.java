package net.dvmn2.lightsetplugin.util;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Stateless helpers for packed block positions and chunk keys.
 *
 * <p>A packed position stores X and Z in 26 bits each and Y in 12 bits:
 * <pre>
 * bits 63..38  X (signed, 26 bits)
 * bits 37..12  Z (signed, 26 bits)
 * bits 11..0   Y (signed, 12 bits)
 * </pre>
 */
public final class Positions {

    private static final int XZ_MASK = 0x3FFFFFF;
    private static final int Y_MASK = 0xFFF;
    private static final int X_SHIFT = 38;
    private static final int Z_SHIFT = 12;
    private static final int CHUNK_SHIFT = 4;

    private Positions() {
    }

    /** Packs block coordinates into one {@code long}. */
    public static long pack(int x, int y, int z) {
        return ((long) (x & XZ_MASK) << X_SHIFT) | ((long) (z & XZ_MASK) << Z_SHIFT) | (long) (y & Y_MASK);
    }

    /** Extracts the block X coordinate from a packed position. */
    public static int unpackX(long packed) {
        return (int) (packed >> X_SHIFT);
    }

    /** Extracts the block Y coordinate from a packed position. */
    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    /** Extracts the block Z coordinate from a packed position. */
    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    /** Builds a unique key of a chunk from its coordinates. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    /** Returns the key of the chunk that contains a packed position. */
    public static long chunkKeyOf(long packed) {
        return chunkKey(unpackX(packed) >> CHUNK_SHIFT, unpackZ(packed) >> CHUNK_SHIFT);
    }

    /** Floors a coordinate to a block coordinate. */
    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * Returns a loaded chunk, never loading or generating one.
     *
     * @param world  world to look in
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return the chunk, or {@code null} when it is not loaded
     */
    public static @Nullable Chunk loadedChunk(World world, int chunkX, int chunkZ) {
        return world.isChunkLoaded(chunkX, chunkZ) ? world.getChunkAt(chunkX, chunkZ) : null;
    }
}
