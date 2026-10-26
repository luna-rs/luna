package io.luna.game.model.mob;

import engine.combat.prayer.CombatPrayer;
import io.luna.game.action.Action;
import io.luna.game.action.ActionType;

/**
 * An {@link Action} that gradually restores temporarily modified skill levels toward their static levels.
 * <p>
 * Health recovery, drained-skill recovery, and boost decay use independent 100-tick clocks. Rapid Heal and
 * Rapid Restore shorten only their respective recovery clock to 50 ticks. Toggling either prayer resets only
 * that clock, preventing duplicate restoration while leaving boost decay and the other recovery clock unchanged.
 * <p>
 * Prayer is excluded from this restoration flow because it is handled separately by its own mechanics.
 * <p>
 * The action finishes when all eligible skills reach their static levels and neither rapid prayer is active,
 * or when the mob is no longer alive. Keeping active prayer clocks running at full stats preserves their phase
 * if the player is damaged or drained later. Restoration
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
     * Elapsed ticks on the independent recovery and decay clocks.
     */
    private int healthTicks, restorationTicks, decayTicks;

    /**
     * Creates a new {@link SkillRestorationAction}.
     *
     * @param mob The mob whose skills will be restored.
     */
    public SkillRestorationAction(Mob mob) {
        super(mob, ActionType.SOFT, false, 1);
        skills = mob.getSkills();
    }

    /**
     * Restarts the affected recovery clock after a rapid prayer changes state. Other clocks retain their phase.
     *
     * @param prayer The prayer whose recovery interval changed.
     */
    public void restartRecovery(CombatPrayer prayer) {
        if (prayer == CombatPrayer.RAPID_HEAL) {
            healthTicks = 0;
        } else if (prayer == CombatPrayer.RAPID_RESTORE) {
            restorationTicks = 0;
        }
    }

    @Override
    public boolean run() {
        if (!mob.isAlive()) {
            return true;
        }
        boolean rapidHeal = mob instanceof Player &&
                ((Player) mob).getCombat().getPrayers().isActive(CombatPrayer.RAPID_HEAL);
        boolean rapidRestore = mob instanceof Player &&
                ((Player) mob).getCombat().getPrayers().isActive(CombatPrayer.RAPID_RESTORE);
        boolean heal = ++healthTicks >= (rapidHeal ? 50 : 100);
        boolean restore = ++restorationTicks >= (rapidRestore ? 50 : 100);
        boolean decay = ++decayTicks >= 100;
        if (heal) {
            healthTicks = 0;
        }
        if (restore) {
            restorationTicks = 0;
        }
        if (decay) {
            decayTicks = 0;
        }
        if (!heal && !restore && !decay) {
            return false;
        }
        boolean done = true;
        for (Skill nextSkill : skills) {
            if (nextSkill.getId() == Skill.PRAYER) {
                continue;
            }

            int level = nextSkill.getLevel();
            int staticLevel = nextSkill.getStaticLevel();

            if (level != staticLevel) {
                if (level < staticLevel && (nextSkill.getId() == Skill.HITPOINTS ? heal : restore)) {
                    nextSkill.adjustLevel(1);
                } else if (level > staticLevel && decay && mob instanceof Player) {
                    nextSkill.setLevel(level - 1);
                }
                done &= nextSkill.getLevel() == staticLevel;
            }
        }
        return done && !rapidHeal && !rapidRestore;
    }
}
