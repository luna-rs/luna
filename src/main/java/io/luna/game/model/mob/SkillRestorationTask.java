package io.luna.game.model.mob;

import io.luna.game.task.Task;

/**
 * Periodic task that restores temporarily modified (boosted or drained) skill levels back toward their static
 * (experience-based) levels.
 *
 * <h2>Restoration behavior</h2>
 * <ul>
 *     <li>Runs on a fixed schedule (configured by the {@link Task} period passed to {@link #SkillRestorationTask(SkillSet)}).</li>
 *     <li>For each skill except {@link Skill#PRAYER}, if the dynamic level differs from the static level, the dynamic
 *     level is moved by 1 toward the static level.</li>
 *     <li>If all eligible skills match their static levels, the task cancels itself.</li>
 * </ul>
 *
 * <h2>SkillSet state flag</h2>
 * This task toggles {@link SkillSet#isRestoring()} while scheduled so the {@link Skill}/{@link SkillSet} code can avoid
 * re-scheduling multiple restoration tasks at the same time.
 *
 * @author lare96
 */
public final class SkillRestorationTask extends Task {

    /**
     * The skill set being restored by this task.
     */
    private final SkillSet skills;

    /**
     * Creates a new {@link SkillRestorationTask}.
     *
     * @param skills The skill set to restore.
     */
    public SkillRestorationTask(SkillSet skills) {
        super(false, 100);
        this.skills = skills;
    }

    @Override
    protected boolean onSchedule() {
        skills.setRestoring(true);
        return true;
    }

    @Override
    protected void onCancel() {
        skills.setRestoring(false);
    }

    @Override
    protected void execute() {
        boolean done = true;

        for (Skill nextSkill : skills) {
            if (nextSkill.getId() == Skill.PRAYER) {
                // Prayer restoration is usually handled separately (e.g., drains over time / altar / potions).
                continue;
            }

            int level = nextSkill.getLevel();
            int staticLevel = nextSkill.getStaticLevel();

            if (level != staticLevel) {
                done = false;
                if (level < staticLevel) {
                    nextSkill.addLevels(1, false);
                } else {
                    nextSkill.removeLevels(1);
                }
            }
        }

        if (done) {
            cancel();
        }
    }
}
