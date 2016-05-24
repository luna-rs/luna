package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.MobileEntity;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Event} implementation sent whenever the skills of a {@link MobileEntity} change.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SkillChangeEvent extends Event {

    /**
     * The {@link MobileEntity} this event is for.
     */
    private final MobileEntity mob;

    /**
     * The old amount of experience.
     */
    private final double oldExperience;

    /**
     * The old static level.
     */
    private final int oldStaticLevel;

    /**
     * The old level.
     */
    private final int oldLevel;

    /**
     * The id of the skill whose experience was changed.
     */
    private final int id;

    /**
     * Creates a new {@link SkillChangeEvent}.
     *
     * @param mob The {@link MobileEntity} this event is for.
     * @param oldExperience The old amount of experience.
     * @param oldStaticLevel The old static level.
     * @param oldLevel The old level.
     * @param id The id of the skill whose experience was changed.
     */
    public SkillChangeEvent(MobileEntity mob, double oldExperience, int oldStaticLevel, int oldLevel, int id) {
        this.mob = mob;
        this.oldExperience = oldExperience;
        this.oldStaticLevel = oldStaticLevel;
        this.oldLevel = oldLevel;
        this.id = id;
    }

    @Override
    public boolean matches(Object... args) {
        checkState(args.length == 1, "args.length != 1");
        return Objects.equals(mob.type(), args[0]);
    }

    /**
     * @return The {@link MobileEntity} this event is for.
     */
    public MobileEntity getMob() {
        return mob;
    }

    /**
     * @return The old amount of experience.
     */
    public double getOldExperience() {
        return oldExperience;
    }

    /**
     * @return The old static level.
     */
    public int getOldStaticLevel() {
        return oldStaticLevel;
    }

    /**
     * @return The old level.
     */
    public int getOldLevel() {
        return oldLevel;
    }

    /**
     * @return The id of the skill whose experience was changed.
     */
    public int getId() {
        return id;
    }
}

