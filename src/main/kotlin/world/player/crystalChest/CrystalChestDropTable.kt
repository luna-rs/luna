package world.player.crystalChest

import api.item.dropTable.DropTable
import api.item.dropTable.DropTableHandler
import api.item.dropTable.DropTableItemList
import api.item.dropTable.RationalTable
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player

/**
 * A [DropTable] implementation for a crystal chest.
 *
 * @author lare96
 */
object CrystalChestDropTable : DropTable() {

    /**
     * The table of items always dropped.
     */
    val ALWAYS_TABLE = DropTableHandler.createList {
        noted {
            "Uncut dragonstone" x 1 chance ALWAYS
        }
    }

    /**
     * The spinach roll table.
     */
    val SPINACH_ROLL_TABLE = DropTableHandler.createList {
        noted {
            "Spinach roll" x 1 chance (1 of 4)
        }
        "Coins" x 2_000 chance (1 of 4)
    }

    /**
     * The runes table.
     */
    val RUNE_TABLE = DropTableHandler.createList {
        "Air rune" x 50 chance (1 of 10)
        "Water rune" x 50 chance (1 of 10)
        "Earth rune" x 50 chance (1 of 10)
        "Fire rune" x 50 chance (1 of 10)
        "Body rune" x 50 chance (1 of 10)
        "Mind rune" x 50 chance (1 of 10)
        "Chaos rune" x 10 chance (1 of 10)
        "Death rune" x 10 chance (1 of 10)
        "Cosmic rune" x 10 chance (1 of 10)
        "Nature rune" x 10 chance (1 of 10)
        "Law rune" x 10 chance (1 of 10)
    }

    /**
     * The gem table.
     */
    val GEM_TABLE = DropTableHandler.createList {
        noted {
            "Ruby" x 2 chance (1 of 10)
            "Diamond" x 2 chance (1 of 10)
        }
    }

    /**
     * The runite bar table.
     */
    val RUNITE_BAR_TABLE = DropTableHandler.createList {
        noted {
            "Runite bar" x 3 chance (1 of 10)
        }
    }

    /**
     * The crystal key halves table.
     */
    val CRYSTAL_KEY_TABLE = DropTableHandler.createList {
        noted {
            985 x 1 chance (1 of 25) // Crystal key half.
            987 x 1 chance (1 of 25) // Crystal key half.
        }
        "Coins" x 750 chance (1 of 13)
    }

    /**
     * The iron ore table.
     */
    val IRON_ORE_TABLE = DropTableHandler.createList {
        noted {
            "Iron ore" x 150 chance (1 of 12)
        }
    }

    /**
     * The coal table.
     */
    val COAL_TABLE = DropTableHandler.createList {
        noted {
            "Coal" x 100 chance (1 of 12)
        }
    }

    /**
     * The raw swordfish table.
     */
    val RAW_SWORDFISH_TABLE = DropTableHandler.createList {
        noted {
            "Raw swordfish" x 5 chance (1 of 16)
        }
        "Coins" x 1000 chance (1 of 16)
    }

    /**
     * The adamant sq shield table.
     */
    val ADAMANT_SQ_SHIELD_TABLE = DropTableHandler.createList {
        noted {
            "Adamant sq shield" x 1 chance (1 of 64)
        }
    }

    /**
     * The rune platelegs/plateskirt table.
     */
    val RUNE_LEGS_OR_SKIRT_TABLE: (Player?) -> DropTableItemList = {
        DropTableHandler.createList {
            noted {
                if (it == null) { // No player, we just need a list of all possible items.
                    "Rune platelegs" x 1 chance (1 of 128)
                    "Rune plateskirt" x 1 chance (1 of 128)
                } else if (it.appearance.isMale) { // Male, so drop normal legs.
                    "Rune platelegs" x 1 chance (1 of 128)
                } else if (it.appearance.isFemale) { // Female, so drop a skirt.
                    "Rune plateskirt" x 1 chance (1 of 128)
                }
            }
        }
    }

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
        if (mob is Player) {
            val itemList = ArrayList(ALWAYS_TABLE)
            val plr = mob.asPlr()

            // Determine which drop table will be rolled using a rational table.
            val nodeTable = RationalTable(mutableListOf(
                    (34 of 128) to SPINACH_ROLL_TABLE,
                    (12 of 128) to RUNE_TABLE,
                    (12 of 128) to GEM_TABLE,
                    (12 of 128) to RUNITE_BAR_TABLE,
                    (10 of 128) to CRYSTAL_KEY_TABLE,
                    (10 of 128) to IRON_ORE_TABLE,
                    (10 of 128) to COAL_TABLE,
                    (8 of 128) to RAW_SWORDFISH_TABLE,
                    (2 of 128) to ADAMANT_SQ_SHIELD_TABLE,
                    (2 of 128) to RUNE_LEGS_OR_SKIRT_TABLE(plr)).apply {
                if (!plr.equipment.contains(2572)) { // Create nothing slots if we don't have ROW.
                    add((17 of 128) to DropTableHandler.createList { nothing(1 of 7) })
                }
            })

            // Pick the drop table.
            val rollTable = nodeTable.roll()
            if (rollTable != null) {
                itemList += rollTable
            }
            return itemList
        }
        return emptyList()
    }

    override fun computePossibleItems(): DropTableItemList = ALWAYS_TABLE + SPINACH_ROLL_TABLE + RUNE_TABLE +
            GEM_TABLE + RUNITE_BAR_TABLE + CRYSTAL_KEY_TABLE + IRON_ORE_TABLE + COAL_TABLE + RAW_SWORDFISH_TABLE +
            ADAMANT_SQ_SHIELD_TABLE + RUNE_LEGS_OR_SKIRT_TABLE(null)
}