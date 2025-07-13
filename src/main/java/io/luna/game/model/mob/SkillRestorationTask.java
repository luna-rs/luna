package io.luna.game.model.mob;

import io.luna.game.task.Task;

/**
 * A task that will restore boosted and depleted skills every minute.
 *
 * @author lare96
 */
public final class SkillRestorationTask extends Task {

    /**
     * The skill set to restore.
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
            int level = nextSkill.getLevel();
            int staticLevel = nextSkill.getStaticLevel();
            if (nextSkill.getId() == Skill.PRAYER) {
                continue;
            }
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
