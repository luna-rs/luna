package game.bot.scripts.skills

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.zone.SubZone
import game.skill.mining.Ore
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonality
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A mine the bot gave up on because it found no free rock. The next mining pick skips it for [SKIP_FOR], so bots
 * that always take the top option don't walk straight back to the same crowded mine.
 */
internal class FullMine(val zone: SubZone, val time: Long = System.currentTimeMillis()) {
    fun isRecent(now: Long = System.currentTimeMillis()) = now - time < SKIP_FOR.inWholeMilliseconds

    companion object {
        val SKIP_FOR = 5.minutes
    }
}

internal var Bot.lastFullMine by Attr.nullableObj(FullMine::class)

/** Whether the bot recently gave up on [zone] because every rock was taken or empty. */
internal fun Bot.recentlyFull(zone: SubZone): Boolean = lastFullMine?.let { it.zone == zone && it.isRecent() } == true

/**
 * Session preferences for ordinary 377 mining. Competition changes the choice of rock, never its ownership or rolls.
 * Search grows only after the local cluster has no acceptable rocks, and contracts after mining starts.
 *
 * @author TheLining
 */
internal class MiningBehavior(personality: BotPersonality, preferred: Set<Ore>, ores: Set<Ore>) {
    enum class Style { KIND, MEAN, COMPLAINER, POWER_MINER, BALANCED }
    enum class Competition { YIELDING, BALANCED, ASSERTIVE }

    private val dexterity = personality.dexterity.coerceIn(0.0, 1.0)
    private val confidence = personality.confidence.coerceIn(0.0, 1.0)
    val style = styleOf(personality)
    private val tiers = preferred.associateWith { 0 } +
            (ores - preferred).sortedByDescending { it.level }.mapIndexed { index, ore -> ore to index + 1 }

    val competition = when {
        style == Style.KIND -> Competition.YIELDING
        style == Style.MEAN -> Competition.ASSERTIVE
        style == Style.COMPLAINER || style == Style.POWER_MINER -> Competition.BALANCED
        personality.kindness - personality.confidence >= 0.25 -> Competition.YIELDING
        personality.confidence - personality.kindness >= 0.25 -> Competition.ASSERTIVE
        else -> Competition.BALANCED
    }
    val searchTimeout = when (style) {
        Style.KIND -> 180.seconds
        Style.MEAN -> (150 + 60 * confidence).seconds
        Style.COMPLAINER -> (45 + 30 * (1.0 - dexterity)).seconds
        Style.POWER_MINER -> 60.seconds
        Style.BALANCED -> (90 + 90 * (1.0 - dexterity)).seconds
    }
    val crowdedSearchLimit = when (style) {
        Style.KIND -> 20
        Style.MEAN -> 30
        Style.COMPLAINER -> 4
        Style.POWER_MINER -> 6
        Style.BALANCED -> 10
    }
    /** Lost or taken rocks before the bot grumbles about it out loud. Complainers barely last two. */
    val complaintThreshold = when (style) {
        Style.COMPLAINER -> 2
        Style.MEAN -> 3
        Style.POWER_MINER, Style.BALANCED -> 4
        Style.KIND -> 5
    }
    private val localRadius = when (style) {
        Style.MEAN -> 8
        Style.POWER_MINER -> 4
        else -> LOCAL_RADIUS
    }
    var radius = localRadius
        private set

    /** Lower scores win. Balanced miners prefer free fallback ore to a contested preferred rock. */
    fun score(ore: Ore, competitors: Int, distance: Int): Double? {
        val tier = tiers[ore] ?: return null
        // Essence and other inexhaustible rocks can be shared without competing for their ore.
        val competing = if (ore.respawnTicks == null) 0 else competitors
        val penalty = when (competition) {
            Competition.YIELDING -> if (competing > 0) return null else 0
            Competition.BALANCED -> if (competing > 1) return null else 1_000
            Competition.ASSERTIVE -> {
                val limit = if (style == Style.MEAN && confidence >= 0.7) 3 else if (confidence < 0.4) 1 else 2
                if (competing > limit) return null
                if (style == Style.MEAN) 20 else 45
            }
        }
        val distanceWeight = if (style == Style.POWER_MINER) 3.0 else 0.5 + 1.5 * dexterity
        return tier * 100.0 + competing * penalty + distance * distanceWeight
    }

    /** Returns false once the bot has checked its entire visible neighborhood. */
    fun expand(): Boolean {
        if (radius == Position.VIEWING_DISTANCE) return false
        val step = when (style) {
            Style.COMPLAINER -> 5
            Style.KIND, Style.POWER_MINER -> 3
            else -> 4
        }
        radius = minOf(Position.VIEWING_DISTANCE, radius + step)
        return true
    }

    fun settle() { radius = localRadius }

    companion object {
        const val LOCAL_RADIUS = 6

        /** Mining styles specialize the bot's saved traits without replacing its global activity template. */
        fun styleOf(personality: BotPersonality): Style = when {
            personality.kindness >= 0.7 -> Style.KIND
            personality.dexterity >= 0.7 && personality.intelligence >= 0.6 -> Style.POWER_MINER
            personality.kindness <= 0.3 || personality.confidence - personality.kindness >= 0.35 -> Style.MEAN
            personality.social >= 0.7 && personality.dexterity < 0.7 -> Style.COMPLAINER
            else -> Style.BALANCED
        }
    }
}
