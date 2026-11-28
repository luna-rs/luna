package game.content.cooksAssistant

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression.HAPPY
import io.luna.game.model.mob.dialogue.Expression.SAD

/**
 * The Cook's conversation, from the Duke's birthday request to the ingredient hand-in.
 *
 * @author lare96
 */
object CooksAssistantDialogue {
    const val ASK = "What's wrong?"
    const val ACCEPT = "Yes, I'll help you."
    const val READY = "I've brought everything for the cake."
    const val INGREDIENTS = "Milk, flour, and an egg!"
    const val HAND_IN = "You hand the cook the milk, egg and flour."

    private const val COOK = CooksAssistant.COOK
    private const val MONEY = "You could give me all your money!"
    private const val UNHAPPY = "You don't look very happy."
    private const val HAT = "Nice hat!"
    private const val DAY_OFF = "I'd take the day off if I were you."
    private const val DECLINE = "No, maybe later."
    private const val DOING_WELL = "I'm getting strong and mighty."
    private const val KEEP_DYING = "I keep on dying."
    private const val RANGE = "Can I use your range?"

    fun talk(player: Player) {
        when (CooksAssistant.stage(player)) {
            CooksAssistant.NOT_STARTED -> player.newDialogue()
                .npc(COOK, SAD, "What am I to do?")
                .options(ASK, ::ask,
                    MONEY, { it.newDialogue().player(MONEY).npc(COOK, SAD, "Very funny.").open() },
                    UNHAPPY, ::unhappy,
                    HAT, ::hat)
                .open()
            CooksAssistant.STARTED -> progress(player)
            CooksAssistant.COMPLETE -> finished(player)
        }
    }

    private fun unhappy(player: Player) {
        player.newDialogue().player(UNHAPPY).npc(COOK, SAD, "No, I'm in a terrible fix.")
            .options(ASK, ::ask, DAY_OFF, {
                it.newDialogue().player(DAY_OFF)
                    .npc(COOK, SAD, "I'd be in even worse trouble if I did that!").then(::ask).open()
            }).open()
    }

    private fun hat(player: Player) {
        player.newDialogue().player(HAPPY, HAT)
            .npc(COOK, "Thank you. It's a fairly ordinary cook's hat.").open()
    }

    private fun ask(player: Player) {
        player.newDialogue().player(ASK)
            .npc(COOK, SAD, "It's the Duke's birthday. I'm supposed to make a cake for him this evening,",
                "and I've forgotten some of the ingredients.")
            .npc(COOK, SAD, "I'll never get them in time. Could you help me?")
            .options(ACCEPT, {
                CooksAssistant.start(it)
                it.newDialogue().player(HAPPY, ACCEPT)
                    .npc(COOK, HAPPY, "Thank you! Please bring me a bucket of milk, an egg and a pot of flour.").open()
            }, DECLINE, {
                it.newDialogue().player(DECLINE).npc(COOK, SAD, "All right, suit yourself.").open()
            }).open()
    }

    private fun progress(player: Player) {
        val dialogue = player.newDialogue().npc(COOK, SAD, "How are you getting on with the ingredients?")
        val ingredients = listOf(CooksAssistant.MILK to "a bucket of milk", CooksAssistant.FLOUR to "a pot of flour",
            CooksAssistant.EGG to "an egg")
        val (carried, missing) = ingredients.partition { player.inventory.contains(it.first) }
        when {
            missing.isEmpty() -> dialogue.player(HAPPY, READY).player(HAPPY, INGREDIENTS)
                .npc(COOK, HAPPY, "You've saved the day. Thank you!")
                .text(HAND_IN).then(CooksAssistant::finish)
            carried.isEmpty() -> dialogue.player("I'm afraid I haven't found any yet.")
                .npc(COOK, SAD, "Oh dear! I still need milk, an egg and flour.")
            else -> {
                dialogue.player("I've found some of the ingredients.")
                carried.forEach { (_, name) -> dialogue.player("I've got $name.") }
                dialogue.npc(COOK, "Thank you. I still need these before I can make the cake.")
                missing.forEach { (_, name) -> dialogue.npc(COOK, "I still need $name.") }
                dialogue.player("I'll go and look for the rest.")
            }
        }
        dialogue.open()
    }

    private fun finished(player: Player) {
        player.newDialogue().npc(COOK, HAPPY, "How are your adventures going, my friend?")
            .options(DOING_WELL, {
                it.newDialogue().player(DOING_WELL).npc(COOK, HAPPY, "Glad to hear it.").open()
            }, KEEP_DYING, {
                it.newDialogue().player(KEEP_DYING)
                    .npc(COOK, HAPPY, "At least you keep coming back to life!").open()
            }, HAT, ::hat, RANGE, {
                it.newDialogue().player(RANGE)
                    .npc(COOK, HAPPY, "Of course. Anyone can use it, and it burns some foods less often.").open()
            }).open()
    }
}
