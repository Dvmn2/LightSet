package net.dvmn2.lightsetplugin;

import net.dvmn2.lightsetplugin.model.Geometry;
import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.model.Vec3;
import net.dvmn2.lightsetplugin.util.ChunkCache;
import net.dvmn2.lightsetplugin.util.Positions;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calculates where the light blocks of a source go and how strong each one is.
 *
 * <p>For a spotlight these are the air cells in front of the surfaces hit by the rays of the cone;
 * for other types they are cells along a line, inside a box or at one point. Chunks are never
 * loaded: a ray that enters an unloaded chunk stops and the result is marked as truncated.
 * Main thread only.
 */
public final class LightGeometry {

    private static final double GOLDEN_ANGLE_RADIANS = 2.399963229728653;
    private static final double VERTICAL_THRESHOLD = 0.99;
    private static final float FULL_FACTOR = 1f;
    private static final byte SOLID_UNKNOWN = 0;
    private static final byte SOLID_YES = 1;
    private static final byte SOLID_NO = 2;

    private final Supplier<LightSetSettings> settings;
    private final byte[] solidCache = new byte[Material.values().length];

    /**
     * Creates the calculator.
     *
     * @param settings supplier of the current settings snapshot
     */
    public LightGeometry(Supplier<LightSetSettings> settings) {
        this.settings = settings;
    }

    /**
     * Calculates the geometry of a source.
     *
     * @param source source to calculate
     * @param world  world the source is in
     * @return the geometry; never {@code null}
     */
    public Geometry compute(LightSource source, World world) {
        ChunkCache cache = new ChunkCache(world);
        Map<Long, Float> hits = new LinkedHashMap<>();
        Vec3 pos = source.getPosition();
        double beamLength = switch (source.getType()) {
            case SPOT -> traceSpot(source, cache, hits);
            case LAMP -> {
                addCell(cache, hits, Positions.floor(pos.x()), Positions.floor(pos.y()), Positions.floor(pos.z()));
                yield 0.0;
            }
            case STRIP -> {
                fillStrip(source, cache, hits);
                yield 0.0;
            }
            case AREA -> {
                fillArea(source, cache, hits);
                yield 0.0;
            }
        };

        long[] positions = new long[hits.size()];
        float[] factors = new float[hits.size()];
        int index = 0;
        for (Map.Entry<Long, Float> entry : hits.entrySet()) {
            positions[index] = entry.getKey();
            factors[index] = entry.getValue();
            index++;
        }
        return new Geometry(positions, factors, beamLength, cache.isTruncated());
    }

    // ---------- Spotlight ----------

    private double traceSpot(LightSource source, ChunkCache cache, Map<Long, Float> hits) {
        LightSetSettings.Limits limits = settings.get().limits();
        int rays = Math.max(1, Math.min(source.getRays(), limits.maxRays()));
        double range = Math.max(1, Math.min(source.getRangeBlocks(), limits.maxRangeBlocks()));
        double angle = Math.min(source.getAngleDegrees(), limits.maxAngleDegrees());
        double tanHalf = Math.tan(Math.toRadians(angle / 2.0));
        Vec3 dir = source.getDirection();
        Vec3 u = perpendicular(dir);
        Vec3 v = cross(dir, u);

        double beamLength = range;
        int[] prev = new int[3];
        for (int i = 0; i < rays; i++) {
            double radial = Math.sqrt((double) i / rays);
            double theta = i * GOLDEN_ANGLE_RADIANS;
            double rx = Math.cos(theta) * radial * tanHalf;
            double ry = Math.sin(theta) * radial * tanHalf;
            Vec3 ray = normalize(
                    dir.x() + u.x() * rx + v.x() * ry,
                    dir.y() + u.y() * rx + v.y() * ry,
                    dir.z() + u.z() * rx + v.z() * ry);

            double distance = trace(cache, source.getPosition(), ray, range, prev);
            if (i == 0 && distance >= 0) {
                beamLength = distance;
            }
            if (distance < 0) {
                continue;
            }
            recordHit(cache, hits, prev, distance, range, radial);
            if (hits.size() >= limits.maxBlocksPerSource()) {
                break;
            }
        }
        return beamLength;
    }

    private void recordHit(ChunkCache cache, Map<Long, Float> hits, int[] cell, double distance, double range,
                           double radial) {
        Material material = cache.get(cell[0], cell[1], cell[2]);
        if (material == null || !(material.isAir() || material == Material.LIGHT)) {
            return;
        }
        LightSetSettings.Spot spot = settings.get().spot();
        double distanceFactor = 1.0 - spot.distanceFalloff() * Math.min(1.0, distance / range);
        double edgeFactor = 1.0 - spot.edgeFalloff() * radial * radial;
        float factor = (float) Math.max(0.0, distanceFactor * edgeFactor);
        if (factor <= 0f) {
            return;
        }
        hits.merge(Positions.pack(cell[0], cell[1], cell[2]), factor, Math::max);
    }

    /**
     * Walks a ray through the voxel grid (Amanatides-Woo) up to the first solid block.
     *
     * @param prev output: the last passable cell before the obstacle
     * @return distance to the obstacle, or {@code -1} when the ray hit nothing or left loaded chunks
     */
    private double trace(ChunkCache cache, Vec3 origin, Vec3 dir, double max, int[] prev) {
        double ox = origin.x();
        double oy = origin.y();
        double oz = origin.z();
        double dx = dir.x();
        double dy = dir.y();
        double dz = dir.z();
        int x = Positions.floor(ox);
        int y = Positions.floor(oy);
        int z = Positions.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double nextX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? (x + 1 - ox) : (ox - x)) * deltaX;
        double nextY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? (y + 1 - oy) : (oy - y)) * deltaY;
        double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? (z + 1 - oz) : (oz - z)) * deltaZ;
        int prevX = x;
        int prevY = y;
        int prevZ = z;

        while (true) {
            double t;
            if (nextX < nextY && nextX < nextZ) {
                t = nextX;
                x += stepX;
                nextX += deltaX;
            } else if (nextY < nextZ) {
                t = nextY;
                y += stepY;
                nextY += deltaY;
            } else {
                t = nextZ;
                z += stepZ;
                nextZ += deltaZ;
            }
            if (t > max || !cache.isInside(y)) {
                return -1;
            }
            Material material = cache.get(x, y, z);
            if (material == null) {
                cache.markTruncated();
                return -1;
            }
            if (isSolid(material)) {
                prev[0] = prevX;
                prev[1] = prevY;
                prev[2] = prevZ;
                return t;
            }
            prevX = x;
            prevY = y;
            prevZ = z;
        }
    }

    private boolean isSolid(Material material) {
        int ordinal = material.ordinal();
        byte state = solidCache[ordinal];
        if (state == SOLID_UNKNOWN) {
            state = material.isSolid() ? SOLID_YES : SOLID_NO;
            solidCache[ordinal] = state;
        }
        return state == SOLID_YES;
    }

    // ---------- Strip and area ----------

    private void fillStrip(LightSource source, ChunkCache cache, Map<Long, Float> hits) {
        Vec3 from = source.getPosition();
        Vec3 to = source.getSecondPoint() != null ? source.getSecondPoint() : from;
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int count = (int) Math.ceil(length / settings.get().shapes().stripSpacingBlocks());
        int max = settings.get().limits().maxBlocksPerSource();
        for (int i = 0; i <= count && hits.size() < max; i++) {
            double t = count == 0 ? 0.0 : (double) i / count;
            addCell(cache, hits,
                    Positions.floor(from.x() + dx * t),
                    Positions.floor(from.y() + dy * t),
                    Positions.floor(from.z() + dz * t));
        }
    }

    private void fillArea(LightSource source, ChunkCache cache, Map<Long, Float> hits) {
        Vec3 a = source.getPosition();
        Vec3 b = source.getSecondPoint() != null ? source.getSecondPoint() : a;
        int minX = Positions.floor(Math.min(a.x(), b.x()));
        int maxX = Positions.floor(Math.max(a.x(), b.x()));
        int minY = Positions.floor(Math.min(a.y(), b.y()));
        int maxY = Positions.floor(Math.max(a.y(), b.y()));
        int minZ = Positions.floor(Math.min(a.z(), b.z()));
        int maxZ = Positions.floor(Math.max(a.z(), b.z()));
        int step = settings.get().shapes().areaSpacingBlocks();
        int max = settings.get().limits().maxBlocksPerSource();
        for (int x = minX; x <= maxX; x += step) {
            for (int y = minY; y <= maxY; y += step) {
                for (int z = minZ; z <= maxZ; z += step) {
                    if (hits.size() >= max) {
                        return;
                    }
                    addCell(cache, hits, x, y, z);
                }
            }
        }
    }

    private void addCell(ChunkCache cache, Map<Long, Float> hits, int x, int y, int z) {
        if (!cache.isInside(y)) {
            return;
        }
        Material material = cache.get(x, y, z);
        if (material == null) {
            cache.markTruncated();
            return;
        }
        if (material.isAir() || material == Material.LIGHT) {
            hits.merge(Positions.pack(x, y, z), FULL_FACTOR, Math::max);
        }
    }

    // ---------- Vector helpers ----------

    /** Returns a unit vector perpendicular to {@code dir}. */
    private static Vec3 perpendicular(Vec3 dir) {
        if (Math.abs(dir.y()) > VERTICAL_THRESHOLD) {
            return new Vec3(1, 0, 0);
        }
        return normalize(dir.z(), 0, -dir.x());
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }

    private static Vec3 normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Vec3(x / length, y / length, z / length);
    }
}
