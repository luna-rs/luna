package io.luna.game.model;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.luna.game.model.mobile.MobileEntityList;
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
 * A model that concurrently runs the update procedure for mobs.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WorldSynchronizer {

    /**
     * A model that applies the update procedure within a synchronization block.
     */
    private final class UpdateTask implements Runnable {

        /**
         * The player.
         */
        private final Player player;

        /**
         * Creates a new {@link UpdateTask}.
         *
         * @param player The player.
         */
        private UpdateTask(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            synchronized (player) {
                try {
                    player.queue(new NpcUpdateMessageWriter());
                    player.queue(new PlayerUpdateMessageWriter());
                } catch (Exception e) {
                    LOGGER.catching(e);
                    player.logout();
                } finally {
                    barrier.arriveAndDeregister();
                }
            }
        }
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A list of players.
     */
    private final MobileEntityList<Player> players;

    /**
     * A list of npcs.
     */
    private final MobileEntityList<Npc> npcs;

    /**
     * A synchronization barrier.
     */
    private final Phaser barrier = new Phaser(1);

    /**
     * A thread pool for parallel updating.
     */
    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder().setNameFormat("WorldSynchronizerThread").build());

    /**
     * Creates a new {@link WorldSynchronizer}.
     *
     * @param world The world.
     */
    public WorldSynchronizer(World world) {
        players = world.getPlayers();
        npcs = world.getNpcs();
    }

    /**
     * Pre-synchronization is for tick-dependant sequential processing.
     */
    public void preSynchronize() {
        for (Player player : players) {
            try {
                player.getWalkingQueue().process();
                player.getSession().dequeue();

                if (player.getLastRegion() == null || player.needsRegionUpdate()) {
                    player.setRegionChanged(true);
                    player.setLastRegion(player.getPosition());

                    player.queue(new RegionChangeMessageWriter());
                }
            } catch (Exception e) {
                player.logout();
                LOGGER.catching(e);
            }
        }

        for (Npc npc : npcs) {
            try {
                npc.getWalkingQueue().process();
            } catch (Exception e) {
                npcs.remove(npc);
                LOGGER.catching(e);
            }
        }
    }

    /**
     * Synchronization applies the update protocol in parallel.
     */
    public void synchronize() {
        barrier.bulkRegister(players.size());
        for (Player player : players) {
            service.execute(new UpdateTask(player));
        }
        barrier.arriveAndAwaitAdvance();
    }

    /**
     * Post-synchronization clears update flags
     */
    public void postSynchronize() {
        for (Player player : players) {
            player.getSession().flushQueue();
            player.clearFlags();
            player.setCachedBlock(null);
        }

        for (Npc npc : npcs) {
            npc.clearFlags();
        }
    }
}
