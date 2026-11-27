package game.content.romeoJuliet

import game.content.romeoJuliet.RomeoJuliet.APOTHECARY
import game.content.romeoJuliet.RomeoJuliet.BERRIES
import game.content.romeoJuliet.RomeoJuliet.COMPLETE
import game.content.romeoJuliet.RomeoJuliet.FATHER_LAWRENCE
import game.content.romeoJuliet.RomeoJuliet.JULIET
import game.content.romeoJuliet.RomeoJuliet.JULIET_CRYPT
import game.content.romeoJuliet.RomeoJuliet.MESSAGE
import game.content.romeoJuliet.RomeoJuliet.NOT_STARTED
import game.content.romeoJuliet.RomeoJuliet.PASSED_MESSAGE
import game.content.romeoJuliet.RomeoJuliet.POTION
import game.content.romeoJuliet.RomeoJuliet.ROMEO
import game.content.romeoJuliet.RomeoJuliet.SPOKEN_APOTHECARY
import game.content.romeoJuliet.RomeoJuliet.SPOKEN_FATHER
import game.content.romeoJuliet.RomeoJuliet.SPOKEN_JULIET
import game.content.romeoJuliet.RomeoJuliet.SPOKEN_ROMEO
import game.content.romeoJuliet.RomeoJuliet.advance
import game.content.romeoJuliet.RomeoJuliet.stage
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.Expression
import kotlin.random.Random

/**
 * Handles the quest's conversations and guarded exchanges with the four Varrock NPCs.
 *
 * @author lare96
 */
object RomeoJulietDialogue {
    const val HELP_ROMEO = "Can I help find her for you?"
    const val GO_TO_CRYPT = "I'll come with you."
    const val ASK_CADAVA = "Father Lawrence sent me about a Cadava potion."
    const val BREW_CADAVA = "Can you make a Cadava potion?"
    const val ASK_STRENGTH = "Can you make a strength potion?"
    const val PAY_STRENGTH = "Yes, I'll pay."
    private const val STRENGTH_POTION = 113
    private const val SPOT_CREAM = 195
    private const val BREW_POTION = 3283
    private val strengthIngredients = listOf(Item(223), Item(225), Item(995, 5))

    fun romeo(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (stage(player)) {
            NOT_STARTED -> dialogue
                .npc(ROMEO, Expression.SAD, "Juliet, where have you gone?", "Have you seen her, adventurer?")
                .options("Yes, I think I've seen her.", { offerRomeo(it, npc) },
                    "No, but that's girls for you.", { offerRomeo(it, npc) },
                    HELP_ROMEO, { acceptRomeo(it, npc) })
            SPOKEN_ROMEO -> dialogue.npc(ROMEO, Expression.SAD, "Juliet lives in the house west of Varrock.", "Please tell her I'm thinking of her.")
            SPOKEN_JULIET -> {
                if (!player.inventory.contains(MESSAGE)) {
                    dialogue.npc(ROMEO, "Have you brought Juliet's message? I need to read it.")
                } else {
                    dialogue.player("I've brought you a message from Juliet.")
                        .text("You hand Juliet's message to Romeo.")
                        .then { p ->
                            if (canContinue(p, npc) && stage(p) == SPOKEN_JULIET && p.inventory.remove(Item(MESSAGE))) {
                                advance(p, PASSED_MESSAGE)
                                p.newDialogue().npc(ROMEO, Expression.SAD, "Her father is against our marriage.")
                                    .npc(ROMEO, "Please tell Father Lawrence. He might be able to help.").open()
                            }
                        }
                }
            }
            PASSED_MESSAGE -> dialogue.npc(ROMEO, "Father Lawrence will know what to do.", "Try the church in northeast Varrock.")
            SPOKEN_FATHER -> dialogue.npc(ROMEO, "The Apothecary lives southwest of the square.", "Try the small house behind the sloped roof.")
            SPOKEN_APOTHECARY -> dialogue.npc(ROMEO, if (player.inventory.contains(POTION))
                "You have the potion! Take it to Juliet." else "I hope the Apothecary can make the potion soon.")
            JULIET_CRYPT -> dialogue.npc(ROMEO, "Have you seen Juliet?")
                .npc(ROMEO, "The potion worked? She'll be waiting for me in the crypt then.")
                .npc(ROMEO, "The crypt? Could you come with me?")
                .options(GO_TO_CRYPT, { if (canContinue(it, npc)) RomeoJulietScenes.finale(it) },
                    "I'll come back later.", { })
            COMPLETE -> dialogue.npc(ROMEO, Expression.HAPPY, "Juliet's cousin and I are getting along splendidly.", "Thank you for your help.")
        }
        dialogue.open()
    }

    private fun offerRomeo(player: Player, npc: Npc) {
        player.newDialogue().npc(ROMEO, "Please find Juliet and tell her I still want to be with her.")
            .options("Yes, I'll tell her.", { acceptRomeo(it, npc) },
                "I'm too busy at the moment.", { it.newDialogue().npc(ROMEO, "If you see her, I'd be grateful for your help.").open() })
            .open()
    }

    private fun acceptRomeo(player: Player, npc: Npc) {
        player.newDialogue().npc(ROMEO, Expression.HAPPY, "Thank you! Tell Juliet I long to see her.")
            .npc(ROMEO, "She lives in the house just west of Varrock.")
            .then { if (canContinue(it, npc) && stage(it) == NOT_STARTED) advance(it, SPOKEN_ROMEO) }
            .open()
    }

    fun juliet(player: Player, npc: Npc) {
        when (stage(player)) {
            NOT_STARTED -> player.newDialogue()
                .npc(JULIET, Expression.SAD, "Have you seen Romeo?", "He spends most of his time thinking about poetry.")
                .options("Yes, I've met him.", { offerJuliet(it, npc) },
                    "No, I'd remember if I had.", { offerJuliet(it, npc) },
                    "I could go and find him.", { giveMessage(it, npc) },
                    "I think you could do better.", { it.newDialogue().npc(JULIET, "He has his good points.").open() })
                .open()
            SPOKEN_ROMEO -> giveMessage(player, npc)
            SPOKEN_JULIET -> if (owns(player, MESSAGE)) say(player, JULIET, "Please take my message to Romeo.") else giveMessage(player, npc)
            PASSED_MESSAGE -> say(player, JULIET, "Father Lawrence knows us both. I hope he can help.")
            SPOKEN_FATHER -> say(player, JULIET, "Please be quick. My father could return any minute.")
            SPOKEN_APOTHECARY -> deliverPotion(player, npc)
            JULIET_CRYPT -> say(player, JULIET, "Make sure Romeo knows the plan. He can be a little forgetful.")
            COMPLETE -> say(player, JULIET, "I woke up alone in that cold crypt. Romeo ran off with my cousin!")
        }
    }

    private fun offerJuliet(player: Player, npc: Npc) {
        player.newDialogue().npc(JULIET, "Could you take a message to Romeo?")
            .options("Yes, I'll take it.", { giveMessage(it, npc) },
                "I have something else to do.", { it.newDialogue().npc(JULIET, "I'll stay here in case he comes back.").open() })
            .open()
    }

    private fun giveMessage(player: Player, npc: Npc) {
        val previous = stage(player)
        player.newDialogue().npc(JULIET, if (previous == SPOKEN_JULIET)
            "I've written you another message. Please don't lose it." else "Please take this message to Romeo. It may be our only hope.")
            .then { p ->
                if (!canContinue(p, npc) || stage(p) != previous) return@then
                val message = Item(MESSAGE)
                if (!owns(p, MESSAGE) && !p.inventory.add(message)) {
                    p.sendMessage("You need a free inventory slot for Juliet's message.")
                    return@then
                }
                advance(p, SPOKEN_JULIET)
                p.newDialogue().text("Juliet gives you a message for Romeo.").open()
            }.open()
    }

    private fun deliverPotion(player: Player, npc: Npc) {
        if (!player.inventory.contains(POTION)) {
            say(player, JULIET, "Have you managed to get the potion yet?")
            return
        }
        player.newDialogue().player("The Apothecary made a potion to make you appear dead.")
            .npc(JULIET, "Good! Romeo can come for me in the crypt.")
            .text("You hand the Cadava potion to Juliet.")
            .then { p ->
                if (canContinue(p, npc)) RomeoJulietScenes.potion(p)
            }.open()
    }

    fun father(player: Player, npc: Npc) {
        when (stage(player)) {
            NOT_STARTED -> say(player, FATHER_LAWRENCE, "Romeo looks troubled lately. Try the town square if you're looking to help.")
            PASSED_MESSAGE -> player.newDialogue()
                .player("Romeo sent me to ask for your help.")
                .npc(FATHER_LAWRENCE, "We could use a potion to make Juliet seem dead.")
                .npc(FATHER_LAWRENCE, "Romeo could fetch her from the crypt afterwards.")
                .npc(FATHER_LAWRENCE, "Tell the Apothecary I sent you. Ask him for a Cadava potion.")
                .then { if (canContinue(it, npc) && stage(it) == PASSED_MESSAGE) advance(it, SPOKEN_FATHER) }
                .open()
            SPOKEN_FATHER -> say(player, FATHER_LAWRENCE, "Ask the Apothecary for a Cadava potion.")
            SPOKEN_APOTHECARY -> say(player, FATHER_LAWRENCE, if (player.inventory.contains(POTION))
                "Take the potion to Juliet. I'll make sure Romeo knows the plan." else "Find the berries the Apothecary needs.")
            else -> say(player, FATHER_LAWRENCE, "A good day to you, adventurer.")
        }
    }

    fun apothecary(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(APOTHECARY, "I have potions to brew. What can I do for you?")
        val options = listOf(
            DialogueOption(ASK_STRENGTH) { strength(it, npc) },
            DialogueOption("Do you have a potion to make someone's hair fall out?") {
                say(it, APOTHECARY, "I do. I gave it to my mother once. I live alone now.")
            },
            DialogueOption("Have you got any potions to give away?") { freePotion(it, npc) }
        )
        val questOption = when (stage(player)) {
            SPOKEN_FATHER -> DialogueOption(ASK_CADAVA) { learnRecipe(it, npc) }
            SPOKEN_APOTHECARY -> DialogueOption(BREW_CADAVA) { brewCadava(it, npc) }
            else -> null
        }
        dialogue.options(options + listOfNotNull(questOption)).open()
    }

    private fun learnRecipe(player: Player, npc: Npc) {
        player.newDialogue().npc(APOTHECARY, "A Cadava potion? Nasty stuff.", "I've got most of what I need already.")
            .npc(APOTHECARY, "Bring me some Cadava berries. You can find them southeast of Varrock.")
            .then { if (canContinue(it, npc) && stage(it) == SPOKEN_FATHER) advance(it, SPOKEN_APOTHECARY) }
            .open()
    }

    private fun brewCadava(player: Player, npc: Npc) {
        if (stage(player) != SPOKEN_APOTHECARY) return
        when {
            owns(player, POTION) -> say(player, APOTHECARY, "I've already made a potion for you.")
            !player.inventory.contains(BERRIES) -> say(player, APOTHECARY, "I still need some Cadava berries.")
            else -> player.newDialogue().npc(APOTHECARY, "You've found the berries. I can make the potion now.")
                .first { npc.animation(Animation(BREW_POTION)) }
                .npc(APOTHECARY, "I'll mix them into a vial of this liquid.")
                .then { p ->
                    if (canContinue(p, npc) && stage(p) == SPOKEN_APOTHECARY && !owns(p, POTION) &&
                        p.inventory.replace(BERRIES, POTION)) {
                        p.newDialogue().text("The Apothecary gives you a Cadava potion.").open()
                    }
                }.open()
        }
    }

    private fun strength(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(APOTHECARY, "Bring me a limpwurt root, red spiders' eggs, and five coins.")
        if (player.inventory.containsAll(strengthIngredients)) {
            dialogue.options(PAY_STRENGTH, { p ->
                if (canContinue(p, npc) && p.inventory.containsAll(strengthIngredients)) {
                    val potion = Item(STRENGTH_POTION)
                    p.inventory.updateAll(listOf(potion), strengthIngredients)
                    p.newDialogue().give(potion, "The Apothecary gives you a strength potion.", false).open()
                }
            }, "No, thanks.", { })
        }
        dialogue.open()
    }

    private fun freePotion(player: Player, npc: Npc) {
        if (!canContinue(player, npc)) return
        when {
            owns(player, SPOT_CREAM) -> say(player, APOTHECARY, "You still have the spot cream I gave you.")
            Random.nextBoolean() -> say(player, APOTHECARY, "Sorry, I've nothing to give away just now.")
            else -> player.newDialogue().give(Item(SPOT_CREAM), "The Apothecary gives you some spot cream.", true).open()
        }
    }

    private fun say(player: Player, speaker: Int, vararg lines: String) {
        player.newDialogue().npc(speaker, *lines).open()
    }

    private fun owns(player: Player, id: Int): Boolean = player.inventory.contains(id) || player.bank.contains(id)

    private fun canContinue(player: Player, npc: Npc): Boolean =
        player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
            npc.state == EntityState.ACTIVE && player.position.isWithinDistance(npc.position, 5)
}
