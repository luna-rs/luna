package world.player.skill.mining

import api.attr.Attr
import api.item.dropTable.DropTable
import api.item.dropTable.DropTableHandler
import api.item.dropTable.DropTableItem
import api.item.dropTable.DropTableItemList
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * A class containing utility functions related to the Mining skill.
 */
object Mining {

    /**
     * A set of charged amulet of glory identifiers.
     */
    private val CHARGED_GLORY_IDS = setOf(1712, 1710, 1708, 1706)

    /**
     * The mining gem drop table.
     */
    val MINING_GEM_DROP_TABLE = DropTableHandler.create {
        nothing(10 of 4681)
        "Uncut sapphire" x 1 chance (1 of 1024)
        "Uncut emerald" x 1 chance (1 of 2048)
        "Uncut ruby" x 1 chance (1 of 4096)
        "Uncut diamond" x 1 chance (1 of 16_384)
    }.table {
        object : DropTable() {
            override fun canRollOnTable(mob: Mob?, source: Entity?): Boolean {
                return rand().nextInt(256) == 0 // 1/256 chance to roll on the table.
            }

            override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
                if (mob is Player) {
                    val hasChargedGlory = CHARGED_GLORY_IDS.contains(mob.equipment.amulet?.id ?: -1)
                    if (hasChargedGlory) {
                        // We have a charged amulet of glory equipped.
                        return DropTableHandler.createList {
                            nothing(1 of 117)
                            "Uncut sapphire" x 1 chance (1 of 257)
                            "Uncut emerald" x 1 chance (1 of 514)
                            "Uncut ruby" x 1 chance (1 of 1027)
                            "Uncut diamond" x 1 chance (1 of 4108)
                        }
                    }
                }
                return table
            }

            override fun computePossibleItems(): DropTableItemList = table
        }
    }
}