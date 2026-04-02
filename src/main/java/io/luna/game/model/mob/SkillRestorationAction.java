package io.luna.game.model.mob;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;

/**
 * An {@link Action} that gradually restores temporarily modified skill levels toward their static levels.
 * <p>
 * On each execution, this action scans the owning mob's {@link SkillSet} and adjusts every eligible skill by one
 * level toward its static value. Skills below their static level are increased, while skills above their static level
 * are reduced.
 * <p>
 * Prayer is excluded from this restoration flow because it is handled separately by its own mechanics.
 * <p>
 * The action finishes once all eligible skills match their static levels.
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
        boolean done = true;
        for (Skill nextSkill : skills) {
            if (nextSkill.getId() == Skill.PRAYER) {
                continue;
            }

            int level = nextSkill.getLevel();
            int staticLevel = nextSkill.getStaticLevel();

            if (level != staticLevel) {
                done = false;
                if (level < staticLevel) {
                    nextSkill.addLevels(1, false);
                } else if(mob instanceof Player) {
                    nextSkill.removeLevels(1);
                }
            }
        }
        return done;
    }
}