package net.dvmn2.lightsetplugin;

import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.model.Vec3;
import net.dvmn2.lightsetplugin.util.Positions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns the visible models (item displays) of light sources.
 *
 * <p>Every model carries the {@link PluginKeys#getDisplay()} marker in its persistent data. A marked
 * display that no source owns is an orphan and is removed at start and when entities load.
 * Main thread only.
 */
public final class ModelManager {

    private static final int TELEPORT_DURATION_TICKS = 3;

    private final PluginKeys keys;
    private final Supplier<LightSetSettings> settings;
    /** Source key to the entity id of its model. */
    private final Map<String, UUID> displays = new HashMap<>();

    /**
     * Creates the manager.
     *
     * @param keys     persistent data keys
     * @param settings supplier of the current settings snapshot
     */
    public ModelManager(PluginKeys keys, Supplier<LightSetSettings> settings) {
        this.keys = keys;
        this.settings = settings;
    }

    /**
     * Spawns or moves the model of a source. Does nothing while the chunk of the source is not loaded.
     *
     * @param source source to show
     */
    public void ensure(LightSource source) {
        LightSetSettings.Model cfg = settings.get().model();
        if (!cfg.enabled()) {
            remove(source);
            return;
        }
        World world = Bukkit.getWorld(source.getWorld());
        Vec3 pos = source.getPosition();
        if (world == null || Positions.loadedChunk(world, Positions.floor(pos.x()) >> 4,
                Positions.floor(pos.z()) >> 4) == null) {
            return;
        }
        Location location = new Location(world, pos.x(), pos.y(), pos.z(),
                source.getYaw() + cfg.yawOffsetDegrees(), source.getPitch() + cfg.pitchOffsetDegrees());
        UUID id = displays.get(source.getKey());
        Entity existing = id == null ? null : Bukkit.getEntity(id);
        if (existing instanceof ItemDisplay display && display.isValid()) {
            display.teleport(location);
            return;
        }
        ItemDisplay display = world.spawn(location, ItemDisplay.class, spawned -> configure(spawned, source, cfg));
        displays.put(source.getKey(), display.getUniqueId());
    }

    /**
     * Removes the model of a source.
     *
     * @param source source whose model to remove
     */
    public void remove(LightSource source) {
        UUID id = displays.remove(source.getKey());
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    /** Removes every model owned by a source. */
    public void removeAll() {
        for (UUID id : displays.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        displays.clear();
    }

    /**
     * Removes orphaned models from the loaded entities of a world.
     *
     * @param world world to clean
     */
    public void removeOrphans(World world) {
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            if (isOrphan(display)) {
                display.remove();
            }
        }
    }

    /**
     * Removes orphaned models among freshly loaded entities.
     *
     * @param entities entities that were just loaded
     */
    public void onEntitiesLoad(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof ItemDisplay display && isOrphan(display)) {
                display.remove();
            }
        }
    }

    private boolean isOrphan(ItemDisplay display) {
        if (!display.getPersistentDataContainer().has(keys.getDisplay(), PersistentDataType.STRING)) {
            return false;
        }
        return !displays.containsValue(display.getUniqueId());
    }

    private void configure(ItemDisplay display, LightSource source, LightSetSettings.Model cfg) {
        display.setPersistent(false);
        display.setItemStack(createItem(cfg.items().get(source.getType())));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(cfg.viewRangeMultiplier());
        display.setTeleportDuration(TELEPORT_DURATION_TICKS);
        float scale = cfg.scale();
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
        display.getPersistentDataContainer().set(keys.getDisplay(), PersistentDataType.STRING, source.getName());
    }

    private static ItemStack createItem(LightSetSettings.ModelItem modelItem) {
        ItemStack item = new ItemStack(modelItem.material());
        if (modelItem.itemModel() != null) {
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(modelItem.itemModel());
            item.setItemMeta(meta);
        }
        return item;
    }
}
