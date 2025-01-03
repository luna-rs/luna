package io.luna.game.event.impl;

import io.luna.game.model.Entity;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
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
     * The result of a player attempting to cast a spell onto a npc.
     *
     * @author searledan
     * @see MagicOnNpcMessageReader
     */
    public static final class MagicOnNpcEvent extends UseSpellEvent implements InteractableEvent {

        /**
         * The spell target.
         */
        private final Npc targetNpc;

        /**
         * Creates a new {@link PlayerEvent}.
         *
         * @param caster The player casting a spell.
         * @param spellId The id of the spell being cast.
         * @param targetNpc The targeted npc.
         */
        public MagicOnNpcEvent(Player caster, int spellId, Npc targetNpc) {
            super(caster, spellId);
            this.targetNpc = targetNpc;
        }

        @Override
        public Entity target() {
            return targetNpc;
        }

        @Override
        public int distance() {
            return 10;
        }

        /**
         * @return The spell target.
         */
        public Npc getTargetNpc() {
            return targetNpc;
        }
    }

    /**
     * The result of a player attempting to cast a spell onto another player.
     *
     * @author notjuanortiz
     * @see MagicOnPlayerMessageReader
     */
    public static final class MagicOnPlayerEvent extends UseSpellEvent implements InteractableEvent {

        /**
         * The spell target.
         */
        private final Player targetPlr;

        /**
         * Creates a new {@link PlayerEvent}.
         *
         * @param caster The player casting a spell.
         * @param spellId The id of the spell being casted.
         * @param targetPlr The targeted player.
         */
        public MagicOnPlayerEvent(Player caster, int spellId, Player targetPlr) {
            super(caster, spellId);
            this.targetPlr = targetPlr;
        }

        @Override
        public Entity target() {
            return targetPlr;
        }

        @Override
        public int distance() {
            return 10;
        }

        /**
         * @return The spell target.
         */
        public Player getTargetPlr() {
            return targetPlr;
        }
    }

    /**
     * An event implementation for casting a spell on an {@link Item}.
     *
     * @author lare96
     */
    public static final class MagicOnItemEvent extends UseSpellEvent {

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
         * Creates a new {@link MagicOnItemEvent}.
         *
         * @param plr The player.
         * @param spellId The spell identifier.
         * @param targetItemInterface The target interface identifier.
         * @param targetItemIndex The target item index.
         * @param targetItemId The target item identifier.
         */
        public MagicOnItemEvent(Player plr, int spellId, int targetItemInterface, int targetItemIndex, int targetItemId) {
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
    public static final class MagicOnGroundItemEvent extends UseSpellEvent implements InteractableEvent {

        /**
         * The target ground item.
         */
        private final GroundItem targetItem;

        /**
         * Creates a new {@link MagicOnGroundItemEvent}.
         *
         * @param plr The player.
         * @param spellId The spell identifier.
         * @param targetItem The target ground item.
         */
        public MagicOnGroundItemEvent(Player plr, int spellId, GroundItem targetItem) {
            super(plr, spellId);
            this.targetItem = targetItem;
        }

        @Override
        public Entity target() {
            return targetItem;
        }

        @Override
        public int distance() {
            return 10;
        }

        /**
         * @return The target ground item.
         */
        public GroundItem getTargetItem() {
            return targetItem;
        }
    }

    /**
     * An event implementation for casting a spell on a {@link GameObject}.
     *
     * @author lare96
     */
    public static final class MagicOnObjectEvent extends UseSpellEvent implements InteractableEvent{

        /**
         * The target object.
         */
        private final GameObject targetObject;

        /**
         * Creates a new {@link MagicOnGroundItemEvent}.
         *
         * @param plr The player.
         * @param spellId The spell identifier.
         * @param targetObject The target object.
         */
        public MagicOnObjectEvent(Player plr, int spellId, GameObject targetObject) {
            super(plr, spellId);
            this.targetObject = targetObject;
        }

        @Override
        public Entity target() {
            return targetObject;
        }

        @Override
        public int distance() {
            return 10;
        }

        /**
         * @return The target object.
         */
        public GameObject getTargetObject() {
            return targetObject;
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
