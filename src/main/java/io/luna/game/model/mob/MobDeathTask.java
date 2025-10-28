package io.luna.game.model.mob;

import api.combat.death.DeathHookHandler;
import io.luna.game.model.EntityType;
import io.luna.game.task.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link Task} that handles the death process for a {@link Mob}. Forwards events to kotlin event listeners
 *
 * @author lare96
 */
public final class MobDeathTask extends Task {

    /**
     * An enumerated type that represents the different death stages.
     */
    public enum DeathStage {

        /**
         * The part of the death stage when the mob's health is reduced to 0.
         */
        PRE_DEATH,

        /**
         * The main death stage where typically, items are dropped for the death source.
         */
        DEATH,

        /**
         * The last death stage where the mob is usually respawned.
         */
        POST_DEATH
    }

    /**
     * An asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The mob that died.
     */
    private final Mob victim;

    /**
     * The source of death.
     */
    private Mob source;

    /**
     * The amount of times the task has executed.
     */
    private int currentLoop;

    /**
     * Creates a new {@link MobDeathTask}.
     */
    public MobDeathTask(Mob victim, Mob source) {
        super(true, 1);
        this.victim = victim;
        this.source = source;
    }

    @Override
    protected boolean onSchedule() {
        victim.getWalking().clear();
        return true;
    }

    @Override
    public void execute() {
        try {
            if (currentLoop == 0) {
                DeathHookHandler.INSTANCE.onDeath(victim, source, DeathStage.PRE_DEATH);
            } else if (currentLoop == 4) {
                DeathHookHandler.INSTANCE.onDeath(victim, source, DeathStage.DEATH);
            } else if (currentLoop >= 5) {
                DeathHookHandler.INSTANCE.onDeath(victim, source, DeathStage.POST_DEATH);
                cancel();
            }
        } finally {
            currentLoop++;
        }
    }

    @Override
    public final void onException(Exception e) {
        // If any errors occur during death, remove mob.
        var world = victim.getWorld();
        if (victim.getType() == EntityType.PLAYER) {
            victim.asPlr().forceLogout();
        } else if (victim.getType() == EntityType.NPC) {
            world.getNpcs().remove(victim.asNpc());
        }
        logger.error("Error while processing death for {}", victim, e);
    }
}