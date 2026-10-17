package game.content.sailing

import io.luna.game.model.mob.Player

/**
 * The search a ship's crew makes before letting a player board, which turns away those carrying what isn't allowed.
 *
 * @author Hydrozoa
 */
enum class ShipSearch {

    /**
     * Nobody is searched.
     */
    NONE {
        override fun turnsAway(plr: Player) = false
    },

    /**
     * The customs officers look for Karamjan rum, which they confiscate.
     */
    RUM {
        override fun turnsAway(plr: Player) = plr.inventory.computeAmountForId(Sailing.KARAMJAN_RUM) > 0
    },

    /**
     * The Monks of Entrana look for weapons and armour, see [Entrana].
     */
    ENTRANA {
        override fun turnsAway(plr: Player) = Entrana.isCarryingForbidden(plr)
    };

    /**
     * Returns `true` if the search finds something on [plr] that keeps them off the ship.
     */
    abstract fun turnsAway(plr: Player): Boolean
}
