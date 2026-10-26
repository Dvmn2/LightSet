package net.dvmn2.lightsetplugin;

import net.dvmn2.lightsetplugin.storage.LightStore;
import net.dvmn2.lightsetplugin.storage.PlacedStore;
import net.dvmn2.lightsetplugin.storage.dto.LightDto;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Decides when data is written to disk.
 *
 * <p>Changes only raise a {@code dirty} flag. A periodic task takes a snapshot on the main thread and
 * writes it asynchronously. Light sources are saved on the next check; the registry of placed blocks
 * is saved every few checks. It is the crash-recovery data, so it is flushed much more often than the
 * usual 30 seconds. {@link #shutdown()} writes everything synchronously.
 *
 * <p>Methods MUST be called from the main thread.
 */
public final class PersistenceManager {

    private static final long CHECK_PERIOD_TICKS = 20L;
    private static final int PLACED_CHECKS_PER_SAVE = 5;

    private final Plugin plugin;
    private final LightStore lightStore;
    private final PlacedStore placedStore;
    private final Supplier<List<LightDto>> lightsSnapshot;
    private final Supplier<Map<String, long[]>> placedSnapshot;

    private boolean lightsDirty;
    private boolean placedDirty;
    private int checks;
    private BukkitTask task;
    private boolean started;

    /**
     * Creates the manager.
     *
     * @param plugin         owner of the scheduled task
     * @param lightStore     store of light sources
     * @param placedStore    store of the placed block registry
     * @param lightsSnapshot makes a snapshot of the light sources (called on the main thread)
     * @param placedSnapshot makes a snapshot of the placed blocks (called on the main thread)
     */
    public PersistenceManager(Plugin plugin, LightStore lightStore, PlacedStore placedStore,
                              Supplier<List<LightDto>> lightsSnapshot,
                              Supplier<Map<String, long[]>> placedSnapshot) {
        this.plugin = plugin;
        this.lightStore = lightStore;
        this.placedStore = placedStore;
        this.lightsSnapshot = lightsSnapshot;
        this.placedSnapshot = placedSnapshot;
    }

    /** Starts the periodic task. Call after all data has been loaded. */
    public void start() {
        started = true;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, CHECK_PERIOD_TICKS,
                CHECK_PERIOD_TICKS);
    }

    /** Remembers that the light sources changed. */
    public void markLightsDirty() {
        lightsDirty = true;
    }

    /** Remembers that the placed block registry changed. */
    public void markPlacedDirty() {
        placedDirty = true;
    }

    /**
     * Cancels the task and writes all data synchronously.
     * Does nothing when {@link #start()} was never called, so a failed start cannot erase data.
     */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!started) {
            return;
        }
        lightStore.write(lightStore.nextTicket(), lightsSnapshot.get());
        placedStore.write(placedStore.nextTicket(), placedSnapshot.get());
        lightsDirty = false;
        placedDirty = false;
    }

    private void check() {
        checks++;
        if (lightsDirty) {
            lightsDirty = false;
            List<LightDto> snapshot = lightsSnapshot.get();
            long ticket = lightStore.nextTicket();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> lightStore.write(ticket, snapshot));
        }
        if (placedDirty && checks % PLACED_CHECKS_PER_SAVE == 0) {
            placedDirty = false;
            Map<String, long[]> snapshot = placedSnapshot.get();
            long ticket = placedStore.nextTicket();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> placedStore.write(ticket, snapshot));
        }
    }
}
