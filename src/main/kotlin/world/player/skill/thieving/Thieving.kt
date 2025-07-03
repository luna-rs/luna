package world.player.skill.thieving

import api.item.dropTable.DropTable
import api.item.dropTable.DropTableHandler
import api.item.dropTable.DropTableItemList
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.util.RandomUtils
import io.luna.util.Rational
import world.player.skill.Skills
import world.player.skill.thieving.pickpocketNpc.ThievableNpc

/**
 * Contains utility functions related to the thieving skill.
 */
object Thieving {

    /**
     * The rogue equipment drop table.
     */
    private val ROGUE_EQUIPMENT_TABLE = DropTableHandler.create {
        "Rogue mask" x 1 chance (1 of 2500)
        "Rogue top" x 1 chance (1 of 2500)
        "Rogue gloves" x 1 chance (1 of 2500)
        "Rogue trousers" x 1 chance (1 of 2500)
        "Rogue boots" x 1 chance (1 of 2500)
    }.table {
        object : DropTable() {
            override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
                if (mob is Player) { // Remove from drop list if we already own a set.
                    val it = items.iterator()
                    while (it.hasNext()) {
                        if (mob.hasItem(it.next().id)) {
                            it.remove()
                        }
                    }
                }
                return items
            }

            override fun computePossibleItems(): DropTableItemList = items
        }
    }

    /**
     * The rogue equipment items.
     */
    private val ROGUE_EQUIPMENT_ITEMS = setOf(Item(5553), Item(5554), Item(5555), Item(5556), Item(5557))


    /**
     * If a pickpocketing action will be successful or not.
     */
    fun canPickpocket(plr: Player, thievable: ThievableNpc): Boolean {
        val level = plr.thieving.level
        if (level >= thievable.master) {
            return true
        }
        val (low, high) = thievable.chance
        return Skills.success(low, high, level)
    }

    /**
     * Attempts to roll for rogue equipment.
     */
    fun rollRogueEquipment(plr: Player, target: Entity, chance: Rational = ALWAYS) {
        if (RandomUtils.rollSuccess(chance)) {
            val rogueLoot = ROGUE_EQUIPMENT_TABLE.roll(plr, target)
            for (item in rogueLoot) {
                plr.giveItem(item)
                plr.sendMessage("You also manage to find ${addArticle(item.itemDef.name)}!")
            }
        }
    }

    /**
     * Determines if [plr] will receive double loot based on their equipment.
     */
    fun isDoubleLoot(plr: Player): Boolean {
        var chance = 0.0
        for (item in ROGUE_EQUIPMENT_ITEMS) {
            if (plr.equipment.contains(item.equipDef.index, item.id)) {
                if (chance >= 0.75) {  // We have all 5 pieces, 100% chance.
                    chance = 1.0
                } else if (chance >= 0.0) { // Increase chance by 15% per piece until 75%.
                    chance += 0.15
                }
            }
        }
        return RandomUtils.rollSuccess(Rational.fromDouble(chance))
    }
}