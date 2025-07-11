package world.player.skill.mining

import api.predef.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An enumerated type representing all pickaxes that can be used to mine [Ore]s.
 */
enum class Pickaxe(val level: Int,
                   val itemId: Int,
                   val animation: Animation,
                   val speed: Int) {
    BRONZE(level = 1,
           itemId = 1265,
           animation = Animation(625),
           speed = 8),
    IRON(level = 1,
         itemId = 1267,
         animation = Animation(626),
         speed = 7),
    STEEL(level = 5,
          itemId = 1269,
          animation = Animation(627),
          speed = 6),
    MITHRIL(level = 20,
            itemId = 1273,
            animation = Animation(629),
            speed = 5),
    ADAMANT(level = 30,
            itemId = 1271,
            animation = Animation(628),
            speed = 4),
    RUNE(level = 40,
         itemId = 1275,
         animation = Animation(624),
         speed = 3);


    companion object {

        /**
         * Pickaxe ID -> Pickaxe instance.
         */
        val ID_TO_PICKAXE = values().associateBy { it.itemId }

        /**
         * Determines which pickaxe to use (based on equipment and inventory).
         */
        fun computePickType(plr: Player): Pickaxe? {
            var pick: Pickaxe? = null
            val weapon = plr.equipment[Equipment.WEAPON]
            if (weapon != null) { // See if weapon is a pickaxe.
                val pickFound = ID_TO_PICKAXE[weapon.id]
                if (pickFound != null && plr.mining.level > pickFound.level) {
                    pick = pickFound
                }
            }

            for (item in plr.inventory) {
                if (item == null) {
                    continue
                }
                val newPick = ID_TO_PICKAXE[item.id]
                if (newPick != null &&
                    plr.mining.level >= newPick.level &&
                    (pick == null || newPick.level > pick.level)) {
                    pick = newPick
                }
            }
            return pick
        }

        /**
         * Does the player have [pick]?
         */
        fun hasPick(plr: Player, pick: Pickaxe) =
            plr.equipment.nonNullGet(Equipment.WEAPON).map { it.id == pick.itemId }.orElse(false) ||
                    plr.inventory.contains(pick.itemId)
    }
}