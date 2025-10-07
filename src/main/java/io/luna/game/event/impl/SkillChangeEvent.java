package io.luna.game.event.impl;

import io.luna.game.model.mob.Mob;

/**
 * An event sent when a mob's skills change.
 *
 * @author lare96
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
     * The id of the skill that was changed.
     */
    private final int id;

    /**
     * Creates a new {@link SkillChangeEvent}.
     *
     * @param oldExp The old amount of experience.
     * @param oldStaticLvl The old static level.
     * @param oldLvl The old level.
     * @param id The id of the skill that was changed.
     */
    public SkillChangeEvent(Mob mob, double oldExp, int oldStaticLvl, int oldLvl, int id) {
        super(mob);
        this.oldExp = oldExp;
        this.oldStaticLvl = oldStaticLvl;
        this.oldLvl = oldLvl;
        this.id = id;
    }

    /**
     * Determines if this event results in a level up.
     *
     * @return {@code true} if {@code mob} leveled up when this event was sent.
     */
    public boolean isLevelUp() {
        if(oldStaticLvl < 99) {
            int newStaticLevel = mob.getSkills().getSkill(id).getStaticLevel();
            return newStaticLevel > oldStaticLvl;
        }
        return false;
    }

    /**
     * @return The old amount of experience.
     */
    public double getOldExp() {
        return oldExp;
    }

    /**
     * @return The old static level.
     */
    public int getOldStaticLvl() {
        return oldStaticLvl;
    }

    /**
     * @return The old level.
     */
    public int getOldLvl() {
        return oldLvl;
    }

    /**
     * @return The id of the skill that was changed.
     */
    public int getId() {
        return id;
    }
}

