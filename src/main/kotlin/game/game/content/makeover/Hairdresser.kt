package game.content.makeover

import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.dialogue.Expression

/**
 * The hairdresser in Falador, who cuts hair, and shaves men, for a fee set by each [HairStyle].
 *
 * @author TheLining
 */
object Hairdresser {

    /**
     * The hairdresser.
     */
    const val HAIRDRESSER = 598

    /**
     * Coins, which pay for the work.
     */
    private const val COINS = 995

    /**
     * How close the hairdresser must be to speak to the player after the work is done.
     */
    private const val TALK_DISTANCE = 5

    /**
     * Starts the talk with [hairdresser] that [plr] clicked on. Only men are offered a shave.
     */
    fun talk(plr: Player, hairdresser: Npc) {
        val id = hairdresser.id
        if (plr.appearance.isFemale) {
            plr.newDialogue()
                .npc(id, Expression.HAPPY, "Good afternoon madam. In need of a haircut are", "we?")
                .options("I'd like a haircut please.", { haircut(it, hairdresser, HairStyle.WOMAN_HAIR) },
                         "No thank you.", { noThanks(it, hairdresser) })
                .open()
        } else {
            plr.newDialogue()
                .npc(id, Expression.HAPPY, "Good afternoon sir. In need of a haircut are we?", "Perhaps a shave too?")
                .options("I'd like a haircut please.", { haircut(it, hairdresser, HairStyle.MAN_HAIR) },
                         "I'd like a shave please.", { shave(it, hairdresser) },
                         "No thank you.", { noThanks(it, hairdresser) })
                .open()
        }
    }

    /**
     * The player asks for a haircut, and the hairdresser opens the [style] brochure if they can pay for it.
     */
    private fun haircut(plr: Player, hairdresser: Npc, style: HairStyle) {
        val id = hairdresser.id
        val dialogue = plr.newDialogue().player(Expression.HAPPY, "I'd like a haircut please.")
        if (style == HairStyle.WOMAN_HAIR) {
            dialogue.npc(id, Expression.HAPPY,
                         "Certainly madam. I cut women's hair at the",
                         "bargain rate of only 2000 gold coins. I'll even",
                         "throw in a free recolour!")
        } else {
            dialogue.npc(id, Expression.HAPPY,
                         "Certainly sir. I cut men's hair at the bargain",
                         "rate of only 1000 gold coins. I'll even throw in",
                         "a free recolour!")
        }
        if (plr.inventory.computeAmountForId(COINS) < style.fee) {
            refuse(dialogue, hairdresser, style).open()
            return
        }
        dialogue.npc(id, Expression.HAPPY,
                     "Please select the hairstyle and colour you would",
                     "like from this brochure.")
            .then { it.overlays.open(HairdresserInterface(style, hairdresser)) }
            .open()
    }

    /**
     * The player asks for a shave, and the hairdresser opens the beard brochure if they can pay for it.
     */
    private fun shave(plr: Player, hairdresser: Npc) {
        val id = hairdresser.id
        val dialogue = plr.newDialogue()
            .player(Expression.HAPPY, "I'd like a shave please.")
            .npc(id, Expression.HAPPY,
                 "Certainly sir. I shave facial hair at the",
                 "bargain rate of only 1000 gold coins. I'll even",
                 "throw in a free recolour!")
        if (plr.inventory.computeAmountForId(COINS) < HairStyle.BEARD.fee) {
            refuse(dialogue, hairdresser, HairStyle.BEARD).open()
            return
        }
        dialogue.npc(id, Expression.HAPPY, "Please select the facial hair you would like from", "this brochure.")
            .then { it.overlays.open(HairdresserInterface(HairStyle.BEARD, hairdresser)) }
            .open()
    }

    /**
     * The player turns the hairdresser down.
     */
    private fun noThanks(plr: Player, hairdresser: Npc) {
        plr.newDialogue()
            .player(Expression.NEUTRAL, "No thank you.")
            .npc(hairdresser.id, Expression.NEUTRAL, "Very well. Come back if you change your mind.")
            .open()
    }

    /**
     * Adds the lines where the player can't pay for [style] to [dialogue].
     */
    private fun refuse(dialogue: DialogueQueueBuilder, hairdresser: Npc, style: HairStyle) =
        dialogue.player(Expression.SAD, "I don't have ${style.fee} gold coins on me...")
            .npc(hairdresser.id, Expression.ANGRY, "Well, come back when you do. I'm not running a", "charity here!")

    /**
     * Picks the style behind button number [index] on the open brochure.
     */
    fun selectStyle(plr: Player, style: HairStyle, index: Int) {
        val window = brochure(plr, style) ?: return
        window.kit = style.kits[index]
    }

    /**
     * Picks hair colour [colour] on the open brochure.
     */
    fun selectColour(plr: Player, style: HairStyle, colour: Int) {
        val window = brochure(plr, style) ?: return
        window.colour = colour
    }

    /**
     * Gives the player the style and colour picked on the open [style] brochure, for the brochure's fee.
     */
    fun confirm(plr: Player, style: HairStyle) {
        val window = brochure(plr, style) ?: return
        val kit = window.kit
        val colour = window.colour
        if (kit == null || colour == null) {
            plr.sendMessage("You must make a selection from both categories.")
            return
        }
        plr.overlays.closeWindows()
        if (plr.appearance.get(PlayerAppearance.GENDER) != style.gender) {
            return
        }
        val hairdresser = window.hairdresser
        if (plr.inventory.computeAmountForId(COINS) < style.fee || !plr.inventory.remove(Item(COINS, style.fee))) {
            if (near(plr, hairdresser)) {
                refuse(plr.newDialogue(), hairdresser, style).open()
            }
            return
        }
        plr.appearance.set(style.part, kit)
        plr.appearance.set(PlayerAppearance.HAIR_COLOR, colour)
        plr.flags.flag(UpdateFlag.APPEARANCE)
        if (near(plr, hairdresser)) {
            plr.newDialogue().npc(hairdresser.id, Expression.HAPPY, style.thanks).open()
        }
    }

    /**
     * Returns the [style] brochure if [plr] has it open.
     */
    private fun brochure(plr: Player, style: HairStyle): HairdresserInterface? {
        val window = plr.overlays.getOverlay(HairdresserInterface::class.java)
        return if (window?.style == style) window else null
    }

    /**
     * Returns `true` if [hairdresser] is close enough to [plr] to speak to them.
     */
    private fun near(plr: Player, hairdresser: Npc) =
        hairdresser.state == EntityState.ACTIVE && plr.position.isWithinDistance(hairdresser.position, TALK_DISTANCE)
}
