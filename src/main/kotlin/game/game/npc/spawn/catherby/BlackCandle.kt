package game.npc.spawn.catherby

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The Catherby candle maker's talk, and the black candle he makes for a bucket of wax.
 *
 * @author TheLining
 */
object BlackCandle {

    /**
     * The candle maker.
     */
    const val CANDLE_MAKER = 562

    /**
     * A bucket of wax.
     */
    const val BUCKET_OF_WAX = 30

    /**
     * A black candle.
     */
    const val BLACK_CANDLE = 38

    /**
     * Whether this player has asked for a black candle and not yet brought the wax.
     */
    var Player.wantsBlackCandle by Attr.boolean().persist("wants_black_candle")

    /**
     * Registers the saved attribute before any player is loaded.
     */
    fun initialize() = Unit

    /**
     * Talks to [plr], asking for the wax first if they're waiting on a black candle.
     */
    fun talk(plr: Player) {
        if (plr.wantsBlackCandle) {
            askForWax(plr)
            return
        }
        // TODO Once Merlin's Crystal is added, only players Morgan Le Faye has sent for a black candle may ask.
        plr.newDialogue()
            .npc(CANDLE_MAKER, Expression.HAPPY, "Hi! Would you be interested in some of my fine candles?")
            .options("Have you got any black candles?", { askForBlackCandle(it) }, "Yes please.", {
                it.newDialogue()
                    .player("Yes please.")
                    .then { other -> GeneralStore.open(other, "Candle Shop") }
                    .open()
            }, "No thank you.", {
                it.newDialogue().player(Expression.HAPPY, "No thank you.").open()
            })
            .open()
    }

    /**
     * [plr] asks for a black candle, and is told to bring a bucket of wax.
     */
    private fun askForBlackCandle(plr: Player) {
        plr.wantsBlackCandle = true
        plr.newDialogue()
            .player(Expression.QUIZZICAL, "Have you got any black candles?")
            .npc(CANDLE_MAKER, Expression.SHOCKED, "BLACK candles???")
            .npc(CANDLE_MAKER, Expression.CONFUSED, "Hmmm. In the candle making trade, we have a tradition that it's " +
                "very bad luck to make black candles.")
            .npc(CANDLE_MAKER, Expression.CONFUSED, "VERY bad luck.")
            .player(Expression.ANGRY, "I will pay good money for one...")
            .npc(CANDLE_MAKER, Expression.CONFUSED, "I still dunno...")
            .npc(CANDLE_MAKER, Expression.CONFUSED, "Tell you what. I'll supply you with a black candle...")
            .npc(CANDLE_MAKER, Expression.CONFUSED, "IF you can bring me a bucket FULL of wax.")
            .open()
    }

    /**
     * Asks [plr] for the wax, and swaps it for a black candle if they have it.
     */
    private fun askForWax(plr: Player) {
        val dialogue = plr.newDialogue().npc(CANDLE_MAKER, Expression.QUIZZICAL, "Have you got any wax yet?")
        if (!plr.inventory.contains(BUCKET_OF_WAX)) {
            dialogue.player(Expression.SAD, "Nope, not yet.").open()
            return
        }
        dialogue.player(Expression.HAPPY, "Yes, I have some now.")
            .then {
                if (it.inventory.remove(Item(BUCKET_OF_WAX))) {
                    it.inventory.add(Item(BLACK_CANDLE))
                    it.wantsBlackCandle = false
                    it.newDialogue().text("You exchange the wax with the candle maker for a black candle.").open()
                }
            }
            .open()
    }
}
