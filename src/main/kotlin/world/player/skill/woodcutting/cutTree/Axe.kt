package world.player.skill.woodcutting.cutTree

import api.predef.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import world.player.skill.woodcutting.cutTree.Tree.*

/**
 * An enumerated type representing all axes that can be used to cut [Tree]s.
 */
enum class Axe(val id: Int,
               val level: Int,
               val animation: Animation,
               val chances: Map<Tree, Pair<Int, Int>>) {
    // 'chances' property based off
    BRONZE(id = 1351,
           level = 1,
           animation = Animation(879),
           chances = mapOf(
               NORMAL to (64 to 200),
               OAK to (32 to 100),
               WILLOW to (16 to 50),
               TEAK to (15 to 46),
               MAPLE to (8 to 25),
               MAHOGANY to (8 to 25),
               YEW to (4 to 12),
               MAGIC to (2 to 6)
           )),
    IRON(id = 1349,
         level = 1,
         animation = Animation(877),
         chances = mapOf(
             NORMAL to (96 to 300),
             OAK to (48 to 150),
             WILLOW to (24 to 75),
             TEAK to (23 to 70),
             MAPLE to (12 to 37),
             MAHOGANY to (12 to 25),
             YEW to (6 to 19),
             MAGIC to (3 to 9)
         )),
    STEEL(id = 1353,
          level = 6,
          animation = Animation(875),
          chances = mapOf(
              NORMAL to (128 to 400),
              OAK to (64 to 200),
              WILLOW to (32 to 100),
              TEAK to (31 to 93),
              MAPLE to (16 to 50),
              MAHOGANY to (16 to 50),
              YEW to (8 to 25),
              MAGIC to (4 to 12)
          )),
    BLACK(id = 1361,
          level = 6,
          animation = Animation(875),
          chances = mapOf(
              NORMAL to (144 to 450),
              OAK to (144 to 225),
              WILLOW to (72 to 112),
              TEAK to (36 to 102),
              MAPLE to (18 to 56),
              MAHOGANY to (18 to 54),
              YEW to (9 to 28),
              MAGIC to (5 to 13)
          )),
    MITHRIL(id = 1355,
            level = 21,
            animation = Animation(871),
            chances = mapOf(
                NORMAL to (160 to 500),
                OAK to (80 to 250),
                WILLOW to (40 to 125),
                TEAK to (39 to 117),
                MAPLE to (20 to 62),
                MAHOGANY to (20 to 63),
                YEW to (10 to 31),
                MAGIC to (5 to 15)
            )),
    ADAMANT(id = 1357,
            level = 31,
            animation = Animation(869),
            chances = mapOf(
                NORMAL to (192 to 600),
                OAK to (96 to 300),
                WILLOW to (48 to 150),
                TEAK to (47 to 140),
                MAPLE to (24 to 75),
                MAHOGANY to (25 to 75),
                YEW to (12 to 37),
                MAGIC to (6 to 18)
            )),
    RUNE(id = 1359,
         level = 41,
         animation = Animation(867),
         chances = mapOf(
             NORMAL to (224 to 700),
             OAK to (112 to 350),
             WILLOW to (56 to 175),
             TEAK to (55 to 164),
             MAPLE to (28 to 87),
             MAHOGANY to (29 to 88),
             YEW to (14 to 44),
             MAGIC to (7 to 21)
         )),
    DRAGON(id = 6739,
           level = 61,
           animation = Animation(2846),
           chances = mapOf(
               NORMAL to (240 to 750),
               OAK to (120 to 375),
               WILLOW to (60 to 187),
               TEAK to (60 to 190),
               MAPLE to (30 to 93),
               MAHOGANY to (34 to 94),
               YEW to (15 to 47),
               MAGIC to (7 to 22)
           ));

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
                    (axe == null || newAxe.level > axe.level)
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