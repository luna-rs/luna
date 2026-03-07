package io.luna.game.model.def;

/**
 * A sub-definition containing the widget ids for a weapon special attack bar.
 * <p>
 * These ids identify the special attack bar widget and its associated energy meter for a {@link WeaponTypeDefinition}.
 *
 * @author lare96
 */
public final class WeaponSpecialBarDefinition {

    /**
     * The widget id for the special attack bar.
     */
    private final int bar;

    /**
     * The widget id for the special attack energy meter.
     */
    private final int meter;

    /**
     * Creates a new {@link WeaponSpecialBarDefinition}.
     *
     * @param bar The widget id for the special attack bar.
     * @param meter The widget id for the special attack energy meter.
     */
    public WeaponSpecialBarDefinition(int bar, int meter) {
        this.bar = bar;
        this.meter = meter;
    }

    /**
     * @return The widget id for the special attack bar.
     */
    public int getBar() {
        return bar;
    }

    /**
     * @return The widget id for the special attack energy meter.
     */
    public int getMeter() {
        return meter;
    }
}