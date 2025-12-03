package io.luna.game.model.mob;

import api.combat.death.DeathHookHandler;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;
import io.luna.game.task.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link Task} responsible for orchestrating the full death lifecycle of a {@link Mob}.
 * <p>
 * The death sequence is broken into three discrete {@link DeathStage}s:
 * </p>
 *
 * <ul>
 *     <li>{@link DeathStage#PRE_DEATH} — fired immediately when health reaches zero (e.g., animations, freezes).</li>
 *     <li>{@link DeathStage#DEATH} — the main death event (e.g., item drops, XP handling, kill attribution).</li>
 *     <li>{@link DeathStage#POST_DEATH} — cleanup and respawn logic.</li>
 * </ul>
 *
 * <p>
 * These stages are forwarded to the Kotlin-driven {@link DeathHookHandler}, allowing scripts to implement custom
 * death behavior without modifying core engine code. The task runs synchronously on the game tick loop and self-cancels
 * when the final stage completes.
 * </p>
 * @author lare96
 */
public final class MobDeathTask extends Task {

    /**
     * Represents the progression of a mob's scripted death.
     */
    public enum DeathStage {

        /**
         * The initial stage triggered as soon as the mob's hitpoints reach zero.
         */
        PRE_DEATH,

        /**
         * The main death stage.
         */
        DEATH,

        /**
         * The final stage of the death cycle.
         */
        POST_DEATH
    }

    /**
     * A logger for asynchronous death errors.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The mob whose death is being processed.
     */
    private final Mob victim;

    /**
     * The entity responsible for the kill, or {@code null} if unknown.
     */
    private final Mob source;

    /**
     * Internal tick counter controlling which stage executes.
     */
    private int currentLoop;

    /**
     * Creates a new {@link MobDeathTask}.
     *
     * @param victim The mob being killed.
     * @param source The killer, or {@code null} if the cause is environmental or unknown.
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

    /**
     * Executes the staged death sequence.
     *
     * <p>
     * The timeline:
     * </p>
     *
     * <ul>
     *     <li>Tick 0 -> {@code PRE_DEATH}</li>
     *     <li>Tick 4 -> {@code DEATH}</li>
     *     <li>Tick 5+ -> {@code POST_DEATH}, then the task cancels itself.</li>
     * </ul>
     */
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
    public void onException(Exception e) {
        World world = victim.getWorld();
        if (victim.getType() == EntityType.PLAYER) {
            victim.asPlr().forceLogout();
        } else if (victim.getType() == EntityType.NPC) {
            world.getNpcs().remove(victim.asNpc());
        }
        logger.error("Error while processing death for {}.", victim, e);
    }
}
