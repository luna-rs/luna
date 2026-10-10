package game.skill.runecrafting.essenceMine

import api.predef.*
import game.skill.magic.teleOther.NpcTeleOtherAction
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Wizard Cromperty, the wizard and inventor in East Ardougne.
 */
val WIZARD_CROMPERTY = EssenceMineWizard.WIZARD_CROMPERTY.npcId

/**
 * Where Cromperty's teleportation block sends players: the RPDT depot, where the other block is waiting.
 */
val RPDT_DEPOT = Position(2649, 3271)

/**
 * Has [cromperty] teleport [plr] to the other teleportation block.
 */
fun teleportToBlock(cromperty: Npc, plr: Player) {
    // TODO Tribal Totem: once the crate is delivered, the block is in Lord Handelmort's mansion (2638, 3321). After
    //  the quest, Cromperty says "Hmm.... that's odd... I can't seem to get a signal..." instead of teleporting.
    EssenceMine.teleOther(cromperty, plr, RPDT_DEPOT) {
        NpcTeleOtherAction(cromperty, plr, RPDT_DEPOT, incantation = "Dipsolum sententa sententi!", message = null)
    }
}

/**
 * Asks Cromperty to be teleported to his other teleportation block.
 */
fun askForBlockTeleport(plr: Player, cromperty: Npc) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Can I be teleported please?")
        .npc(WIZARD_CROMPERTY, "By all means! I'm afraid I can't give you any specifics",
             "as to where you will come out however. Presumably", "wherever the other block is located.")
        .options(
            "Yes, that sounds good. Teleport me!", {
                it.newDialogue()
                    .player("Yes, that sounds good. Teleport me!")
                    .npc(WIZARD_CROMPERTY, Expression.HAPPY, "Okey dokey! Ready?")
                    .then { plr -> teleportToBlock(cromperty, plr) }
                    .open()
            },
            "That sounds dangerous. Leave me here.", {
                it.newDialogue()
                    .player(Expression.SHOCKED, "That sounds dangerous. Leave me here.")
                    .npc(WIZARD_CROMPERTY, "As you wish.")
                    .open()
            })
        .open()
}

/**
 * Asks Cromperty where his other teleportation block is.
 */
fun askWhereBlockIs(plr: Player, cromperty: Npc) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "So where is the other block?")
        .npc(WIZARD_CROMPERTY, Expression.CONFUSED, "Well... Hmm. I would guess somewhere between here",
             "and the Wizards' Tower in Misthalin. All I know is that",
             "it hasn't got there yet as the wizards there would have", "contacted me.")
        .npc(WIZARD_CROMPERTY, Expression.SAD, "I'm using the RPDT for delivery. They assured me it",
             "would be delivered promptly.")
        .options(
            "Can I be teleported please?", { askForBlockTeleport(it, cromperty) },
            "Who are the RPDT?", {
                it.newDialogue()
                    .player(Expression.QUIZZICAL, "Who are the RPDT?")
                    .npc(WIZARD_CROMPERTY, Expression.HAPPY, "The RuneScape Parcel Delivery Team. They come",
                         "very highly recommended. Their motto is: 'We aim to",
                         "deliver your stuff at some point after you have paid us!'")
                    .open()
            })
        .open()
}

/**
 * Asks Cromperty about his inventions.
 */
fun askAboutInventions(plr: Player, cromperty: Npc) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "So what have you invented?")
        .npc(WIZARD_CROMPERTY, Expression.HAPPY, "Ah! My latest invention is my patent pending",
             "teleportation block! It emits a low level magical signal,",
             "that will allow me to locate it anywhere in the world,", "and teleport anything")
        .npc(WIZARD_CROMPERTY, Expression.HAPPY, "directly to it! I hope to revolutionise the entire",
             "teleportation system! Don't you think I'm great? Uh, I", "mean it's great?")
        .options(
            "So where is the other block?", { askWhereBlockIs(it, cromperty) },
            "Can I be teleported please?", { askForBlockTeleport(it, cromperty) },
            "Well done, that's very clever.", {
                it.newDialogue()
                    .player("Well done, that's very clever.")
                    .npc(WIZARD_CROMPERTY, Expression.HAPPY,
                         "Yes it is isn't it? Forgive me for feeling a little smug,",
                         "this is a major breakthrough in the field of teleportation!")
                    .open()
            })
        .open()
}

npc1(WIZARD_CROMPERTY) {
    val cromperty = targetNpc
    // TODO Rune Mysteries: until it's complete, he doesn't offer "Can you teleport me to the Rune Essence?".
    plr.newDialogue()
        .npc(WIZARD_CROMPERTY, "Hello there.", "My name is Cromperty.", "I am a Wizard, and an inventor.")
        .options(
            "Two jobs? That's got to be tough.", {
                it.newDialogue()
                    .player("Two jobs? That's got to be tough.")
                    .npc(WIZARD_CROMPERTY, Expression.HAPPY, "Not when you combine them it isn't!",
                         "I invent MAGIC things!")
                    .options(
                        "So what have you invented?", { plr -> askAboutInventions(plr, cromperty) },
                        "Well, I shall leave you to your inventing.", { plr ->
                            plr.newDialogue()
                                .player(Expression.CONFUSED, "Well, I shall leave you to your inventing.")
                                .npc(WIZARD_CROMPERTY, "Thank you for dropping by! Stop again anytime!")
                                .open()
                        })
                    .open()
            },
            "So what have you invented?", { askAboutInventions(it, cromperty) },
            "Can you teleport me to the Rune Essence?", {
                it.newDialogue()
                    .player(Expression.QUIZZICAL, "Can you teleport me to the Rune Essence?")
                    .then { plr -> EssenceMine.teleport(cromperty, plr) }
                    .open()
            })
        .open()
}

npc2(WIZARD_CROMPERTY) {
    EssenceMine.teleport(targetNpc, plr)
}
