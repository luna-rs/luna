package io.luna.game.model.mob;

import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.event.impl.DeathEvent;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Animation.AnimationPriority;
import io.luna.game.model.mob.Player.SkullIcon;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.task.Task;
import io.luna.net.msg.out.WalkableInterfaceMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * A {@link Task} that handles the death process for a {@link Mob}.
 *
 * @author lare96
 */
public abstract class MobDeathTask<T extends Mob> extends Task {

    /**
     * A death task implementation for {@link Player}s.
     */
    public static final class PlayerDeathTask extends MobDeathTask<Player> {

        /**
         * Creates a new {@link PlayerDeathTask}.
         */
        public PlayerDeathTask(Player player) {
            super(player);
        }

        @Override
        public void handleDeath(DeathStage stage, Mob source) {
            switch (stage) {
                case PRE_DEATH:
                    mob.sendMessage("Oh dear, you are dead!");
                    mob.animation(new Animation(2304, AnimationPriority.HIGH));
                    mob.getInterfaces().close();
                    mob.getActions().interrupt();
                    break;
                case DEATH:
                    mob.getPlugins().post(new DeathEvent(mob, source));
                    break;
                case POST_DEATH:
                    mob.move(Luna.settings().game().startingPosition());
                    mob.animation(new Animation(65535));
                    mob.queue(new WalkableInterfaceMessageWriter(65535));
                    mob.getSkills().resetAll();
                    mob.setSkullIcon(SkullIcon.NONE);
                    // TODO Reset all prayers. https://github.com/luna-rs/luna/issues/369
                    mob.getFlags().flag(UpdateFlag.APPEARANCE);
                    break;
            }
        }
    }

    /**
     * A death task implementation for {@link Npc}s.
     */
    public static final class NpcDeathTask extends MobDeathTask<Npc> {

        /**
         * Creates a new {@link NpcDeathTask}.
         */
        public NpcDeathTask(Npc npc) {
            super(npc);
        }

        @Override
        public void handleDeath(DeathStage stage, Mob source) {
            switch (stage) {
                case PRE_DEATH:
                    mob.getActions().interrupt();
                    mob.getCombatDefinition().ifPresent(def ->
                            mob.animation(new Animation(def.getDeathAnimation(), AnimationPriority.HIGH)));
                    break;
                case DEATH:
                    mob.getPlugins().post(new DeathEvent(mob, source));
                    world.getNpcs().remove(mob);
                    break;
                case POST_DEATH:
                    if(mob.isRespawn()) {
                        var defOptional = mob.getCombatDefinition().
                                filter(def -> def.getRespawnTime() != -1).
                                map(NpcCombatDefinition::getRespawnTime);
                        defOptional.ifPresent(delay -> world.schedule(new Task(delay) {
                            @Override
                            protected void execute() {
                                cancel();

                                // TODO Clone NPC on death properly.
                                var respawnedNpc = new Npc(mob.getContext(), mob.getBaseId(), mob.getBasePosition()).setRespawning();
                                world.getNpcs().add(respawnedNpc);
                            }
                        }));
                    }
                    break;
            }
        }
    }

    /**
     * An enumerated type that represents the different death stages.
     */
    private enum DeathStage {

        /**
         * Pre-death, right after the mob's health is reduced to 0. The death source is computed at this point.
         */
        PRE_DEATH,

        /**
         * The main death stage where items are dropped for the death source.
         */
        DEATH,

        /**
         * Post-death, the last part of the death process where the mob is respawned.
         */
        POST_DEATH
    }

    /**
     * An asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The context.
     */
    final LunaContext ctx;

    /**
     * The world.
     */
    final World world;

    /**
     * The mob that died.
     */
    final T mob;

    /**
     * The amount of times the task has executed.
     */
    private int currentLoop;

    /**
     * The source of death.
     */
    private Mob source;

    /**
     * Creates a new {@link MobDeathTask}.
     */
    public MobDeathTask(T mob) {
        super(1);
        this.mob = mob;
        ctx = mob.getContext();
        world = mob.getWorld();
    }

    /**
     * Handle a death stage.
     *
     * @param stage The stage of death.
     * @param source The source of death.
     */
    public abstract void handleDeath(DeathStage stage, Mob source);

    @Override
    protected boolean onSchedule() {
        mob.skill(Skill.HITPOINTS).setLevel(0);
        mob.getWalking().clear();
        mob.getActions().interrupt();
        // TODO Calculate the source of death.
        // source = ???
        return true;
    }

    @Override
    public final void execute() {
        if (currentLoop == 0) {
            handleDeath(DeathStage.PRE_DEATH, source);
        } else if (currentLoop == 4) {
            handleDeath(DeathStage.DEATH, source);
        } else if (currentLoop == 5) {
            handleDeath(DeathStage.POST_DEATH, source);
            cancel();
        }
        currentLoop++;
    }

    @Override
    public final void onException(Exception e) {
        // If any errors occur during death, remove mob.
        var world = mob.getWorld();
        if (mob.getType() == EntityType.PLAYER) {
            mob.asPlr().getClient().disconnect();
        } else if (mob.getType() == EntityType.NPC) {
            world.getNpcs().remove(mob.asNpc());
        }
        logger.error(new ParameterizedMessage("Error while processing death for {}", mob), e);
    }
}