package game.content.barrows

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression
import kotlin.random.Random

/**
 * Strange Old Man dialogue from barrows_oldman.rs2 at contribution revision 0e7d7a8.
 * Uses Luna's existing dialogue expressions and inventory-full message.
 *
 * @author lare96
 */
fun scream(player: Player) {
    player.newDialogue().npc(2024, "AAAAAAAAARRRRRRGGGGGHHHHHHHH!")
        .options("What's wrong?", {
            it.newDialogue().player(Expression.QUIZZICAL, "What's wrong?")
                .then { next -> scream(next) }.open()
        }, "I'll leave you to it then...", {
            it.newDialogue().player(Expression.QUIZZICAL, "I'll leave you to it then...").open()
        }).open()
}

npc1(2024) {
    when (Random.nextInt(5)) {
        0 -> scream(plr)
        1 -> plr.newDialogue().npc(2024, "Pst, wanna hear a secret?")
            .options("Sure!", {
                it.newDialogue().player("Sure!").npc(2024, "They're not normal!").open()
            }, "No thanks. ", {
                it.newDialogue().player("No thanks.").open()
            }).open()
        2 -> plr.newDialogue().npc(2024, "What? I didn't ask for a book!")
            .then { it.inventory.add(Item(4707)) }.open()
        3 -> plr.newDialogue().npc(2024, "Knock knock.")
            .player("Who's there?")
            .npc(2024, "A big scary monster HAHAHAHAHAHAHAHAHAHA!")
            .player("Okay...").open()
        4 -> plr.newDialogue().npc(2024, "Dig, dig, dig.").open()
    }
}
