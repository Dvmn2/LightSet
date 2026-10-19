package net.dvmn2.lightset;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Реакция на загрузку чанков: восстановление моделей, света и отложенных блоков.
 */
public final class LightListener implements Listener {

    private final LightManager manager;

    public LightListener(LightManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.onChunkLoad(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
