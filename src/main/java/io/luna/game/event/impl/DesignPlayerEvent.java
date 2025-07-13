package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * A {@link PlayerEvent} implementation sent when the design interface is selected.
 *
 * @author lare96
 */
public final class DesignPlayerEvent extends PlayerEvent {

    /**
     * The gender.
     */
    private final int gender;

    /**
     * The models.
     */
    private final byte[] models;

    /**
     * The colors.
     */
    private final byte[] colors;

    /**
     * The entire array of appearance values.
     */
    private final int[] values;

    /**
     * Creates a new {@link DesignPlayerEvent}.
     *
     * @param plr    The player.
     * @param gender The gender.
     * @param models The models.
     * @param colors The colors.
     * @param values The entire array of appearance values.
     */
    public DesignPlayerEvent(Player plr, int gender, byte[] models, byte[] colors, int[] values) {
        super(plr);
        this.gender = gender;
        this.models = models;
        this.colors = colors;
        this.values = values;
    }

    /**
     * @return The gender.
     */
    public int getGender() {
        return gender;
    }

    /**
     * @return The models.
     */
    public byte[] getModels() {
        return models;
    }

    /**
     * @return The colors.
     */
    public byte[] getColors() {
        return colors;
    }

    /**
     * @return The entire array of appearance values.
     */
    public int[] getValues() {
        return values;
    }
}
