package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * The result of a player attempting to cast a spell onto another player.
 *
 * @see io.luna.net.msg.in.CastOnPlayerMessageReader
 */
public class CastOnPlayerEvent extends PlayerEvent {

    private final int spellId;
    private final Player target;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param caster  The player casting a spell.
     * @param spellId The id of the spell being casted.
     * @param target  The targeted player.
     */
    public CastOnPlayerEvent(Player caster, int spellId, Player target) {
        super(caster);
        this.spellId = spellId;
        this.target = target;
    }

    public int getIdOfSpell() {
        return spellId;
    }

    public Player getTarget() {
        return target;
    }
}
