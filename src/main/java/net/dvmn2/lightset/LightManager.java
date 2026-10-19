package net.dvmn2.lightset;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ядро плагина.
 *
 * <h3>Модель</h3>
 * Каждый источник ({@link LightSource}) умеет посчитать «геометрию» — набор позиций
 * воздушных блоков и коэффициент яркости (0..1) для каждой. Для прожектора это точки на
 * поверхностях, куда попали лучи конуса. Итоговый уровень = яркость × коэффициент × эффект.
 * Геометрия кэшируется, поэтому смена яркости/эффекта/вкл-выкл НЕ требует новых лучей.
 *
 * <h3>Слияние источников</h3>
 * Если несколько источников претендуют на один блок, ставится максимальный уровень
 * ({@code contributions}). Реально поставленное хранится в {@code placed}; расхождения
 * между желаемым и поставленным разбираются очередью {@code pending} с лимитом на тик.
 *
 * <h3>Устойчивость</h3>
 * Позиции поставленных блоков пишутся в placed.yml. После рестарта/краша они «усыновляются»
 * при загрузке чанка, и если источник их больше не требует — убираются.
 */
public final class LightManager {

    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private static final byte[] SOLID_CACHE = new byte[Material.values().length];

    private record PosKey(String world, long pos) {
    }

    private record ChunkRef(String world, long chunk) {
    }

    private record Due(PosKey key, long tick) {
    }

    private final LightSet plugin;
    private final LightSettings settings;
    private final LightStore store;

    private final Map<String, LightSource> sources = new LinkedHashMap<>();

    /**
     * мир → позиция → (источник → уровень).
     */
    private final Map<String, Map<Long, Map<String, Integer>>> contributions = new HashMap<>();
    /**
     * мир → позиция → уровень реально поставленного светового блока.
     */
    private final Map<String, Map<Long, Integer>> placed = new HashMap<>();
    private int placedCount;

    private final LinkedHashSet<PosKey> pending = new LinkedHashSet<>();
    /**
     * Позиции, до которых не дошли руки, потому что чанк не загружен: мир → чанк → позиции.
     */
    private final Map<String, Map<Long, Set<Long>>> deferred = new HashMap<>();

    private final LinkedHashSet<String> dirtyQueue = new LinkedHashSet<>();
    private final LinkedHashSet<String> delayedDirty = new LinkedHashSet<>();

    /**
     * Реестр блоков из прошлого запуска, ещё не усыновлённых: мир → чанк → позиции.
     */
    private final Map<String, Map<Long, List<Long>>> registry = new HashMap<>();
    private final LinkedHashSet<ChunkRef> adoptQueue = new LinkedHashSet<>();
    private final ArrayDeque<Due> orphanChecks = new ArrayDeque<>();

    private final BlockData[] lightData = new BlockData[16];

    private long tick;
    private BukkitTask mainTask;

    public LightManager(LightSet plugin, LightSettings settings, LightStore store) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
    }

    // ------------------------------------------------------------------ жизненный цикл

    public void start() {
        for (LightSource s : store.loadSources()) {
            sources.put(key(s.name), s);
        }
        for (String line : store.loadPlaced()) {
            int idx = line.indexOf(';');
            if (idx <= 0) {
                continue;
            }
            try {
                long pos = Long.parseLong(line.substring(0, idx));
                String world = line.substring(idx + 1);
                registry.computeIfAbsent(world, w -> new HashMap<>())
                        .computeIfAbsent(chunkKeyOfPos(pos), c -> new ArrayList<>())
                        .add(pos);
            } catch (NumberFormatException ignored) {
                // битая строка реестра — пропускаем
            }
        }
        store.bind(this::serializeLights, this::snapshotPlaced);

        for (LightSource s : sources.values()) {
            dirtyQueue.add(key(s.name));
        }
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                onChunkLoad(w.getName(), c.getX(), c.getZ());
            }
        }
        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
        for (LightSource s : sources.values()) {
            removeDisplay(s);
        }
        store.flushSync();
    }

    /**
     * Перечитывает настройки и пересчитывает всё (lights.yml не перечитывается).
     */
    public void reload() {
        settings.reload();
        Lang.setLanguage(plugin.getConfig().getString("language", "auto"));
        for (LightSource s : sources.values()) {
            removeDisplay(s);
            ensureDisplay(s);
            s.geometryDirty = true;
            dirtyQueue.add(key(s.name));
        }
    }

    // ------------------------------------------------------------------ публичный API для команд

    public Collection<LightSource> all() {
        return sources.values();
    }

    public LightSource get(String name) {
        return sources.get(key(name));
    }

    public List<String> names() {
        List<String> list = new ArrayList<>();
        for (LightSource s : sources.values()) {
            list.add(s.name);
        }
        return list;
    }

    public Set<String> groupNames() {
        Set<String> groups = new TreeSet<>();
        for (LightSource s : sources.values()) {
            if (s.group != null) {
                groups.add(s.group);
            }
        }
        return groups;
    }

    public List<LightSource> groupMembers(String group) {
        List<LightSource> list = new ArrayList<>();
        for (LightSource s : sources.values()) {
            if (s.group != null && s.group.equalsIgnoreCase(group)) {
                list.add(s);
            }
        }
        return list;
    }

    public LightSource create(String name, LightType type, Location loc) {
        LightSource s = new LightSource(name, type);
        s.world = loc.getWorld().getName();
        s.x = Math.floor(loc.getX()) + 0.5;
        s.y = Math.floor(loc.getY()) + 0.5;
        s.z = Math.floor(loc.getZ()) + 0.5;
        s.yaw = normalizeYaw(loc.getYaw());
        s.pitch = clampPitch(loc.getPitch());
        s.brightness = settings.defaultBrightness();
        s.angle = settings.defaultAngle();
        s.range = settings.defaultRange();
        s.rays = settings.defaultRays();
        s.beam = settings.beamDefault();
        sources.put(key(name), s);
        ensureDisplay(s);
        changed(s, true);
        return s;
    }

    public void remove(LightSource s) {
        diff(s, new HashMap<>());
        removeDisplay(s);
        String k = key(s.name);
        sources.remove(k);
        dirtyQueue.remove(k);
        delayedDirty.remove(k);
        store.scheduleLightsSave();
    }

    public void setEnabled(LightSource s, boolean enabled) {
        s.enabled = enabled;
        changed(s, false);
    }

    public void setBrightness(LightSource s, int brightness) {
        s.brightness = Math.max(0, Math.min(15, brightness));
        s.lastEffectKey = -1;
        changed(s, false);
    }

    public void setDirection(LightSource s, float yaw, float pitch) {
        s.yaw = normalizeYaw(yaw);
        s.pitch = clampPitch(pitch);
        ensureDisplay(s);
        changed(s, true);
    }

    public void aimAt(LightSource s, double tx, double ty, double tz) {
        double dx = tx - s.x;
        double dy = ty - s.y;
        double dz = tz - s.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-6 && Math.abs(dy) < 1.0E-6) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        setDirection(s, yaw, pitch);
    }

    public void move(LightSource s, double x, double y, double z) {
        s.x = x;
        s.y = y;
        s.z = z;
        ensureDisplay(s);
        changed(s, true);
    }

    public void setPos2(LightSource s, double x, double y, double z) {
        s.hasPos2 = true;
        s.x2 = x;
        s.y2 = y;
        s.z2 = z;
        changed(s, true);
    }

    public void setAngle(LightSource s, float angle) {
        s.angle = Math.max(1f, Math.min(settings.maxAngle(), angle));
        changed(s, true);
    }

    public void setRange(LightSource s, int range) {
        s.range = Math.max(1, Math.min(settings.maxRange(), range));
        changed(s, true);
    }

    public void setRays(LightSource s, int rays) {
        s.rays = Math.max(1, Math.min(settings.maxRays(), rays));
        changed(s, true);
    }

    public void setBeam(LightSource s, boolean beam) {
        s.beam = beam;
        store.scheduleLightsSave();
    }

    public void setEffect(LightSource s, Effect effect) {
        s.effect = effect;
        s.effectFactor = 1.0;
        s.lastEffectKey = -1;
        changed(s, false);
    }

    public void setGroup(LightSource s, String group) {
        s.group = group;
        store.scheduleLightsSave();
    }

    private void changed(LightSource s, boolean geometry) {
        if (geometry) {
            s.geometryDirty = true;
        }
        dirtyQueue.add(key(s.name));
        store.scheduleLightsSave();
    }

    // ------------------------------------------------------------------ события чанков

    public void onChunkLoad(String world, int cx, int cz) {
        long ck = chunkKey(cx, cz);

        Map<Long, Set<Long>> dm = deferred.get(world);
        if (dm != null) {
            Set<Long> set = dm.remove(ck);
            if (set != null) {
                for (long pos : set) {
                    pending.add(new PosKey(world, pos));
                }
            }
        }

        Map<Long, List<Long>> rm = registry.get(world);
        if (rm != null && rm.containsKey(ck)) {
            adoptQueue.add(new ChunkRef(world, ck));
        }

        for (LightSource s : sources.values()) {
            if (!s.world.equals(world)) {
                continue;
            }
            int scx = fl(s.x) >> 4;
            int scz = fl(s.z) >> 4;
            boolean own = scx == cx && scz == cz;
            boolean near = s.truncated
                    && Math.max(Math.abs(cx - scx), Math.abs(cz - scz)) * 16 <= reachOf(s) + 16;
            if (own || near) {
                delayedDirty.add(key(s.name));
            }
        }
    }

    private int reachOf(LightSource s) {
        if (s.type == LightType.SPOT) {
            return Math.min(s.range, settings.maxRange());
        }
        if (!s.hasPos2) {
            return 16;
        }
        return (int) Math.max(Math.abs(s.x2 - s.x), Math.abs(s.z2 - s.z)) + 16;
    }

    // ------------------------------------------------------------------ главный тик

    private void tick() {
        tick++;
        if (tick % 5 == 0) {
            processDelayed();
        }
        if (tick % settings.effectsInterval() == 0) {
            effectsTick();
        }
        if (tick % settings.beamInterval() == 0) {
            beamTick();
        }
        processSources();
        processPending();
    }

    private void processDelayed() {
        if (!adoptQueue.isEmpty()) {
            List<ChunkRef> refs = new ArrayList<>(adoptQueue);
            adoptQueue.clear();
            for (ChunkRef ref : refs) {
                adopt(ref);
            }
        }
        if (!delayedDirty.isEmpty()) {
            List<String> keys = new ArrayList<>(delayedDirty);
            delayedDirty.clear();
            for (String k : keys) {
                LightSource s = sources.get(k);
                if (s == null) {
                    continue;
                }
                ensureDisplay(s);
                s.geometryDirty = true;
                dirtyQueue.add(k);
            }
        }
        while (!orphanChecks.isEmpty() && orphanChecks.peekFirst().tick() <= tick) {
            pending.add(orphanChecks.pollFirst().key());
        }
    }

    /**
     * Усыновляет блоки света из реестра прошлого запуска в загруженном чанке.
     */
    private void adopt(ChunkRef ref) {
        Map<Long, List<Long>> rm = registry.get(ref.world());
        if (rm == null) {
            return;
        }
        List<Long> list = rm.remove(ref.chunk());
        if (list == null) {
            return;
        }
        World w = Bukkit.getWorld(ref.world());
        if (w == null) {
            rm.put(ref.chunk(), list);
            return;
        }
        Map<Long, Integer> pm = placed.computeIfAbsent(ref.world(), n -> new HashMap<>());
        for (long pos : list) {
            int x = unX(pos);
            Chunk chunk = loadedChunk(w, x >> 4, unZ(pos) >> 4);
            if (chunk == null) {
                rm.computeIfAbsent(ref.chunk(), c -> new ArrayList<>()).add(pos);
                continue;
            }
            Block block = chunk.getBlock(x & 15, unY(pos), unZ(pos) & 15);
            if (block.getType() == Material.LIGHT && block.getBlockData() instanceof Light light) {
                if (pm.put(pos, light.getLevel()) == null) {
                    placedCount++;
                }
                // Через ~2 секунды (источники успеют пересчитаться) проверим, нужен ли блок.
                orphanChecks.add(new Due(new PosKey(ref.world(), pos), tick + 40));
            }
        }
        store.schedulePlacedSave();
    }

    // ------------------------------------------------------------------ пересчёт источников

    private void processSources() {
        int budget = settings.sourceUpdatesPerTick();
        Iterator<String> it = dirtyQueue.iterator();
        while (it.hasNext() && budget > 0) {
            String k = it.next();
            it.remove();
            LightSource s = sources.get(k);
            if (s == null) {
                continue;
            }
            if (processSource(s)) {
                budget--;
            }
        }
    }

    /**
     * @return true, если выполнялась тяжёлая работа (трассировка лучей).
     */
    private boolean processSource(LightSource s) {
        World w = Bukkit.getWorld(s.world);
        if (w == null) {
            return false;
        }
        if (loadedChunk(w, fl(s.x) >> 4, fl(s.z) >> 4) == null) {
            return false;
        }
        boolean heavy = false;
        if (s.geometryDirty) {
            computeGeometry(s, w);
            s.geometryDirty = false;
            heavy = true;
        }
        applyLevels(s);
        return heavy;
    }

    private void computeGeometry(LightSource s, World w) {
        ChunkCache cache = new ChunkCache(w);
        Map<Long, Float> hits = new LinkedHashMap<>();
        s.beamLength = 0;
        switch (s.type) {
            case SPOT -> geometrySpot(s, cache, hits);
            case LAMP -> addCell(cache, hits, fl(s.x), fl(s.y), fl(s.z), 1f);
            case STRIP -> geometryStrip(s, cache, hits);
            case AREA -> geometryArea(s, cache, hits);
        }
        s.truncated = cache.truncated;

        int n = hits.size();
        s.hitPos = new long[n];
        s.hitFactor = new float[n];
        int i = 0;
        for (Map.Entry<Long, Float> e : hits.entrySet()) {
            s.hitPos[i] = e.getKey();
            s.hitFactor[i] = e.getValue();
            i++;
        }
    }

    private void geometrySpot(LightSource s, ChunkCache cache, Map<Long, Float> hits) {
        int n = Math.max(1, Math.min(s.rays, settings.maxRays()));
        double range = Math.max(1, Math.min(s.range, settings.maxRange()));
        double tanHalf = Math.tan(Math.toRadians(Math.min(s.angle, settings.maxAngle()) / 2.0));
        int maxBlocks = settings.maxBlocksPerSource();
        double[] d = s.direction();

        // Ортонормированный базис (u, v) в плоскости, перпендикулярной направлению.
        double ux, uy, uz;
        if (Math.abs(d[1]) > 0.99) {
            ux = 1;
            uy = 0;
            uz = 0;
        } else {
            ux = d[2];
            uy = 0;
            uz = -d[0];
            double len = Math.sqrt(ux * ux + uz * uz);
            ux /= len;
            uz /= len;
        }
        double vx = d[1] * uz - d[2] * uy;
        double vy = d[2] * ux - d[0] * uz;
        double vz = d[0] * uy - d[1] * ux;

        s.beamLength = range;
        int[] prev = new int[3];
        for (int i = 0; i < n; i++) {
            double rf = Math.sqrt((double) i / n);
            double theta = i * GOLDEN_ANGLE;
            double off = rf * tanHalf;
            double rx = Math.cos(theta) * off;
            double ry = Math.sin(theta) * off;
            double dx = d[0] + ux * rx + vx * ry;
            double dy = d[1] + uy * rx + vy * ry;
            double dz = d[2] + uz * rx + vz * ry;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len;
            dy /= len;
            dz /= len;

            double t = trace(cache, s.x, s.y, s.z, dx, dy, dz, range, prev);
            if (i == 0 && t >= 0) {
                s.beamLength = t;
            }
            if (t < 0) {
                continue;
            }
            Material m = cache.get(prev[0], prev[1], prev[2]);
            if (m == null || !(m.isAir() || m == Material.LIGHT)) {
                continue;
            }
            double distF = 1.0 - settings.distanceFalloff() * Math.min(1.0, t / range);
            double edgeF = 1.0 - settings.edgeFalloff() * rf * rf;
            float f = (float) Math.max(0.0, distF * edgeF);
            if (f <= 0f) {
                continue;
            }
            hits.merge(pack(prev[0], prev[1], prev[2]), f, Math::max);
            if (hits.size() >= maxBlocks) {
                break;
            }
        }
    }

    private void geometryStrip(LightSource s, ChunkCache cache, Map<Long, Float> hits) {
        double x2 = s.hasPos2 ? s.x2 : s.x;
        double y2 = s.hasPos2 ? s.y2 : s.y;
        double z2 = s.hasPos2 ? s.z2 : s.z;
        double dx = x2 - s.x;
        double dy = y2 - s.y;
        double dz = z2 - s.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int count = (int) Math.ceil(length / settings.stripSpacing());
        int max = settings.maxBlocksPerSource();
        for (int i = 0; i <= count && hits.size() < max; i++) {
            double t = count == 0 ? 0.0 : (double) i / count;
            addCell(cache, hits, fl(s.x + dx * t), fl(s.y + dy * t), fl(s.z + dz * t), 1f);
        }
    }

    private void geometryArea(LightSource s, ChunkCache cache, Map<Long, Float> hits) {
        double x2 = s.hasPos2 ? s.x2 : s.x;
        double y2 = s.hasPos2 ? s.y2 : s.y;
        double z2 = s.hasPos2 ? s.z2 : s.z;
        int minX = fl(Math.min(s.x, x2)), maxX = fl(Math.max(s.x, x2));
        int minY = fl(Math.min(s.y, y2)), maxY = fl(Math.max(s.y, y2));
        int minZ = fl(Math.min(s.z, z2)), maxZ = fl(Math.max(s.z, z2));
        int step = settings.areaSpacing();
        int max = settings.maxBlocksPerSource();
        for (int x = minX; x <= maxX; x += step) {
            for (int y = minY; y <= maxY; y += step) {
                for (int z = minZ; z <= maxZ; z += step) {
                    if (hits.size() >= max) {
                        return;
                    }
                    addCell(cache, hits, x, y, z, 1f);
                }
            }
        }
    }

    private void addCell(ChunkCache cache, Map<Long, Float> hits, int x, int y, int z, float factor) {
        if (y < cache.minY || y >= cache.maxY) {
            return;
        }
        Material m = cache.get(x, y, z);
        if (m == null) {
            cache.truncated = true;
            return;
        }
        if (m.isAir() || m == Material.LIGHT) {
            hits.merge(pack(x, y, z), factor, Math::max);
        }
    }

    /**
     * Трассировка луча по вокселям (Amanatides–Woo) до первого твёрдого блока.
     * Незагруженные чанки не подгружаются: луч в них обрывается, источник помечается truncated.
     *
     * @param prev выход: координаты последней проходимой клетки перед препятствием
     * @return расстояние до препятствия или -1, если луч ничего не встретил
     */
    private double trace(ChunkCache cache, double ox, double oy, double oz,
                         double dx, double dy, double dz, double max, int[] prev) {
        int x = fl(ox), y = fl(oy), z = fl(oz);
        int sx = dx > 0 ? 1 : -1;
        int sy = dy > 0 ? 1 : -1;
        int sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tdy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tdz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tmx = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx;
        double tmy = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy;
        double tmz = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz;
        int px = x, py = y, pz = z;

        while (true) {
            double t;
            if (tmx < tmy && tmx < tmz) {
                t = tmx;
                x += sx;
                tmx += tdx;
            } else if (tmy < tmz) {
                t = tmy;
                y += sy;
                tmy += tdy;
            } else {
                t = tmz;
                z += sz;
                tmz += tdz;
            }
            if (t > max) {
                return -1;
            }
            if (y < cache.minY || y >= cache.maxY) {
                return -1;
            }
            Material m = cache.get(x, y, z);
            if (m == null) {
                cache.truncated = true;
                return -1;
            }
            if (isSolid(m)) {
                prev[0] = px;
                prev[1] = py;
                prev[2] = pz;
                return t;
            }
            px = x;
            py = y;
            pz = z;
        }
    }

    private static boolean isSolid(Material m) {
        int o = m.ordinal();
        byte c = SOLID_CACHE[o];
        if (c == 0) {
            c = (byte) (m.isSolid() ? 1 : 2);
            SOLID_CACHE[o] = c;
        }
        return c == 1;
    }

    /**
     * Доступ к блокам без подгрузки чанков, с кэшем чанков на время одного пересчёта.
     */
    private static final class ChunkCache {
        final World world;
        final int minY;
        final int maxY;
        boolean truncated;
        private final Map<Long, Chunk> chunks = new HashMap<>();

        ChunkCache(World world) {
            this.world = world;
            this.minY = world.getMinHeight();
            this.maxY = world.getMaxHeight();
        }

        Material get(int x, int y, int z) {
            long ck = chunkKey(x >> 4, z >> 4);
            Chunk chunk;
            if (chunks.containsKey(ck)) {
                chunk = chunks.get(ck);
            } else {
                chunk = loadedChunk(world, x >> 4, z >> 4);
                chunks.put(ck, chunk);
            }
            if (chunk == null) {
                return null;
            }
            return chunk.getBlock(x & 15, y, z & 15).getType();
        }
    }

    // ------------------------------------------------------------------ уровни и дифф

    private void applyLevels(LightSource s) {
        Map<Long, Integer> next = new HashMap<>();
        if (s.enabled && s.brightness > 0) {
            double eff = s.brightness * (s.effect == Effect.NONE ? 1.0 : s.effectFactor);
            int cap = settings.maxTotalBlocks();
            int added = 0;
            for (int i = 0; i < s.hitPos.length; i++) {
                int level = (int) Math.round(eff * s.hitFactor[i]);
                if (level > 15) {
                    level = 15;
                }
                if (level < 1) {
                    continue;
                }
                long pos = s.hitPos[i];
                if (!s.applied.containsKey(pos)) {
                    if (placedCount + added >= cap) {
                        continue;
                    }
                    added++;
                }
                next.put(pos, level);
            }
        }
        diff(s, next);
    }

    /**
     * Переводит вклад источника в новое состояние и ставит изменившиеся позиции в очередь.
     */
    private void diff(LightSource s, Map<Long, Integer> next) {
        String sk = key(s.name);
        Map<Long, Map<String, Integer>> cm = contributions.computeIfAbsent(s.world, w -> new HashMap<>());
        Map<Long, Integer> pm = placed.computeIfAbsent(s.world, w -> new HashMap<>());

        for (long pos : s.applied.keySet()) {
            if (!next.containsKey(pos)) {
                Map<String, Integer> c = cm.get(pos);
                if (c != null) {
                    c.remove(sk);
                    if (c.isEmpty()) {
                        cm.remove(pos);
                    }
                }
                pending.add(new PosKey(s.world, pos));
            }
        }
        for (Map.Entry<Long, Integer> e : next.entrySet()) {
            long pos = e.getKey();
            int level = e.getValue();
            Integer old = s.applied.get(pos);
            if (old == null || old != level) {
                cm.computeIfAbsent(pos, p -> new HashMap<>(2)).put(sk, level);
                pending.add(new PosKey(s.world, pos));
            } else {
                Integer cur = pm.get(pos);
                if (cur == null || cur < level) {
                    pending.add(new PosKey(s.world, pos));
                }
            }
        }
        s.applied = next;
    }

    private void processPending() {
        int budget = settings.blockUpdatesPerTick();
        Iterator<PosKey> it = pending.iterator();
        while (it.hasNext() && budget > 0) {
            PosKey k = it.next();
            it.remove();
            reconcile(k);
            budget--;
        }
    }

    private int desiredLevel(String world, long pos) {
        Map<Long, Map<String, Integer>> cm = contributions.get(world);
        if (cm == null) {
            return 0;
        }
        Map<String, Integer> c = cm.get(pos);
        if (c == null) {
            return 0;
        }
        int max = 0;
        for (int v : c.values()) {
            max = Math.max(max, v);
        }
        return max;
    }

    /**
     * Приводит один блок мира к желаемому уровню света.
     */
    private void reconcile(PosKey k) {
        World w = Bukkit.getWorld(k.world());
        if (w == null) {
            return;
        }
        long pos = k.pos();
        int x = unX(pos);
        int y = unY(pos);
        int z = unZ(pos);
        int desired = desiredLevel(k.world(), pos);
        Map<Long, Integer> pm = placed.computeIfAbsent(k.world(), n -> new HashMap<>());
        Integer curObj = pm.get(pos);
        int cur = curObj == null ? 0 : curObj;
        if (desired == cur) {
            return;
        }

        Chunk chunk = loadedChunk(w, x >> 4, z >> 4);
        if (chunk == null) {
            deferred.computeIfAbsent(k.world(), n -> new HashMap<>())
                    .computeIfAbsent(chunkKey(x >> 4, z >> 4), n -> new HashSet<>())
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
                // Позицию занял другой блок — света здесь не будет.
                if (curObj != null) {
                    pm.remove(pos);
                    placedCount--;
                    store.schedulePlacedSave();
                }
                return;
            }
            if (curObj == null) {
                placedCount++;
            }
            pm.put(pos, desired);
        } else {
            if (type == Material.LIGHT) {
                block.setType(Material.AIR, false);
            }
            pm.remove(pos);
            placedCount--;
        }
        store.schedulePlacedSave();
    }

    private BlockData lightData(int level) {
        BlockData data = lightData[level];
        if (data == null) {
            final int lv = level;
            data = Bukkit.createBlockData(Material.LIGHT, d -> ((Light) d).setLevel(lv));
            lightData[level] = data;
        }
        return data;
    }

    // ------------------------------------------------------------------ эффекты

    private void effectsTick() {
        for (LightSource s : sources.values()) {
            if (s.effect == Effect.NONE || !s.enabled) {
                continue;
            }
            s.effectFactor = computeEffect(s);
            int effKey = (int) Math.round(s.brightness * s.effectFactor * 2.0);
            if (effKey != s.lastEffectKey) {
                s.lastEffectKey = effKey;
                dirtyQueue.add(key(s.name));
            }
        }
    }

    private double computeEffect(LightSource s) {
        return switch (s.effect) {
            case FLICKER -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                if (rnd.nextDouble() < settings.flickerChance()) {
                    yield settings.flickerMin() + rnd.nextDouble() * (1.0 - settings.flickerMin());
                }
                yield s.effectFactor;
            }
            case PULSE -> {
                double period = settings.pulsePeriod();
                double phase = (tick % settings.pulsePeriod()) / period;
                double wave = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * phase - Math.PI / 2.0);
                yield settings.pulseMin() + (1.0 - settings.pulseMin()) * wave;
            }
            case BLINK -> {
                long m = tick % (settings.blinkOn() + settings.blinkOff());
                yield m < settings.blinkOn() ? 1.0 : 0.0;
            }
            default -> 1.0;
        };
    }

    // ------------------------------------------------------------------ луч из частиц

    private void beamTick() {
        double view = settings.beamView();
        double view2 = view * view;
        double step = settings.beamStep();
        Particle.DustOptions dust = settings.dust();

        for (LightSource s : sources.values()) {
            if (s.type != LightType.SPOT || !s.enabled || !s.beam || s.brightness <= 0) {
                continue;
            }
            if (s.effect != Effect.NONE && s.effectFactor < 0.05) {
                continue;
            }
            World w = Bukkit.getWorld(s.world);
            if (w == null) {
                continue;
            }
            List<Player> players = w.getPlayers();
            if (players.isEmpty()) {
                continue;
            }
            double[] d = s.direction();
            double length = s.beamLength > 0 ? s.beamLength : Math.min(s.range, settings.maxRange());
            for (double t = step; t <= length; t += step) {
                double px = s.x + d[0] * t;
                double py = s.y + d[1] * t;
                double pz = s.z + d[2] * t;
                List<Player> near = null;
                for (Player p : players) {
                    double ddx = p.getX() - px;
                    double ddy = p.getY() - py;
                    double ddz = p.getZ() - pz;
                    if (ddx * ddx + ddy * ddy + ddz * ddz <= view2) {
                        if (near == null) {
                            near = new ArrayList<>();
                        }
                        near.add(p);
                    }
                }
                if (near == null) {
                    continue;
                }
                new ParticleBuilder(Particle.DUST)
                        .location(w, px, py, pz)
                        .count(1)
                        .offset(0, 0, 0)
                        .extra(0)
                        .data(dust)
                        .force(true)
                        .receivers(near)
                        .spawn();
            }
        }
    }

    // ------------------------------------------------------------------ модель (item_display)

    private void ensureDisplay(LightSource s) {
        if (!settings.modelEnabled()) {
            removeDisplay(s);
            return;
        }
        World w = Bukkit.getWorld(s.world);
        if (w == null || loadedChunk(w, fl(s.x) >> 4, fl(s.z) >> 4) == null) {
            return;
        }
        Entity existing = s.displayId == null ? null : Bukkit.getEntity(s.displayId);
        Location loc = new Location(w, s.x, s.y, s.z,
                s.yaw + settings.yawOffset(), s.pitch + settings.pitchOffset());
        if (existing instanceof ItemDisplay display && display.isValid()) {
            display.teleport(loc);
            return;
        }
        ItemDisplay display = w.spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(settings.modelItem(s.type));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(settings.modelViewRange());
            d.setTeleportDuration(3);
            float scale = settings.modelScale();
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            d.addScoreboardTag("lightset");
        });
        s.displayId = display.getUniqueId();
    }

    private void removeDisplay(LightSource s) {
        if (s.displayId != null) {
            Entity e = Bukkit.getEntity(s.displayId);
            if (e != null) {
                e.remove();
            }
            s.displayId = null;
        }
    }

    // ------------------------------------------------------------------ сохранение

    private String serializeLights() {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("lights");
        for (LightSource s : sources.values()) {
            s.save(root.createSection(s.name));
        }
        return cfg.saveToString();
    }

    private List<String> snapshotPlaced() {
        List<String> list = new ArrayList<>(placedCount + 16);
        for (Map.Entry<String, Map<Long, Integer>> e : placed.entrySet()) {
            for (long pos : e.getValue().keySet()) {
                list.add(pos + ";" + e.getKey());
            }
        }
        // Ещё не усыновлённые записи прошлого запуска тоже сохраняем.
        for (Map.Entry<String, Map<Long, List<Long>>> e : registry.entrySet()) {
            for (List<Long> positions : e.getValue().values()) {
                for (long pos : positions) {
                    list.add(pos + ";" + e.getKey());
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ утилиты

    /**
     * Загруженный чанк или null. Никогда не подгружает и не генерирует чанки.
     */
    static Chunk loadedChunk(World world, int cx, int cz) {
        return world.isChunkLoaded(cx, cz) ? world.getChunkAt(cx, cz) : null;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static int fl(double v) {
        return (int) Math.floor(v);
    }

    static float normalizeYaw(float yaw) {
        float y = yaw % 360f;
        if (y > 180f) {
            y -= 360f;
        } else if (y <= -180f) {
            y += 360f;
        }
        return y;
    }

    static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    static int unX(long l) {
        return (int) (l >> 38);
    }

    static int unY(long l) {
        return (int) (l << 52 >> 52);
    }

    static int unZ(long l) {
        return (int) (l << 26 >> 38);
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    private static long chunkKeyOfPos(long pos) {
        return chunkKey(unX(pos) >> 4, unZ(pos) >> 4);
    }
}
