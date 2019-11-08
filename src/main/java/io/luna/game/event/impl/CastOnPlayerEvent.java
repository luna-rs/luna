package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

public class CastOnPlayerEvent extends PlayerEvent {

    private final int spellCastedId;
    private final int targetPlayerId;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr            The player casting a spell.
     * @param spellCastedId  The id of the spell being casted.
     * @param targetPlayerId The id of the targeted player.
     */
    public CastOnPlayerEvent(Player plr, int spellCastedId, int targetPlayerId) {
        super(plr);
        this.spellCastedId = spellCastedId;
        this.targetPlayerId = targetPlayerId;
    }

    public int getIdOfSpell() {
        return spellCastedId;
    }

    public int getIdOfTargetPlayer() {
        return targetPlayerId;
    }
}
