package game.npc.spawn.falador

import api.bot.zone.SubZone
import api.predef.*
import api.shop.dsl.ShopHandler
import game.content.quests.Quest
import game.content.quests.QuestGates
import game.content.respawn.RespawnPoint.faladorRespawn
import game.npc.spawn.GeneralStore
import game.npc.spawn.falador.InitiateArmoury.ARMOURY
import game.npc.spawn.falador.InitiateArmoury.BUY_ARMOUR
import game.npc.spawn.falador.InitiateArmoury.PIECES
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder

/**
 * Sir Tiffy Cashien.
 */
val SIR_TIFFY = 2290

ShopHandler.create(ARMOURY) {
    botAccess {
        npcSeller(1, SIR_TIFFY, SubZone.FALADOR_PARK_MARKETPLACE, dialogueOption = BUY_ARMOUR)
    }
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        PIECES.forEach { it x 30 }
    }
}

/**
 * How Sir Tiffy addresses [plr].
 */
fun title(plr: Player) = if (plr.appearance.isFemale) "milady" else "sirrah"

/**
 * Sir Tiffy's questions for a Temple Knight.
 */
fun knightOptions(dialogue: DialogueQueueBuilder) =
    dialogue.options("Can you explain the Gaze of Saradomin to me?", { explainGaze(it) },
                     BUY_ARMOUR, { buyArmour(it) },
                     "Can I switch respawns please?", { switchRespawn(it) },
                     "Goodbye.", {
                         it.newDialogue()
                             .player("Well, see you around Tiffy.")
                             .npc(SIR_TIFFY, "Ta-ta for now, old bean!")
                             .open()
                     })

/**
 * Sir Tiffy explains why Temple Knights respawn in Falador.
 */
fun explainGaze(plr: Player) {
    val dialogue = plr.newDialogue()
        .player("I don't really understand this 'Gaze of Saradomin' thing... Do you think you could explain what " +
                    "it does for me?")
        .npc(SIR_TIFFY, "Certainly ${title(plr)}! As you know, we Temple Knights are personally favoured by " +
            "Saradomin himself.")
        .npc(SIR_TIFFY, "And when I say personally favoured, I don't mean that sometime off in the future he's " +
            "going to buy us all a drink!")
        .npc(SIR_TIFFY, "He watches over each of us, and when we die he catches us as we fall, and ensures we " +
            "arrive back at Falador castle safe and sound.")
        .npc(SIR_TIFFY, "We usually lose some equipment when he does so, but it's a small price to pay to be hale " +
            "and hearty again, what?")
        .npc(SIR_TIFFY, "Some lucky fellows have a similar system going already, but when they die they spawn in " +
            "that squalid little swamp village Lumbridge.")
        .player("Yeah, what kind of person would want to spawn there... Certainly not me, and I never have! Honest!")
        .npc(SIR_TIFFY, "Well, you should be glad that we offer you a step up then! Falador is clearly a far " +
            "superior town to spend your time in!")
        .npc(SIR_TIFFY, "Was there something else you wanted to ask good old Tiffy, ${title(plr)}?")
    knightOptions(dialogue).open()
}

/**
 * Sir Tiffy opens his armoury.
 */
fun buyArmour(plr: Player) {
    plr.newDialogue()
        .player(BUY_ARMOUR)
        .npc(SIR_TIFFY, "Of course old bean. I can sell you up to Initiate level items only I'm afraid.")
        .then { if (QuestGates.passes(it, Quest.RECRUITMENT_DRIVE)) GeneralStore.open(it, ARMOURY) }
        .open()
}

/**
 * Sir Tiffy moves [plr]'s respawn to Falador, or back to Lumbridge.
 */
fun switchRespawn(plr: Player) {
    val toFalador = !plr.faladorRespawn
    val dialogue = plr.newDialogue().player("Can I switch respawns, please?")
    if (toFalador) {
        dialogue.npc(SIR_TIFFY, "Ah, so you'd like to respawn in Falador, the good old homestead! Are you sure?")
    } else {
        dialogue.npc(SIR_TIFFY, "What? You're saying you want to respawn in Lumbridge? Are you sure?")
    }
    dialogue.options(if (toFalador) "Yes, I want to respawn in Falador." else "Yes, I want to respawn in Lumbridge.", {
        it.faladorRespawn = toFalador
        it.newDialogue()
            .player(if (toFalador) "Yes, I want to respawn in Falador." else "Yes, I want to respawn in Lumbridge.")
            .npc(SIR_TIFFY, if (toFalador) "Top-hole, what? Good old Fally is definitely the hot-spot nowadays!"
            else "Why anyone would want to visit that smelly little swamp village of oiks is quite beyond me, " +
                "I'm afraid, but the deed is done now.")
            .open()
    }, "Actually, no thanks. I like my respawn point.", {
        it.newDialogue()
            .player("Actually, no thanks. I like my respawn point.")
            .npc(SIR_TIFFY, "As you wish, what? Ta-ta for now.")
            .open()
    }).open()
}

npc1(SIR_TIFFY) {
    if (QuestGates.passes(plr, Quest.RECRUITMENT_DRIVE)) {
        val dialogue = plr.newDialogue()
            .npc(SIR_TIFFY, "What ho, ${if (plr.appearance.isFemale) "milady" else "sir"}. Jolly good show on the " +
                "old training grounds thingy, what?")
        knightOptions(dialogue).open()
    } else {
        plr.newDialogue()
            .player("Hello.")
            .npc(SIR_TIFFY, "What ho, ${if (plr.appearance.isFemale) "milady" else "sir"}. Spiffing day for a walk " +
                "in the park, what?")
            .player("...spiffing?")
            .npc(SIR_TIFFY, "Absolutely, top-hole! Well, can't stay and chat all day, dontchaknow! Ta-ta for now!")
            .player("Erm... goodbye.")
            .open()
    }
}
