package net.dvmn2.lightsetplugin;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Factory of the {@link NamespacedKey}s used by the plugin. Built once from the plugin instance.
 */
public final class PluginKeys {

    private final NamespacedKey display;

    /**
     * Creates the keys.
     *
     * @param plugin owner of the keys
     */
    public PluginKeys(Plugin plugin) {
        this.display = new NamespacedKey(plugin, "display");
    }

    /** Returns the PDC key (string: source name) that marks a model entity created by the plugin. */
    public NamespacedKey getDisplay() {
        return display;
    }
}
