package game.item.consumable.empty

import game.player.Sound
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The containers that can be tipped out with the "Empty" inventory option, and what is left afterwards.
 *
 * @property container The emptied container.
 * @property noun What the message calls the container.
 */
enum class EmptyVessel(val container: Int, val noun: String) {
    VIAL(container = 229, noun = "vial"),
    BUCKET(container = 1925, noun = "bucket"),
    JUG(container = 1935, noun = "jug"),
    BOWL(container = 1923, noun = "bowl"),
    POT(container = 1931, noun = "pot"),
    CUP(container = 1980, noun = "cup"),
    GLASS(container = 2026, noun = "glass");

    companion object {
        private const val OPTION = "Empty"

        private val BUCKETS = setOf("full bucket", "apple mush", "compost", "super compost", "ugthanki dung")
        private val BOWLS = setOf("nettle-water", "nettle tea", "burnt egg", "burnt onion", "burnt mushroom")
        private val POTS = setOf("pot of flour", "pot of cornflour", "pot of vinegar")
        private val CUPS = setOf("cup of water", "cup of hot water", "ruined herb tea", "herb tea mix")
        private val VIALS = setOf("vial of water", "unfinished potion", "unfinished potion.", "weapon poison",
            "weapon poison(+)", "weapon poison(s)", "coconut milk", "plant cure", "cat antipoison",
            "black mushroom ink")
        private val DOSES = Regex(""".*\([1-4]\)$""")

        /**
         * The highest id of a cup of nettle tea; later cups of tea are porcelain and keep their own cup.
         */
        private const val LAST_NETTLE_CUP = 4246

        /**
         * Resolves what the item with [id] is held in, or `null` if its "Empty" option belongs to other content.
         * Dosed drinks are vials unless they are a cup of Guthix rest.
         */
        fun forItem(id: Int): EmptyVessel? {
            val name = ItemDefinition.ALL.get(id).map { it.name.lowercase() }.orElse(null) ?: return null
            return when {
                name.startsWith("bucket of ") || name in BUCKETS -> BUCKET
                name.startsWith("jug of ") -> JUG
                name.startsWith("bowl of ") || name in BOWLS -> BOWL
                name in POTS -> POT
                name == "cup of tea" -> CUP.takeIf { id <= LAST_NETTLE_CUP }
                name in CUPS || name.startsWith("guthix rest") -> CUP
                name == "odd cocktail" -> GLASS
                name in VIALS || DOSES.matches(name) -> VIAL
                else -> null
            }
        }

        /**
         * Tips out the unnoted item in [index] if its option in menu slot [option] is "Empty", leaving the container
         * in the same slot.
         */
        fun empty(plr: Player, index: Int, id: Int, option: Int): Boolean {
            val def = ItemDefinition.ALL.get(id).orElse(null) ?: return false
            if (!def.hasInventoryAction(option, OPTION)) return false
            val vessel = forItem(id) ?: return false
            val item = plr.inventory[index] ?: return false
            if (item.id != id || item.amount != 1 || item.isDynamic) return false
            plr.inventory[index] = Item(vessel.container)
            plr.playSound(Sound.LIQUID)
            plr.sendMessage("You empty the contents of the ${vessel.noun} on the floor.")
            return true
        }
    }
}
