package game.item.teleport

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * The Camulet from Enakhra's Lament, which teleports the player back to Enakhra's Temple and is recharged with
 * camel dung.
 *
 * @author TheLining
 */
object Camulet {

    /**
     * The Camulet item id.
     */
    const val CAMULET = 6707

    /**
     * The bucket of Ugthanki dung item id.
     */
    const val UGTHANKI_DUNG = 4601

    /**
     * The empty bucket item id.
     */
    const val BUCKET = 1925

    /**
     * The most charges a Camulet holds, and what a bucket of dung recharges it to.
     */
    const val MAX_CHARGES = 4

    /**
     * The tile in Lazim's room, on the top floor of Enakhra's Temple, that the teleport lands around.
     */
    val TEMPLE = Position(3105, 9315, 2)

    /**
     * How far from [TEMPLE] the teleport may land.
     */
    const val TEMPLE_RADIUS = 2

    /**
     * The deepest Wilderness level the Camulet teleports from.
     */
    const val MAX_WILDERNESS_LEVEL = 20

    /**
     * The player's Camulet charges. They are kept on the player, not the item, so every Camulet the player owns
     * shares them. A new player starts with the full charges the quest gives.
     *
     * TODO Enakhra's Lament: set the charges when the quest hands over the Camulet.
     */
    var Player.camuletCharges by Attr.int { MAX_CHARGES }.persist("camulet_charges")
}
