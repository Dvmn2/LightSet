package net.dvmn2.lightsetplugin;

import com.destroystokyo.paper.ParticleBuilder;
import net.dvmn2.lightsetplugin.model.Effect;
import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.model.LightType;
import net.dvmn2.lightsetplugin.model.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the visible particle beam of spotlights to nearby players. Main thread only.
 */
public final class BeamRenderer {

    private static final double MIN_VISIBLE_FACTOR = 0.05;

    private final Supplier<LightSetSettings> settings;

    /**
     * Creates the renderer.
     *
     * @param settings supplier of the current settings snapshot
     */
    public BeamRenderer(Supplier<LightSetSettings> settings) {
        this.settings = settings;
    }

    /**
     * Draws one frame of the beams of all visible spotlights.
     *
     * @param sources all sources; those that are not visible spotlights are skipped
     */
    public void render(Collection<LightSource> sources) {
        LightSetSettings.Beam cfg = settings.get().beam();
        for (LightSource source : sources) {
            if (!isVisible(source)) {
                continue;
            }
            World world = Bukkit.getWorld(source.getWorld());
            if (world == null) {
                continue;
            }
            List<Player> players = world.getPlayers();
            if (!players.isEmpty()) {
                drawBeam(source, world, players, cfg);
            }
        }
    }

    private boolean isVisible(LightSource source) {
        if (source.getType() != LightType.SPOT || !source.isEnabled() || !source.isBeamEnabled()
                || source.getBrightness() <= 0) {
            return false;
        }
        return source.getEffect() == Effect.NONE || source.getEffectFactor() >= MIN_VISIBLE_FACTOR;
    }

    private void drawBeam(LightSource source, World world, List<Player> players, LightSetSettings.Beam cfg) {
        double viewSquared = cfg.viewDistanceBlocks() * cfg.viewDistanceBlocks();
        Vec3 dir = source.getDirection();
        Vec3 origin = source.getPosition();
        double known = source.getGeometry().beamLength();
        double length = known > 0
                ? known
                : Math.min(source.getRangeBlocks(), settings.get().limits().maxRangeBlocks());
        for (double t = cfg.stepBlocks(); t <= length; t += cfg.stepBlocks()) {
            double px = origin.x() + dir.x() * t;
            double py = origin.y() + dir.y() * t;
            double pz = origin.z() + dir.z() * t;
            List<Player> near = playersNear(players, px, py, pz, viewSquared);
            if (near.isEmpty()) {
                continue;
            }
            new ParticleBuilder(Particle.DUST)
                    .location(world, px, py, pz)
                    .count(1)
                    .offset(0, 0, 0)
                    .extra(0)
                    .data(cfg.dust())
                    .force(true)
                    .receivers(near)
                    .spawn();
        }
    }

    private static List<Player> playersNear(List<Player> players, double x, double y, double z,
                                            double distanceSquared) {
        List<Player> near = new ArrayList<>(0);
        for (Player player : players) {
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= distanceSquared) {
                near.add(player);
            }
        }
        return near;
    }
}
