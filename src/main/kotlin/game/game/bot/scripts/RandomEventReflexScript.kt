package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import api.bot.script.ReflexBotScript
import api.predef.world
import game.content.randomEvents.RandomEvents
import game.content.randomEvents.VisitorRandomEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Responds to personal random-event visitors through the normal NPC interaction handler.
 * Intelligence and dexterity determine one ignore decision per encounter. Dexterous bots
 * always intend to respond; danger, locks and unreachable visitors can still prevent success.
 * Reaction time passes while normal work continues, then the reflex briefly pauses that work.
 * Ignored visitors keep their native warnings and consequences.
 *
 * @author lare96
 */
class RandomEventReflexScript @JvmOverloads constructor(
    bot: Bot,
    private val random: Random = Random.Default
) : ReflexBotScript(bot) {
    companion object {
        /**
         * Probability of deliberately ignoring a visitor, using normalized personality traits.
         * Luna considers dexterity of at least 0.7 dexterous. Below that threshold, both traits
         * continuously reduce the chance from a maximum of 90 percent.
         */
        internal fun ignoreChance(intelligence: Double, dexterity: Double): Double {
            val intelligenceScore = intelligence.coerceIn(0.0, 1.0)
            val dexterityScore = dexterity.coerceIn(0.0, 1.0)
            if (dexterityScore >= 0.7) return 0.0
            return 0.9 * (1.0 - intelligenceScore) * (1.0 - dexterityScore / 0.7)
        }
    }

    /**
     * The exact visitor instance owns the decision. Retries cannot reroll an ignored encounter;
     * replacement encounters receive their own decision even when they have the same NPC type.
     */
    private var observed: VisitorRandomEvent? = null
    private var ignoring = false

    /**
     * Earliest response tick, also used to space failed interaction retries without new decisions.
     */
    private var respondAt = 0L

    override fun shouldReact(): Boolean {
        val encounter = (RandomEvents.encounter(bot) as? VisitorRandomEvent)
            ?.takeIf { it.awaitingResponse && it.npc.state == EntityState.ACTIVE }
        if (encounter == null) {
            observed = null
            return false
        }
        if (observed !== encounter) {
            observed = encounter
            val intelligence = bot.personality.intelligence
            val dexterity = bot.personality.dexterity
            ignoring = random.nextDouble() < ignoreChance(intelligence, dexterity)
            val maximumDelay = (9 - 4 * dexterity - 2 * intelligence).roundToInt().coerceIn(2, 9)
            respondAt = world.currentTick + random.nextInt(2, maximumDelay + 1)
        }
        return !ignoring && world.currentTick >= respondAt && canRespond(encounter)
    }

    override suspend fun run(): Boolean {
        val encounter = observed ?: return true
        if (ignoring || !canRespond(encounter)) return true
        respondAt = world.currentTick + 3
        withTimeoutOrNull(6.seconds) {
            if (bot.overlays.hasWindow()) {
                output.sendCloseInterface()
                waitFor(2.seconds) { !bot.overlays.hasWindow() || !canRespond(encounter) }
            }
            if (canRespond(encounter) && !bot.overlays.hasWindow()) {
                handler.interactions.interact(1, encounter.npc) {
                    canRespond(encounter) && !bot.overlays.hasWindow()
                }
            }
        }
        return true
    }

    /**
     * Rechecks ownership, native response eligibility and urgent safety after every suspension.
     * Eating and combat recovery retain priority over collecting a visitor's gift.
     */
    private fun canRespond(encounter: VisitorRandomEvent): Boolean =
        bot.state == EntityState.ACTIVE && RandomEvents.encounter(bot) === encounter &&
            encounter.awaitingResponse && encounter.npc.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.emotions.isNervousAboutHp
}
