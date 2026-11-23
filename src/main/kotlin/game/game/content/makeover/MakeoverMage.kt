package game.content.makeover

import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.game.model.mob.dialogue.Expression

/**
 * The make-over mage west of Falador, who changes a player's gender and skin colour for [FEE] coins. The mage switches
 * between a male and a female form every [SWAP_TICKS] ticks.
 *
 * @author TheLining
 */
object MakeoverMage {

    /**
     * The mage's male form.
     */
    const val MALE_MAGE = 599

    /**
     * The mage's female form.
     */
    const val FEMALE_MAGE = 2676

    /**
     * What a make-over costs.
     */
    const val FEE = 3000

    /**
     * The ticks between the mage's changes of form.
     */
    const val SWAP_TICKS = 17

    /**
     * The male portrait and its caption.
     */
    val MALE_BUTTONS = listOf(5568, 5557)

    /**
     * The female portrait and its caption.
     */
    val FEMALE_BUTTONS = listOf(5569, 5559)

    /**
     * The first skin colour button. The buttons are in skin colour order.
     */
    const val FIRST_SKIN_BUTTON = 5549

    /**
     * How many skin colours there are to pick from.
     */
    const val SKIN_COLOURS = 8

    /**
     * The confirm button.
     */
    const val CONFIRM_BUTTON = 5544

    /**
     * Coins, which pay for the make-over.
     */
    private const val COINS = 995

    /**
     * How close the mage must be to speak to the player after the make-over.
     */
    private const val TALK_DISTANCE = 5

    /**
     * The clean-shaven jaw that a player changed into a man starts with.
     */
    private const val CLEAN_SHAVEN = 14

    /**
     * What the mage shouts on changing form.
     */
    private val SHOUTS = listOf("Ahah!", "Ooh!")

    /**
     * The woman's identity kit closest to each man's kit, for the head, body, arms, hands, legs and feet.
     */
    private val MAN_TO_WOMAN = mapOf(
        0 to 45, 1 to 47, 2 to 48, 3 to 49, 4 to 50, 5 to 51, 6 to 52, 7 to 53, 8 to 54,
        18 to 56, 19 to 56, 20 to 56, 21 to 56, 22 to 56, 23 to 56, 24 to 56, 25 to 56,
        26 to 61, 27 to 63, 28 to 62, 29 to 65, 30 to 64, 31 to 63,
        33 to 67, 34 to 68,
        36 to 70, 37 to 71, 38 to 72, 39 to 76, 40 to 75,
        42 to 79, 43 to 80
    )

    /**
     * The man's identity kit closest to each woman's kit, for the head, body, arms, hands, legs and feet.
     */
    private val WOMAN_TO_MAN = mapOf(
        45 to 0, 46 to 0, 47 to 1, 48 to 2, 49 to 3, 50 to 4, 51 to 5, 52 to 6, 53 to 7, 54 to 8,
        56 to 18, 57 to 18, 58 to 18, 59 to 18, 60 to 18,
        61 to 26, 62 to 27, 63 to 28, 64 to 29, 65 to 29,
        67 to 33, 68 to 34,
        70 to 36, 71 to 37, 72 to 38, 73 to 36, 74 to 36, 75 to 40, 76 to 39, 77 to 36,
        79 to 42, 80 to 43
    )

    /**
     * Starts the talk with [mage] that [plr] clicked on.
     */
    fun talk(plr: Player, mage: Npc) {
        plr.newDialogue()
            .npc(mage.id, Expression.HAPPY,
                 "Hello there! I am known as the make-over mage!",
                 "I have spent many years researching magics that",
                 "can change your physical appearance!")
            .npc(mage.id, Expression.HAPPY,
                 "I can alter your physical form for a small fee of",
                 "only 3000 gold coins! Would you like me to",
                 "perform my magics upon you?")
            .options("Tell me more about this 'make-over'.", { tellMore(it, mage) },
                     "Sure. Do it.", { sure(it, mage) },
                     "No thanks.", { noThanks(it, mage) })
            .open()
    }

    /**
     * The mage explains how the make-over works.
     */
    private fun tellMore(plr: Player, mage: Npc) {
        plr.newDialogue()
            .player(Expression.QUIZZICAL, "Tell me more about this 'make-over'.")
            .npc(mage.id, Expression.HAPPY,
                 "Why, of course! Basically, and I will try and",
                 "explain this so that you will understand it",
                 "correctly,")
            .npc(mage.id, Expression.HAPPY,
                 "I use my secret magical technique to melt your",
                 "body down into a puddle of its elements.")
            .npc(mage.id, Expression.HAPPY,
                 "When I have broken down all trace of your body, I",
                 "then rebuild it into the form I am thinking of!")
            .npc(mage.id, Expression.CONFUSED, "Or, you know, somewhere vaguely close enough", "anyway.")
            .player(Expression.QUIZZICAL, "Uh... that doesn't sound particularly safe to", "me...")
            .npc(mage.id, Expression.HAPPY,
                 "It's as safe as houses! Why, I have only had",
                 "thirty-six major accidents this month!")
            .npc(mage.id, Expression.QUIZZICAL,
                 "So what do you say? Feel like a change? It's only",
                 "3000 coins.")
            .options("Sure. Do it.", { sure(it, mage) },
                     "No thanks.", { noThanks(it, mage) })
            .open()
    }

    /**
     * The player asks for a make-over, and the mage opens the make-over window if they can pay for it.
     */
    private fun sure(plr: Player, mage: Npc) {
        val dialogue = plr.newDialogue().player(Expression.HAPPY, "Sure. Do it.")
        if (plr.inventory.computeAmountForId(COINS) < FEE) {
            dialogue.npc(mage.id, Expression.HAPPY, "Great. Come back with 3000 coins, and I'll get", "started.")
                .open()
            return
        }
        dialogue.npc(mage.id, Expression.HAPPY,
                     "You of course agree that if by some accident you",
                     "are turned into a frog you have no rights for",
                     "compensation or refund.")
            .then { it.overlays.open(MakeoverMageInterface(mage)) }
            .open()
    }

    /**
     * The player turns the mage down.
     */
    private fun noThanks(plr: Player, mage: Npc) {
        plr.newDialogue()
            .player(Expression.ANGRY, "No thanks. I'm happy as Saradomin made me.")
            .npc(mage.id, Expression.SAD, "Ehhh... suit yourself.")
            .open()
    }

    /**
     * Picks [gender] on the open make-over window.
     */
    fun selectGender(plr: Player, gender: Int) {
        plr.overlays.getOverlay(MakeoverMageInterface::class.java)?.gender = gender
    }

    /**
     * Picks skin colour [colour] on the open make-over window.
     */
    fun selectSkin(plr: Player, colour: Int) {
        plr.overlays.getOverlay(MakeoverMageInterface::class.java)?.skin = colour
    }

    /**
     * Gives the player the gender and skin colour picked on the open make-over window, for [FEE] coins.
     */
    fun confirm(plr: Player) {
        val window = plr.overlays.getOverlay(MakeoverMageInterface::class.java) ?: return
        val appearance = plr.appearance
        val gender = window.gender
        val skin = window.skin ?: appearance.get(PlayerAppearance.SKIN_COLOR)
        if (gender == appearance.get(PlayerAppearance.GENDER) && skin == appearance.get(PlayerAppearance.SKIN_COLOR)) {
            plr.sendMessage("You haven't changed anything.")
            return
        }
        plr.overlays.closeWindows()
        if (plr.inventory.computeAmountForId(COINS) < FEE || !plr.inventory.remove(Item(COINS, FEE))) {
            plr.sendMessage("You need $FEE coins to pay for the make-over.")
            return
        }
        val values = appearance.toArray()
        if (gender != values[PlayerAppearance.GENDER]) {
            changeGender(values, gender)
        }
        values[PlayerAppearance.SKIN_COLOR] = skin
        appearance.setValues(values)
        plr.flags.flag(UpdateFlag.APPEARANCE)
        afterwards(plr, window.mage)
    }

    /**
     * Turns [values] into [gender]'s, giving each body part the closest kit that [gender] has.
     */
    private fun changeGender(values: IntArray, gender: Int) {
        val female = gender == PlayerAppearance.GENDER_FEMALE
        val kits = if (female) MAN_TO_WOMAN else WOMAN_TO_MAN
        for (index in PlayerAppearance.HEAD..PlayerAppearance.BOOTS) {
            values[index] = if (index == PlayerAppearance.BEARD) {
                if (female) 0 else CLEAN_SHAVEN
            } else {
                kits.getValue(values[index])
            }
        }
        values[PlayerAppearance.GENDER] = gender
    }

    /**
     * The mage checks how the make-over went, if they are still close by.
     */
    private fun afterwards(plr: Player, mage: Npc) {
        if (mage.state != EntityState.ACTIVE || !plr.position.isWithinDistance(mage.position, TALK_DISTANCE)) {
            return
        }
        val id = mage.id
        val dialogue = plr.newDialogue()
        when (rand(0, 3)) {
            0 -> dialogue.npc(id, Expression.SHOCKED, "Whew! That was lucky!")
                .player(Expression.NEUTRAL, "What was?")
                .npc(id, Expression.HAPPY, "Nothing! It's all fine! You seem alive anyway!")
            1 -> dialogue.npc(id, Expression.SHOCKED, "Woah!")
                .player(Expression.QUIZZICAL, "What?")
                .npc(id, Expression.SHOCKED, "You still look human!")
            2 -> dialogue.npc(id, Expression.QUIZZICAL,
                              "Hmm... you didn't feel any unexpected growths",
                              "anywhere around your head just then did you?")
                .player(Expression.QUIZZICAL, "Uh... no...?")
                .npc(id, Expression.HAPPY, "Good, good! I was worried for a second there!")
            else -> dialogue.npc(id, Expression.HAPPY,
                                 "Two arms... two legs... one head... it seems that",
                                 "spell finally worked okay!")
        }
        dialogue.player(Expression.CONFUSED, "Uh... Thanks, I guess.").open()
    }

    /**
     * Switches [mage] to their other form.
     */
    fun swap(mage: Npc) {
        mage.transform(if (mage.id == MALE_MAGE) FEMALE_MAGE else MALE_MAGE)
        mage.speak(SHOUTS.random())
    }
}
