package game.content.vampireSlayer

import game.content.vampireSlayer.VampireSlayer as Quest
import game.obj.entrances.Entrances
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Morgan's request and Dr Harlow's advice and beer exchange.
 *
 * @author lare96
 */
object VampireDialogue {
    const val ACCEPT = "Okay, I'm up for an adventure."
    const val ASK_FOR_HELP = "Morgan needs your help."

    fun morgan(player: Player) {
        val dialogue = player.newDialogue()
        when (Quest.stage(player)) {
            0 -> dialogue
                .npc(Quest.MORGAN, Expression.SHOCKED, "Please help us, adventurer!")
                .player("What's the problem?")
                .npc(Quest.MORGAN, "A vampire is terrorising our village. He lives in the",
                     "basement of the manor to the north. Can you stop him?")
                .options("No, vampires are scary!", {
                    it.newDialogue().npc(Quest.MORGAN, Expression.SAD, "I don't blame you.").open()
                }, ACCEPT, ::harlowDirections, "Have you got any tips on killing the vampire?", ::harlowDirections)
            Quest.COMPLETE -> dialogue
                .player("I've slain the vampire!")
                .npc(Quest.MORGAN, Expression.HAPPY, "Thank you! You'll always be a hero in our village.")
            else -> dialogue
                .npc(Quest.MORGAN, "How are you getting on?")
                .player("I'm still working on it.")
                .npc(Quest.MORGAN, Expression.SHOCKED, "Please hurry! We could be his next victims.")
        }
        dialogue.open()
    }

    private fun harlowDirections(player: Player) {
        player.newDialogue()
            .npc(Quest.MORGAN, "My friend Dr Harlow used to hunt vampires. He usually",
                 "drinks at the Blue Moon Inn in Varrock.")
            .npc(Quest.MORGAN, "Tell him Morgan sent you. He wouldn't want to see",
                 "his old friend killed by a vampire.")
            .player("I'll go and find him.")
            .then { Quest.advance(it, 0, Quest.STARTED) }
            .open()
    }

    fun harlow(player: Player) {
        val dialogue = player.newDialogue().npc(Quest.HARLOW, Expression.CONFUSED, "Buy me a drink, pleash...")
        when (Quest.stage(player)) {
            Quest.STARTED -> dialogue.options("No, you've had enough.", {
                it.newDialogue().player("I think you've had enough.").open()
            }, ASK_FOR_HELP, ::askForHelp)
            Quest.SPOKE_TO_HARLOW -> when {
                player.inventory.contains(Quest.STAKE) -> dialogue.options("No, you've had enough.", {},
                    "All right, here you go.", ::giveBeer)
                !player.inventory.contains(Quest.BEER) -> dialogue.player("I'll go and buy one.")
                else -> dialogue.player("Here you go.").then(::giveBeer)
            }
            else -> dialogue.player("I think you've had enough.")
        }
        dialogue.open()
    }

    private fun askForHelp(player: Player) {
        player.newDialogue()
            .npc(Quest.HARLOW, Expression.CONFUSED, "Morgan, you shay?")
            .player("A vampire is terrorising his village. He said you",
                    "might be able to tell me how to stop it.")
            .npc(Quest.HARLOW, Expression.CONFUSED, "Bring me a beer and I'll tell you what you need to know.")
            .then { Quest.advance(it, Quest.STARTED, Quest.SPOKE_TO_HARLOW) }
            .open()
    }

    private fun giveBeer(player: Player) {
        if (Quest.stage(player) != Quest.SPOKE_TO_HARLOW) return
        val inventory = player.inventory
        val hadStake = inventory.contains(Quest.STAKE)
        val received = if (hadStake) inventory.remove(Quest.BEER) else inventory.replace(Quest.BEER, Quest.STAKE)
        if (!received) {
            player.newDialogue().player("I'll go and buy a beer.").open()
            return
        }
        Entrances.itemBox(player, Quest.BEER, "You give a beer to Dr Harlow.") {
            if (hadStake) advice(it) else Entrances.itemBox(it, Quest.STAKE, "Dr Harlow hands you a stake.", ::advice)
        }
    }

    private fun advice(player: Player) {
        player.newDialogue()
            .npc(Quest.HARLOW, "A vampire will regenerate unless you finish it off",
                 "with a stake. Have a hammer to drive it in.")
            .npc(Quest.HARLOW, "Garlic will weaken it. Morgan likes garlic, so try",
                 "looking in his cupboard before you go to the manor.")
            .player("Thank you very much.")
            .open()
    }
}
