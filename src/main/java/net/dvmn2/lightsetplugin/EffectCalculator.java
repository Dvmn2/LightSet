package net.dvmn2.lightsetplugin;

import net.dvmn2.lightsetplugin.model.LightSource;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Computes the brightness multiplier of a source for its current effect.
 * Main thread only; holds no state of its own.
 */
public final class EffectCalculator {

    private static final double RANDOM_FULL = 1.0;

    private final Supplier<LightSetSettings> settings;

    /**
     * Creates the calculator.
     *
     * @param settings supplier of the current settings snapshot
     */
    public EffectCalculator(Supplier<LightSetSettings> settings) {
        this.settings = settings;
    }

    /**
     * Computes the multiplier.
     *
     * @param source source with an effect
     * @param tick   current server tick counter of the plugin
     * @return multiplier in {@code 0..1}
     */
    public double compute(LightSource source, long tick) {
        LightSetSettings.Effects cfg = settings.get().effects();
        return switch (source.getEffect()) {
            case NONE -> 1.0;
            case FLICKER -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                if (random.nextDouble() < cfg.flicker().changeChance()) {
                    yield cfg.flicker().min() + random.nextDouble() * (RANDOM_FULL - cfg.flicker().min());
                }
                yield source.getEffectFactor();
            }
            case PULSE -> {
                double period = cfg.pulse().periodTicks();
                double phase = (tick % cfg.pulse().periodTicks()) / period;
                double wave = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * phase - Math.PI / 2.0);
                yield cfg.pulse().min() + (1.0 - cfg.pulse().min()) * wave;
            }
            case BLINK -> {
                long phase = tick % (cfg.blink().onTicks() + cfg.blink().offTicks());
                yield phase < cfg.blink().onTicks() ? 1.0 : 0.0;
            }
        };
    }
}
