package io.luna.game.model;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.out.NpcUpdateMessageWriter;
import io.luna.net.msg.out.PlayerUpdateMessageWriter;
import io.luna.net.msg.out.RegionChangeMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

/**
 * Synchronizes all of the {@link Player}s and {@link Npc}s with the {@link World} through the updating protocol. The entire
 * process except for pre-synchronization is done in parallel, effectively utilizing as much of the host computer's CPU as
 * possible for maximum performance.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WorldSynchronizer {

    /**
     * A {@code Runnable} implementation that executes some sort of logic while handling thread-safety for a {@link
     * MobileEntity}, {@code Exception}s, as well as synchronization barriers.
     */
    private abstract class SynchronizationTask implements Runnable {

        /**
         * The {@link MobileEntity} to synchronize over.
         */
        private final MobileEntity entity;

        /**
         * Creates a new {@link SynchronizationTask}.
         *
         * @param entity The {@link MobileEntity} to synchronize over.
         */
        public SynchronizationTask(MobileEntity entity) {
            this.entity = entity;
        }

        @Override
        public void run() {
            synchronized (entity) {
                try {
                    execute();
                } catch (Exception e) {
                    LOGGER.catching(e);
                    remove();
                } finally {
                    synchronizer.arriveAndDeregister();
                }
            }
        }

        /**
         * The logic to execute within this task.
         */
        public abstract void execute();

        /**
         * Removes the {@code entity} if an {@code Exception} is thrown.
         */
        private void remove() {
            if (entity.type() == EntityType.PLAYER) {
                Player player = (Player) entity;
                player.logout();
            } else if (entity.type() == EntityType.NPC) {
                world.getNpcs().remove(entity.getIndex());
            } else {
                throw new IllegalStateException("should never reach here");
            }
        }
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The {@link World} instance.
     */
    private final World world;

    /**
     * A synchronization barrier that will ensure the main game thread waits for the {@code updateExecutor} threads to finish
     * executing {@link SynchronizationTask}s before proceeding.
     */
    private final Phaser synchronizer = new Phaser(1);

    /**
     * An {@link ExecutorService} that will execute {@link SynchronizationTask}s in parallel.
     */
    private final ExecutorService updateExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder().setNameFormat("WorldSynchronizerThread").build());

    /**
     * Creates a new {@link WorldSynchronizer}.
     *
     * @param world The {@link World} instance.
     */
    public WorldSynchronizer(World world) {
        this.world = world;
    }

    /**
     * Pre-synchronization, update the walking queue and perform miscellaneous processing that requires cyclic execution.
     * This is <strong>generally</strong> not safe to do in parallel.
     */
    public void preSynchronize() {
        world.getPlayers().forEach(it -> {
            try {
                it.getWalkingQueue().process();
                it.getSession().dequeue();

                if (it.getLastRegion() == null || it.needsRegionUpdate()) {
                    it.setRegionChanged(true);
                    it.setLastRegion(it.getPosition());

                    it.queue(new RegionChangeMessageWriter());
                }
            } catch (Exception e) {
                it.logout();
                LOGGER.catching(e);
            }
        });
    }

    /**
     * Synchronization, send the {@link Player} and {@link Npc} updating messages for all online {@code Player}s in
     * parallel.
     */
    public void synchronize() {
        synchronizer.bulkRegister(world.getPlayers().size());
        world.getPlayers().forEach(it -> updateExecutor.execute(new SynchronizationTask(it) {
            @Override
            public void execute() {
                it.queue(new NpcUpdateMessageWriter());
                it.queue(new PlayerUpdateMessageWriter());
            }
        }));
        synchronizer.arriveAndAwaitAdvance();
    }

    /**
     * Post-synchronization, clear various flags. This can be done safely in parallel.
     */
    public void postSynchronize() {
        synchronizer.bulkRegister(world.getPlayers().size());
        world.getPlayers().forEach(it -> updateExecutor.execute(new SynchronizationTask(it) {
            @Override
            public void execute() {
                it.clearFlags();
                it.setCachedBlock(null);
                it.setRegionChanged(false);
            }
        }));
        synchronizer.arriveAndAwaitAdvance();

        synchronizer.bulkRegister(world.getNpcs().size());
        world.getNpcs().forEach(it -> updateExecutor.execute(new SynchronizationTask(it) {
            @Override
            public void execute() {
                it.clearFlags();
            }
        }));
        synchronizer.arriveAndAwaitAdvance();
    }
}
