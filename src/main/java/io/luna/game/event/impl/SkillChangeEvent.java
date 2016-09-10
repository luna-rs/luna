package io.luna.game.event.impl;

import io.luna.game.model.mobile.MobileEntity;

/**
 * An event sent when a mob's skills change.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SkillChangeEvent extends MobEvent {

    /**
     * The old amount of experience.
     */
    private final double oldExp;

    /**
     * The old static level.
     */
    private final int oldStaticLvl;

    /**
     * The old level.
     */
    private final int oldLvl;

    /**
     * The id of the skill whose experience was changed.
     */
    private final int id;

    /**
     * Creates a new {@link SkillChangeEvent}.
     *
     * @param oldExp The old amount of experience.
     * @param oldStaticLvl The old static level.
     * @param oldLvl The old level.
     * @param id The id of the skill whose experience was changed.
     */
    public SkillChangeEvent(MobileEntity mob, double oldExp, int oldStaticLvl, int oldLvl, int id) {
        super(mob);
        this.oldExp = oldExp;
        this.oldStaticLvl = oldStaticLvl;
        this.oldLvl = oldLvl;
        this.id = id;
    }

    /**
     * @return The old amount of experience.
     */
    public double oldExperience() {
        return oldExp;
    }

    /**
     * @return The old static level.
     */
    public int oldStaticLevel() {
        return oldStaticLvl;
    }

    /**
     * @return The old level.
     */
    public int oldLevel() {
        return oldLvl;
    }

    /**
     * @return The id of the skill whose experience was changed.
     */
    public int id() {
        return id;
    }
}

