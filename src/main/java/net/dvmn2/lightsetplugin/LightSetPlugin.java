package net.dvmn2.lightsetplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.lightsetplugin.command.LightSetCommand;
import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.storage.LightStore;
import net.dvmn2.lightsetplugin.storage.PlacedStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Composition root of LightSetPlugin: dynamic spotlights and lighting built from light blocks.
 * Only wires objects together and controls the enable/disable order.
 */
public final class LightSetPlugin extends JavaPlugin {

    private volatile LightSetSettings settings;
    private PersistenceManager persistence;
    private LightBlockManager blocks;
    private LightManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        Path dataFolder = getDataFolder().toPath();
        LightStore lightStore = new LightStore(dataFolder, getLogger());
        PlacedStore placedStore = new PlacedStore(dataFolder, getLogger());
        persistence = new PersistenceManager(this, lightStore, placedStore,
                () -> manager.snapshotLights(), () -> blocks.snapshotPlaced());
        blocks = new LightBlockManager(() -> settings, persistence::markPlacedDirty);
        ModelManager models = new ModelManager(new PluginKeys(this), () -> settings);
        manager = new LightManager(this, () -> settings, new LightGeometry(() -> settings), blocks, models,
                new EffectCalculator(() -> settings), new BeamRenderer(() -> settings), persistence);

        blocks.loadRegistry(placedStore.load());
        List<LightSource> loaded = lightStore.load();
        manager.start(loaded);
        persistence.start();

        getServer().getPluginManager().registerEvents(new LightListener(manager, models), this);

        LightSetCommand command = new LightSetCommand(this, manager);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Manage LightSet light sources"));

        getLogger().info("Enabled with " + loaded.size() + " light source(s).");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        if (persistence != null) {
            persistence.shutdown();
        }
    }

    /** Re-reads config.yml and rebuilds everything derived from it without losing state. */
    public void reloadAll() {
        reloadConfig();
        reloadSettings();
        manager.onSettingsReloaded();
    }

    private void reloadSettings() {
        settings = LightSetSettings.parse(getConfig(), getLogger());
        Lang.setLanguage(settings.language());
    }
}
