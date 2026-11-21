package game.skill.crafting.jewelleryMaking

import api.predef.npc1
import api.predef.useItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

val BROTHER_JERED = 802
val SCORPIUS = 492
val UNBLESSED_SYMBOL = 1716
val HOLY_SYMBOL = 1718
val UNPOWERED_SYMBOL = 1722
val UNHOLY_SYMBOL = 1724

/**
 * Exchanges one strung symbol in place when the player accepts the blessing.
 */
fun offerBlessing(plr: Player, npcId: Int, unblessed: Int, blessed: Int, offer: String, vararg instructions: String) {
    if (!plr.inventory.contains(unblessed)) {
        plr.newDialogue().npc(npcId, *instructions).open()
        return
    }

    plr.newDialogue().npc(npcId, offer)
        .options("Yes, please.", {
            if (it.inventory.replace(unblessed, blessed)) {
                it.newDialogue().give(Item(blessed), "Your symbol has been blessed.", false).open()
            } else {
                it.newDialogue().npc(npcId, "Bring me the strung symbol and I can bless it.").open()
            }
        }, "No, thank you.", { it.overlays.closeWindows() })
        .open()
}

fun talkToJered(plr: Player) {
    offerBlessing(plr, BROTHER_JERED, UNBLESSED_SYMBOL, HOLY_SYMBOL,
                  "Shall I bless your symbol of Saradomin?",
                  "Bring me a strung symbol of Saradomin and I can bless it.",
                  "Cast a silver bar with a holy mould, then add a ball of wool.")
}

fun talkToScorpius(plr: Player) {
    // Observatory Quest is unavailable, so the strung emblem is enough to request a blessing.
    offerBlessing(plr, SCORPIUS, UNPOWERED_SYMBOL, UNHOLY_SYMBOL,
                  "Shall I bless your emblem with Zamorak's power?",
                  "Bring me a strung emblem of Zamorak and I can bless it.",
                  "Chaos druids carry the mould. Cast the emblem from silver,",
                  "then string it with a ball of wool.")
}

npc1(BROTHER_JERED) { talkToJered(plr) }
useItem(UNBLESSED_SYMBOL).onNpc(BROTHER_JERED) { talkToJered(plr) }

npc1(SCORPIUS) { talkToScorpius(plr) }
useItem(UNPOWERED_SYMBOL).onNpc(SCORPIUS) { talkToScorpius(plr) }
