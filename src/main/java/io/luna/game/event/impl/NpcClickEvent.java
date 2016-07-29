package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Npc;

import java.util.Arrays;
import java.util.Objects;

/**
 * An event implementation sent when a player clicks any npc index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class NpcClickEvent extends Event {

    /**
     * An event implementation sent when a player clicks an npc's first index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class NpcFirstClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcFirstClickEvent(Npc npc) {
            super(npc);
        }
    }

    /**
     * An event implementation sent when a player clicks an npc's second index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class NpcSecondClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcSecondClickEvent}.
         */
        public NpcSecondClickEvent(Npc npc) {
            super(npc);
        }
    }

    /**
     * An event implementation sent when a player clicks an npc's third index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class NpcThirdClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcThirdClickEvent(Npc npc) {
            super(npc);
        }
    }

    /**
     * An event implementation sent when a player clicks an npc's fourth index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class NpcFourthClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcFourthClickEvent(Npc npc) {
            super(npc);
        }
    }

    /**
     * An event implementation sent when a player clicks an npc's fifth index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class NpcFifthClickEvent extends NpcClickEvent {

        /**
         * Creates a new {@link NpcClickEvent}.
         */
        public NpcFifthClickEvent(Npc npc) {
            super(npc);
        }
    }

    /**
     * The instance of the npc.
     */
    private final Npc npc;

    /**
     * Creates a new {@link NpcClickEvent}.
     *
     * @param npc The instance of the npc.
     */
    private NpcClickEvent(Npc npc) {
        this.npc = npc;
    }

    @Override
    public final boolean matches(Object... args) {
        return Arrays.stream(args).anyMatch(it -> Objects.equals(it, npc.getId()));
    }

    /**
     * @return The instance of the npc.
     */
    public Npc getNpc() {
        return npc;
    }
}
