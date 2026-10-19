package net.dvmn2.lightset;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Файловое хранилище:
 * <ul>
 *   <li>{@code lights.yml} — источники света;</li>
 *   <li>{@code placed.yml} — реестр поставленных световых блоков («мир;позиция»),
 *       чтобы после рестарта/краша их можно было найти и не оставить мусор в мире.</li>
 * </ul>
 * Сохранение отложенное (debounce), запись файла — в async-потоке. Снимок данных
 * делается в основном потоке, поэтому блокировок на данные не нужно.
 */
public final class LightStore {

    private static final long LIGHTS_SAVE_DELAY = 20L;
    private static final long PLACED_SAVE_DELAY = 100L;

    private final JavaPlugin plugin;
    private final File lightsFile;
    private final File placedFile;

    private final Object ioLock = new Object();
    private final long[] seq = new long[2];
    private final long[] written = new long[2];

    private Supplier<String> lightsSnapshot = () -> "";
    private Supplier<List<String>> placedSnapshot = ArrayList::new;

    private BukkitTask lightsTask;
    private BukkitTask placedTask;

    public LightStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lightsFile = new File(plugin.getDataFolder(), "lights.yml");
        this.placedFile = new File(plugin.getDataFolder(), "placed.yml");
    }

    public void bind(Supplier<String> lightsSnapshot, Supplier<List<String>> placedSnapshot) {
        this.lightsSnapshot = lightsSnapshot;
        this.placedSnapshot = placedSnapshot;
    }

    public List<LightSource> loadSources() {
        List<LightSource> result = new ArrayList<>();
        if (!lightsFile.exists()) {
            return result;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(lightsFile);
        ConfigurationSection root = cfg.getConfigurationSection("lights");
        if (root == null) {
            return result;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) {
                continue;
            }
            LightSource s = LightSource.load(name, sec);
            if (s == null) {
                plugin.getLogger().warning("Пропущен источник света без мира: " + name);
                continue;
            }
            result.add(s);
        }
        return result;
    }

    public List<String> loadPlaced() {
        if (!placedFile.exists()) {
            return new ArrayList<>();
        }
        return YamlConfiguration.loadConfiguration(placedFile).getStringList("placed");
    }

    public void scheduleLightsSave() {
        if (lightsTask != null) {
            return;
        }
        lightsTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            lightsTask = null;
            String yaml = lightsSnapshot.get();
            long id = nextSeq(0);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(lightsFile, yaml, 0, id));
        }, LIGHTS_SAVE_DELAY);
    }

    public void schedulePlacedSave() {
        if (placedTask != null) {
            return;
        }
        placedTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            placedTask = null;
            List<String> list = placedSnapshot.get();
            long id = nextSeq(1);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(placedFile, placedYaml(list), 1, id));
        }, PLACED_SAVE_DELAY);
    }

    /**
     * Синхронная запись при выключении плагина (задачи планировщика уже недоступны).
     */
    public void flushSync() {
        if (lightsTask != null) {
            lightsTask.cancel();
            lightsTask = null;
        }
        if (placedTask != null) {
            placedTask.cancel();
            placedTask = null;
        }
        write(lightsFile, lightsSnapshot.get(), 0, nextSeq(0));
        write(placedFile, placedYaml(placedSnapshot.get()), 1, nextSeq(1));
    }

    private long nextSeq(int idx) {
        synchronized (ioLock) {
            return ++seq[idx];
        }
    }

    private static String placedYaml(List<String> list) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("placed", list);
        return cfg.saveToString();
    }

    /**
     * Запись через временный файл; устаревшие (по номеру) записи отбрасываются.
     */
    private void write(File file, String content, int idx, long id) {
        synchronized (ioLock) {
            if (id < written[idx]) {
                return;
            }
            written[idx] = id;
            try {
                File dir = file.getParentFile();
                if (dir != null) {
                    dir.mkdirs();
                }
                File tmp = new File(file.getPath() + ".tmp");
                Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось сохранить " + file.getName() + ": " + e.getMessage());
            }
        }
    }
}
