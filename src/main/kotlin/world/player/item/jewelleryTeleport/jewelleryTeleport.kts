package world.player.item.jewelleryTeleport

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.Position
import io.luna.game.model.item.*
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.util.StringUtils
import world.player.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import world.player.skill.magic.teleportSpells.TeleportStyle
import world.player.skill.magic.teleportSpells.TeleportAction
import world.player.skill.magic.SpellRequirement

TeleportJewellery.values().forEach {
    val jewellery = it

    for (i in 0..jewellery.itemIDs.size-1) {
        val itemID: Int = jewellery.itemIDs.get(i)
        val lastItem: Boolean = i == jewellery.itemIDs.size-1

        if (lastItem && !jewellery.disappear) {
            item4(itemID) {
                plr.sendMessage(jewellery.lastChargeMessage)
            }
            continue
        }

        item4(itemID) {
            plr.sendMessage(jewellery.rubMessage)
            world.scheduleOnce(1, {
                val dialogueOptions = mutableListOf<DialogueOption>()
                for (destination in jewellery.destinations) {
                    dialogueOptions.add(DialogueOption(destination.second, {
                        teleport(plr, destination.first, destination.second)
                        degrade(plr, itemID, index, jewellery)
                    }))
                }
                dialogueOptions.add(DialogueOption("Nowhere.", {
                    plr.resetDialogues()
                }))

                plr.newDialogue()
                    .options(dialogueOptions)
                    .open()
            })
        }
    }
}

fun degrade(plr: Player, necklaceId: Int, index: Int, type: TeleportJewellery) {
    plr.inventory.remove(index, Item(necklaceId, 1))

    val jewelleryIndex = type.itemIDs.indexOf(necklaceId)
    val lastCharge = (!type.disappear && jewelleryIndex+2 == type.itemIDs.size)
            || (type.disappear && jewelleryIndex+1 == type.itemIDs.size)

    if (lastCharge) {
        plr.sendMessage(type.lastChargeMessage)
        if (type.disappear) {
            return
        }
    }
    val nextJewellery = type.itemIDs.get(jewelleryIndex+1)
    plr.inventory.add(index, Item(nextJewellery, 1))
}

fun teleport(plr: Player, pos: Position, destinationName: String) {
    if (plr.teleportDelay.ready(2)) { // So player can't button spam.
        plr.submitAction(object : TeleportAction(
            plr,
            0,
            0.0,
            pos,
            TeleportStyle.REGULAR,
            emptyList<SpellRequirement>()
        ) {
            override fun onTeleport() {
                plr.sendMessage("You teleport to ${StringUtils.capitalize(destinationName)}")
            }
        })
    }
}