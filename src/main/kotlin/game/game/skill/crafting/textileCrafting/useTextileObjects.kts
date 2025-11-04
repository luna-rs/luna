package game.skill.crafting.textileCrafting

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Registers [objectList] with [itemArray] for object clicking and using an item on an object.
 */
fun register(objectList: Set<Int>, itemArray: IntArray) {
    registerObjectClick(objectList, itemArray)
    registerUseItemOnObject(objectList)
}

/**
 * Registers an object click.
 */
fun registerObjectClick(objectList: Set<Int>, itemArray: IntArray) {
    objectList.forEach {
        object2(it) {
            plr.overlays.open(object : MakeItemDialogue(*itemArray) {
                override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
                    val textile = Textile.PROCESSED_IDS_TO_TEXTILES[id]
                    if (textile != null) {
                        plr.submitAction(MakeTextileActionItem(player, textile, forAmount))
                    }
                }
            })
        }
    }
}

/**
 * Registers using an item on an object.
 */
fun registerUseItemOnObject(objectList: Set<Int>) {
    Textile.RAW_IDS_TO_TEXTILES.entries.forEach { entry ->
        objectList.forEach {
            useItem(entry.key).onObject(it) {
                plr.overlays.open(object : MakeItemDialogue(entry.value.processedItem.id) {
                    override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
                        plr.submitAction(MakeTextileActionItem(player, entry.value, forAmount))
                    }
                })
            }
        }
    }
}

// Register both loom and spinning wheel objects here.
register(TextileType.SPINNING_WHEEL.objectIds, Textile.SPINNING_WHEEL_PROCESSED_IDS)
register(TextileType.LOOM.objectIds, Textile.LOOM_PROCESSED_IDS)