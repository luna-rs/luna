package io.luna.game.event.impl;

import io.luna.game.model.Entity;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;

/**
 * A npc-click based event. Not intended for interception.
 *
 * @author lare96
 */
public class NpcClickEvent extends PlayerEvent implements ControllableEvent, InteractableEvent {

    /**
     * An event sent when a player clicks an npc's first index.
     *
     * @author lare96
     */
    public static final class NpcFirstClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcFirstClickEvent(Player player, Npc npc) {
            super(player, npc);
        }
    }

    /**
     * An event sent when a player clicks an npc's second index.
     *
     * @author lare96
     */
    public static final class NpcSecondClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcSecondClickEvent}.
         */
        public NpcSecondClickEvent(Player player, Npc npc) {
            super(player, npc);
        }
    }

    /**
     * An event sent when a player clicks an npc's third index.
     *
     * @author lare96
     */
    public static final class NpcThirdClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcThirdClickEvent(Player player, Npc npc) {
            super(player, npc);
        }
    }

    /**
     * An event sent when a player clicks an npc's fourth index.
     *
     * @author lare96
     */
    public static final class NpcFourthClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcFourthClickEvent(Player player, Npc npc) {
            super(player, npc);
        }
    }

    /**
     * An event sent when a player clicks an npc's fifth index.
     *
     * @author lare96
     */
    public static final class NpcFifthClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcFifthClickEvent(Player player, Npc npc) {
            super(player, npc);
        }
    }

    /**
     * The non-player character.
     */
    private final Npc targetNpc;

    /**
     * Creates a new {@link NpcClickEvent}.
     *
     * @param player The player.
     * @param targetNpc The non-player character.
     */
    private NpcClickEvent(Player player, Npc targetNpc) {
        super(player);
        this.targetNpc = targetNpc;
    }

    @Override
    public Entity target() {
        return targetNpc;
    }

    /**
     * @return The non-player character.
     */
    public Npc getTargetNpc() {
        return targetNpc;
    }
}
