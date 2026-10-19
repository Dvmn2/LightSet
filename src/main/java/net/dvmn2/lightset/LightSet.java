package net.dvmn2.lightset;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина LightSet: динамические прожекторы и освещение
 * на основе световых блоков (minecraft:light).
 */
public final class LightSet extends JavaPlugin {

    private net.dvmn2.lightset.LightManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        net.dvmn2.lightset.LightSettings settings = new net.dvmn2.lightset.LightSettings(this);
        net.dvmn2.lightset.Lang.setLanguage(getConfig().getString("language", "auto"));

        net.dvmn2.lightset.LightStore store = new net.dvmn2.lightset.LightStore(this);
        manager = new net.dvmn2.lightset.LightManager(this, settings, store);
        manager.start();

        getServer().getPluginManager().registerEvents(new net.dvmn2.lightset.LightListener(manager), this);

        net.dvmn2.lightset.LightSetCommand command = new net.dvmn2.lightset.LightSetCommand(manager);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(command.create(), "Управление источниками света LightSet");
        });

        getLogger().info("LightSet enabled!");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        getLogger().info("LightSet disabled!");
    }
}
