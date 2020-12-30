package io.luna.game.event.impl;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;

/**
 * The result of a player attempting to cast a spell onto an npc.
 *
 * @author searledan
 * @see io.luna.net.msg.in.CastOnNpcMessageReader
 */
public class CastOnNpcEvent extends PlayerEvent {

    /**
     * The spell identifier.
     */
    private final int spellId;

    /**
     * The spell target.
     */
    private final Npc target;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param caster  The player casting a spell.
     * @param spellId The id of the spell being casted.
     * @param target  The targeted npc.
     */
    public CastOnNpcEvent(Player caster, int spellId, Npc target) {
        super(caster);
        this.spellId = spellId;
        this.target = target;
    }

    /**
     * @return The spell identifier.
     */
    public int getIdOfSpell() {
        return spellId;
    }

    /**
     * @return The spell target.
     */
    public Npc getTarget() {
        return target;
    }
}
