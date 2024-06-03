package io.luna.game.event.impl;

import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.ControllableEvent;
import io.luna.game.model.object.GameObject;
import io.luna.net.msg.in.MagicOnNpcMessageReader;
import io.luna.net.msg.in.MagicOnPlayerMessageReader;

/**
 * A cast spell event. Not intended for interception.
 *
 * @author lare96
 */
public class UseSpellEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The result of a player attempting to cast a spell onto an npc.
     *
     * @author searledan
     * @see MagicOnNpcMessageReader
     */
    public static final class CastOnNpcEvent extends UseSpellEvent {

        /**
         * The spell target.
         */
        private final Npc target;

        /**
         * Creates a new {@link PlayerEvent}.
         *
         * @param caster The player casting a spell.
         * @param spellId The id of the spell being casted.
         * @param target The targeted npc.
         */
        public CastOnNpcEvent(Player caster, int spellId, Npc target) {
            super(caster, spellId);
            this.target = target;
        }

        /**
         * @return The spell target.
         */
        public Npc getTarget() {
            return target;
        }
    }

    /**
     * The result of a player attempting to cast a spell onto another player.
     *
     * @author notjuanortiz
     * @see MagicOnPlayerMessageReader
     */
    public static final class CastOnPlayerEvent extends UseSpellEvent {

        /**
         * The spell target.
         */
        private final Player target;

        /**
         * Creates a new {@link PlayerEvent}.
         *
         * @param caster The player casting a spell.
         * @param spellId The id of the spell being casted.
         * @param target The targeted player.
         */
        public CastOnPlayerEvent(Player caster, int spellId, Player target) {
            super(caster, spellId);
            this.target = target;
        }

        /**
         * @return The spell target.
         */
        public Player getTarget() {
            return target;
        }
    }

    /**
     * An event implementation for casting a spell on an {@link Item}.
     *
     * @author lare96
     */
    public static final class CastOnItemEvent extends UseSpellEvent {

        /**
         * The target interface identifier.
         */
        private final int targetItemInterface;

        /**
         * The target item index.
         */
        private final int targetItemIndex;

        /**
         * The target item identifier.
         */
        private final int targetItemId;

        /**
         * Creates a new {@link CastOnItemEvent}.
         *
         * @param plr The player.
         * @param spellId The spell identifier.
         * @param targetItemInterface The target interface identifier.
         * @param targetItemIndex The target item index.
         * @param targetItemId The target item identifier.
         */
        public CastOnItemEvent(Player plr, int spellId, int targetItemInterface, int targetItemIndex, int targetItemId) {
            super(plr, spellId);
            this.targetItemInterface = targetItemInterface;
            this.targetItemIndex = targetItemIndex;
            this.targetItemId = targetItemId;
        }

        /**
         * @return The target interface identifier.
         */
        public int getTargetItemInterface() {
            return targetItemInterface;
        }

        /**
         * @return The target item index.
         */
        public int getTargetItemIndex() {
            return targetItemIndex;
        }

        /**
         * @return The target item identifier.
         */
        public int getTargetItemId() {
            return targetItemId;
        }
    }

    /**
     * An event implementation for casting a spell on a {@link GroundItem}.
     *
     * @author lare96
     */
    public static final class CastOnGroundItemEvent extends UseSpellEvent {

        /**
         * The target ground item.
         */
        private final GroundItem target;

        /**
         * Creates a new {@link CastOnGroundItemEvent}.
         *
         * @param plr The player.
         * @param spellId The spell identifier.
         * @param target The target ground item.
         */
        public CastOnGroundItemEvent(Player plr, int spellId, GroundItem target) {
            super(plr, spellId);
            this.target = target;
        }

        /**
         * @return The target ground item.
         */
        public GroundItem getTarget() {
            return target;
        }
    }

    /**
     * An event implementation for casting a spell on a {@link GameObject}.
     *
     * @author lare96
     */
    public static final class CastOnObjectEvent extends UseSpellEvent {

        /**
         * The target object.
         */
        private final GameObject target;

        /**
         * Creates a new {@link CastOnGroundItemEvent}.
         *
         * @param plr The player.
         * @param spellId The spell identifier.
         * @param target The target object.
         */
        public CastOnObjectEvent(Player plr, int spellId, GameObject target) {
            super(plr, spellId);
            this.target = target;
        }

        /**
         * @return The target object.
         */
        public GameObject getTarget() {
            return target;
        }
    }

    /**
     * The spell identifier.
     */
    private final int spellId;

    /**
     * Creates a new {@link UseSpellEvent}.
     *
     * @param plr The player.
     * @param spellId The spell identifier.
     */
    private UseSpellEvent(Player plr, int spellId) {
        super(plr);
        this.spellId = spellId;
    }

    /**
     * @return The spell identifier.
     */
    public int getSpellId() {
        return spellId;
    }
}
