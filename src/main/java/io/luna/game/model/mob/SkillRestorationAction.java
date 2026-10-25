package io.luna.game.model.mob;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;

/**
 * An {@link Action} that gradually restores temporarily modified skill levels toward their static levels.
 * <p>
 * On each execution, this action scans the owning mob's {@link SkillSet} and adjusts every eligible skill by one
 * level toward its static value. Skills below their static level are increased, while skills above their static level
 * are reduced by exactly one without discarding the rest of a temporary boost.
 * <p>
 * Prayer is excluded from this restoration flow because it is handled separately by its own mechanics.
 * <p>
 * The action finishes on the execution that brings all eligible skills to their static levels, or when the mob is
 * no longer alive. Restoration
 * must not raise zero hitpoints while a death action is pending, since that would make the victim alive again.
 *
 * @author lare96
 */
public final class SkillRestorationAction extends Action<Mob> {

    /**
     * The skill set being restored by this action.
     */
    private final SkillSet skills;

    /**
     * Creates a new {@link SkillRestorationAction}.
     *
     * @param mob The mob whose skills will be restored.
     */
    public SkillRestorationAction(Mob mob) {
        super(mob, ActionType.SOFT, false, 100);
        skills = mob.getSkills();
    }

    @Override
    public boolean run() {
        if (!mob.isAlive()) {
            return true;
        }
        boolean done = true;
        for (Skill nextSkill : skills) {
            if (nextSkill.getId() == Skill.PRAYER) {
                continue;
            }

            int level = nextSkill.getLevel();
            int staticLevel = nextSkill.getStaticLevel();

            if (level != staticLevel) {
                if (level < staticLevel) {
                    nextSkill.adjustLevel(1);
                } else if(mob instanceof Player) {
                    nextSkill.setLevel(level - 1);
                }
                done &= nextSkill.getLevel() == staticLevel;
            }
        }
        return done;
    }
}
