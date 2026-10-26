package net.dvmn2.lightsetplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Reacts to chunk and entity loading: restores models, light blocks and geometry, and removes
 * orphaned models. Main thread only.
 */
public final class LightListener implements Listener {

    private final LightManager manager;
    private final ModelManager models;

    /**
     * Creates the listener.
     *
     * @param manager light source manager
     * @param models  model manager
     */
    public LightListener(LightManager manager, ModelManager models) {
        this.manager = manager;
        this.models = models;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.onChunkLoad(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        models.onEntitiesLoad(event.getEntities());
    }
}
