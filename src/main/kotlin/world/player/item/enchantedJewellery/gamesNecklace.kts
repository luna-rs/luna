package world.player.item.enchantedJewellery

import api.predef.*
import api.predef.ext.*
import api.predef.magic
import api.predef.world
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.Position
import io.luna.game.model.item.*
import io.luna.game.model.mob.dialogue.*
import io.luna.util.StringUtils
import world.player.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import world.player.skill.magic.teleportSpells.TeleportStyle
import world.player.skill.magic.teleportSpells.TeleportStyle.REGULAR
import world.player.skill.magic.teleportSpells.TeleportAction
import world.player.skill.magic.SpellRequirement

val GAMES_NECKLACE_1 = 3867
val GAMES_NECKLACE_2 = 3865
val GAMES_NECKLACE_3 = 3863
val GAMES_NECKLACE_4 = 3861
val GAMES_NECKLACE_5 = 3859
val GAMES_NECKLACE_6 = 3857
val GAMES_NECKLACE_7 = 3855
val GAMES_NECKLACE_8 = 3853

val GAMES_NECKLACES = intArrayOf(
    GAMES_NECKLACE_1,
    GAMES_NECKLACE_2,
    GAMES_NECKLACE_3,
    GAMES_NECKLACE_4,
    GAMES_NECKLACE_5,
    GAMES_NECKLACE_6,
    GAMES_NECKLACE_7,
    GAMES_NECKLACE_8)

val degration = mapOf(
    GAMES_NECKLACE_8 to GAMES_NECKLACE_7,
    GAMES_NECKLACE_7 to GAMES_NECKLACE_6,
    GAMES_NECKLACE_6 to GAMES_NECKLACE_5,
    GAMES_NECKLACE_5 to GAMES_NECKLACE_4,
    GAMES_NECKLACE_4 to GAMES_NECKLACE_3,
    GAMES_NECKLACE_3 to GAMES_NECKLACE_2,
    GAMES_NECKLACE_2 to GAMES_NECKLACE_1,
    GAMES_NECKLACE_1 to null,
)

GAMES_NECKLACES.forEach {
    val necklaceId = it
    item4(necklaceId) {
        plr.newDialogue()
            .options(
                "Burthorpe.", {
                    //plr.move(Position(2898, 3546))
                    teleport(plr, Position(2898, 3546), "Burthorpe")
                    degrade(plr, necklaceId, index)
                 },
                "Barbarian Outpost.", {
                    //plr.move(Position(2536, 3565))
                    teleport(plr, Position(2536, 3565), "Barbarian Outpost")
                    degrade(plr, necklaceId, index)
                })
            .open()
    }
}

fun degrade(plr: Player, necklaceId: Int, index: Int) {
    plr.inventory.remove(index ?: 0, Item(necklaceId, 1))
    val nextNecklace: Int = degration.get(necklaceId) ?: -1
    if (nextNecklace != -1) {
        plr.inventory.add(index ?: 0, Item(nextNecklace, 1))
    } else {
        plr.sendMessage("The necklace crumbles to dust.")
    }
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
                plr.sendMessage("You teleport to ${StringUtils.capitalize(destinationName)}.")
            }
        })
    }
}