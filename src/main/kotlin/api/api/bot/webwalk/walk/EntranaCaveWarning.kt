package api.bot.webwalk.walk

import api.bot.webwalk.plan.PlanLeg
import api.predef.ext.*
import game.content.lostCity.EntranaCaveMonk
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Continues the native one-way Entrana cave warning after the ordinary ladder interaction.
 * Other ladders and unrelated dialogues retain their normal navigation behavior.
 *
 * @author lare96
 */
internal object EntranaCaveWarning {

    /**
     * Identifies only the warning-bearing surface ladder and its downward option.
     */
    fun handles(id: Int, position: Position, option: String): Boolean = id == EntranaCaveMonk.LADDER &&
        position == EntranaCaveMonk.LADDER_POSITION && option.equals("Climb-down", ignoreCase = true)

    /**
     * Advances at most the three native warning pages, accepts their exact choices and waits for actual arrival.
     * Moving away, dying or cancellation cannot leave an owned warning available for a later accidental acceptance.
     */
    suspend fun enter(context: LegContext, leg: PlanLeg): String? {
        val bot = context.bot
        val origin = bot.position
        var previous: NpcDialogue? = null
        var pages = 0
        var accepted = false
        val arrived = try {
            withTimeoutOrNull(LegContext.ACTION_TIMEOUT) {
                while (true) {
                    if (context.interrupted || bot.state != EntityState.ACTIVE || !bot.isAlive ||
                        bot.actions.getAll(MobDeathAction::class.java).any { !it.isFinished })
                        return@withTimeoutOrNull false
                    val atLanding = context.arrived(leg, context.landingSlack(leg))
                    if (atLanding && !bot.isLocked) return@withTimeoutOrNull true
                    if (!atLanding && bot.position != origin) return@withTimeoutOrNull false
                    if (!accepted && !atLanding) {
                        val dialogue = bot.overlays[DialogueInterface::class]
                        when (dialogue) {
                            is OptionDialogue -> {
                                if (!advanceDialogue(context)) return@withTimeoutOrNull false
                                accepted = true
                            }
                            is NpcDialogue -> {
                                if (dialogue.npcId != EntranaCaveMonk.NPC) return@withTimeoutOrNull false
                                if (dialogue !== previous) {
                                    if (++pages > 3 || !advanceDialogue(context)) return@withTimeoutOrNull false
                                    previous = dialogue
                                }
                            }
                            null -> {}
                            else -> return@withTimeoutOrNull false
                        }
                    }
                    delay(600)
                }
                @Suppress("UNREACHABLE_CODE")
                false
            } == true
        } finally {
            if (ownsDialogue(bot)) bot.overlays.closeWindows()
        }
        return if (arrived) null else "the Entrana cave monk's warning was interrupted or did not reach ${leg.to}"
    }

    /**
     * Sends normal client dialogue input only for this monk's speech or the exact native two-option warning.
     */
    internal fun advanceDialogue(context: LegContext): Boolean {
        val bot = context.bot
        val options = bot.overlays[OptionDialogue::class]
        if (options != null) {
            return options.options == listOf(EntranaCaveMonk.DECLINE, EntranaCaveMonk.AGREE) &&
                context.handler.widgets.clickDialogueOption(EntranaCaveMonk.AGREE)
        }
        return bot.overlays[NpcDialogue::class]?.npcId == EntranaCaveMonk.NPC && bot.output.sendContinueDialogue()
    }

    /**
     * Limits cancellation cleanup to the warning this continuation owns.
     */
    internal fun ownsDialogue(bot: Bot): Boolean = bot.overlays[NpcDialogue::class]?.npcId == EntranaCaveMonk.NPC ||
        bot.overlays[OptionDialogue::class]?.options == listOf(EntranaCaveMonk.DECLINE, EntranaCaveMonk.AGREE)
}
