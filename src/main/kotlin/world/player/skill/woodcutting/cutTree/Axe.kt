package world.player.skill.woodcutting.cutTree

import api.predef.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An enumerated type representing all axes that can be used to cut [Tree]s.
 */
enum class Axe(val id: Int, val level: Int, val animation: Animation, val speed: Int, val strength: Double) {
    BRONZE(id = 1351,
           level = 1,
           animation = Animation(879),
           speed = 4,
           strength = 0.45),
    IRON(id = 1349,
         level = 1,
         animation = Animation(877),
         speed = 4,
         strength = 0.50),
    STEEL(id = 1353,
          level = 6,
          animation = Animation(875),
          speed = 4,
          strength = 0.60),
    BLACK(id = 1361,
          level = 6,
          animation = Animation(875),
          speed = 4,
          strength = 0.65),
    MITHRIL(id = 1355,
            level = 21,
            animation = Animation(871),
            speed = 4,
            strength = 0.75),
    ADAMANT(id = 1357,
            level = 31,
            animation = Animation(869),
            speed = 4,
            strength = 0.85),
    RUNE(id = 1359,
         level = 41,
         animation = Animation(867),
         speed = 4,
         strength = 0.95),
    DRAGON(id = 6739,
           level = 61,
           animation = Animation(2846),
           speed = 3,
           strength = 1.05);

    companion object {

        /**
         * Item ID -> Axe instance.
         */
        val VALUES = values().associateBy { it.id }

        /**
         * Determines which axe to use (based on equipment and inventory).
         */
        fun computeAxeType(plr: Player): Axe? {
            var axe: Axe? = null
            val weapon = plr.equipment[Equipment.WEAPON]
            if (weapon != null) { // See if weapon is an axe.
                val axeFound = VALUES[weapon.id]
                if (axeFound != null && plr.woodcutting.level >= axeFound.level) {
                    axe = axeFound
                }
            }

            for (item in plr.inventory) {
                if (item == null) {
                    continue
                }
                val newAxe = VALUES[item.id]
                if (newAxe != null &&
                    plr.woodcutting.level >= newAxe.level &&
                    (axe == null || newAxe.strength > axe.strength)
                ) {
                    axe = newAxe
                }
            }
            return axe
        }

        /**
         * Determines if the player has an axe.
         */
        fun hasAxe(plr: Player, axe: Axe) =
            plr.equipment.nonNullGet(Equipment.WEAPON).map { it.id == axe.id }.orElse(false) ||
                    plr.inventory.contains(axe.id)
    }
}