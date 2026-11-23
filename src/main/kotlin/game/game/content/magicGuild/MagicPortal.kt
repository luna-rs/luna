package game.content.magicGuild

import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * The three magic portals on the top floor of the Magic Guild.
 *
 * @param id The id of the portal.
 * @param place What the destination is called in the arrival message.
 * @param destination Where the portal sends the player.
 * @author TheLining
 */
enum class MagicPortal(val id: Int, private val place: String, private val destination: Position) {

    /**
     * The east portal, to the Wizards' Tower south of Draynor Village.
     */
    WIZARDS_TOWER(2156, "the Wizards' tower", Position(3109, 3159)),

    /**
     * The south portal, to the Dark Wizards' Tower north of the Crafting Guild.
     */
    DARK_WIZARDS_TOWER(2157, "the Dark Wizards' tower", Position(2907, 3333)),

    /**
     * The west portal, to Thormac the Sorcerer's house west of Catherby.
     */
    THORMACS_HOUSE(2158, "Thormac the Sorcerer's house", Position(2703, 3406));

    /**
     * Sends [plr] through this portal.
     */
    fun enter(plr: Player) {
        plr.sendMessage("You enter the magic portal...")
        plr.sendMessage("You teleport to $place.")
        plr.move(destination)
    }
}
