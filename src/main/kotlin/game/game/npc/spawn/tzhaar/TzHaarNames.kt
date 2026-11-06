package game.npc.spawn.tzhaar

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * How the TzHaar of the city name a player, and the talk about that name they all share.
 */
object TzHaarNames {

    /**
     * A TzHaar caste, with how a TzHaar describes a player of it.
     */
    enum class Caste(val description: String) {
        MEJ("you user of mystic powers no?"),
        KET("you tough and strong no?"),
        XIL("you agile and quick no?"),
        HUR("you skilled at making things no?");

        /**
         * The caste as it appears in a name, such as "Ket".
         */
        val formattedName = name.lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * The player's caste: the one whose pair of levels adds up highest. Magic and Prayer make a Mej, Strength and
     * Defence a Ket, Attack and Agility a Xil, Crafting and Smithing a Hur. A tie goes to the caste listed first.
     */
    fun caste(plr: Player): Caste {
        val totals = listOf(
            Caste.MEJ to plr.magic.staticLevel + plr.prayer.staticLevel,
            Caste.KET to plr.strength.staticLevel + plr.defence.staticLevel,
            Caste.XIL to plr.attack.staticLevel + plr.agility.staticLevel,
            Caste.HUR to plr.crafting.staticLevel + plr.smithing.staticLevel
        )
        var best = totals.first()
        for (total in totals) {
            if (total.second > best.second) {
                best = total
            }
        }
        return best.first
    }

    /**
     * The player's caste title, such as "JalYt-Ket".
     */
    fun title(plr: Player) = "JalYt-${caste(plr).formattedName}"

    /**
     * The player's TzHaar name, such as "JalYt-Ket-Name".
     */
    fun name(plr: Player) = "${title(plr)}-${plr.username}"

    /**
     * The "What did you call me?" talk. [selfLines] are what [npc] answers to "What are you then?".
     */
    fun whatDidYouCallMe(plr: Player, npc: Int, vararg selfLines: String) {
        whatDidYouCallMe(plr, npc) {
            var dialogue = it.newDialogue().player("What are you then?")
            for (line in selfLines) {
                dialogue = dialogue.npc(npc, *DialogueUtils.splitChatLines(line))
            }
            dialogue.open()
        }
    }

    /**
     * The "What did you call me?" talk. [whatAreYou] runs when the player asks [npc] "What are you then?".
     */
    fun whatDidYouCallMe(plr: Player, npc: Int, whatAreYou: (Player) -> Unit) {
        val title = title(plr)
        plr.newDialogue()
            .npc(npc, *DialogueUtils.splitChatLines("Are you not ${name(plr)}?"))
            .options("What's a '$title'?", { explainTitle(it, npc, whatAreYou) },
                     "I guess so...", {
                         it.newDialogue()
                             .player("I guess so...")
                             .npc(npc, "Well then, no problems.")
                             .open()
                     },
                     "No I'm not!", {
                         it.newDialogue()
                             .player("No I'm not!")
                             .npc(npc, "What ever you say, crazy JalYt!")
                             .open()
                     })
            .open()
    }

    /**
     * Explains the player's caste title, then lets them ask what [npc] is.
     */
    private fun explainTitle(plr: Player, npc: Int, whatAreYou: (Player) -> Unit) {
        val title = title(plr)
        plr.newDialogue()
            .player("What's a '$title'?")
            .npc(npc, "That what you are... ${caste(plr).description}")
            .player("Well yes I suppose I am...")
            .npc(npc, "Then you $title!")
            .options("What are you then?", { whatAreYou(it) }, "Thanks for explaining it.", {
                it.newDialogue().player("Thanks for explaining it.").open()
            })
            .open()
    }
}
