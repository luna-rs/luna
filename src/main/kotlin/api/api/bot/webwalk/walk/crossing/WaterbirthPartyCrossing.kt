package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import engine.combat.prayer.CombatPrayer
import game.content.waterbirth.Waterbirth
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Two real bots open both pressure doors together before taking different corridors and striking reciprocal props.
 * The web leg spans the whole cooperative obstacle, so either branch legitimately ends at its common landing.
 * Missing partners, native route failures, spent supplies and cancellation stop the attempt within three minutes.
 *
 * @author lare96
 */
object WaterbirthPartyCrossing : CrossingHandler {
    const val ID = "waterbirth_party"
    const val READY = "waterbirth_party_ready"
    val FOYER = Position(2488, 10147)
    val CENTER = Position(2544, 10143)

    fun ready(bot: Bot): Boolean = bot.combat.weapon.isRanged && bot.skill(Skill.PRAYER).staticLevel >= 43

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val bot = context.bot
        if (context.arrived(leg, 0)) return null
        if (!ready(bot)) return "the cooperative Waterbirth route requires an owned ranged loadout and protection prayers"
        context.walk(leg.from, 0)?.let { return it }
        val party = WaterbirthParties.join(bot.world, bot, System.currentTimeMillis())
        val role = party.role(bot)
        var success = false
        var assisted: Npc? = null
        var prayer = CombatPrayer.PROTECT_FROM_MELEE
        fun active(): Boolean = !party.failed && System.currentTimeMillis() < party.deadline &&
            listOfNotNull(party.north, party.middle).all { it.state == EntityState.ACTIVE && it.isAlive } &&
            !(bot.combat.inCombat() && bot.combat.lastAttackReceived?.attacker is Player)

        suspend fun maintain(): Boolean {
            if (!active() || bot.healthPercent < 35) return false
            bot.combat.prayers.activateIfOff(prayer)
            context.handler.potions.drink(false, false, prayerBelow = 15)
            if (bot.healthPercent < 65) {
                val food = bot.inventory.withIndex().firstOrNull { it.value?.id in Food.ID_TO_FOOD } ?: return false
                if (!context.handler.inventory.eat(food.index, food.value!!.id)) return false
            }
            return active()
        }

        suspend fun await(milliseconds: Long, predicate: () -> Boolean): Boolean {
            val until = minOf(party.deadline, System.currentTimeMillis() + milliseconds)
            while (System.currentTimeMillis() < until) {
                if (predicate()) return true
                if (!maintain()) return false
                delay(500)
            }
            return predicate()
        }

        suspend fun walk(position: Position): Boolean = coroutineScope {
            if (!maintain()) return@coroutineScope false
            val walking = async { context.walk(position, 0) }
            try {
                while (!walking.isCompleted) {
                    if (!maintain()) { walking.cancelAndJoin(); return@coroutineScope false }
                    delay(300)
                }
                walking.await() == null && active()
            } finally { walking.cancel() }
        }

        try {
            if (!await(45_000) { party.middle != null }) return "no real partner joined the Waterbirth passage"
            val north = Waterbirth.Gate.NORTH
            val middle = Waterbirth.Gate.MIDDLE
            if (!walk(north.pads[role]) || !await(15_000) { Waterbirth.open(bot.world, north) })
                return "the pair did not open the northern pressure door"
            party.advance(WaterbirthParties.Phase.NORTH_PADS, WaterbirthParties.Phase.MIDDLE_PADS)
            if (!walk(middle.pads[role]) || !await(15_000) { Waterbirth.open(bot.world, middle) })
                return "the pair did not open the middle pressure door"
            party.advance(WaterbirthParties.Phase.MIDDLE_PADS, WaterbirthParties.Phase.CORRIDORS)
            val gate = if (role == 0) north else middle
            val own = if (role == 0) Waterbirth.Support.NORTH else Waterbirth.Support.WEST
            val other = if (role == 0) Waterbirth.Support.WEST else Waterbirth.Support.NORTH
            prayer = if (role == 0) CombatPrayer.PROTECT_FROM_MELEE else CombatPrayer.PROTECT_FROM_MAGIC
            if (!walk(gate.east) || !walk(own.outside)) return "the assigned Waterbirth corridor was not reachable"
            party.atProps += bot
            if (!await(45_000) { party.atProps.size == 2 }) return "the Waterbirth partner did not reach the opposing corridor"
            party.advance(WaterbirthParties.Phase.CORRIDORS, WaterbirthParties.Phase.SUPPORTS)
            if (!Waterbirth.open(bot.world, other)) {
                assisted = context.findNpc(other.position, setOf(other.npc), 0) ?: return "the opposing Waterbirth prop was missing"
                if (!maintain() || !context.handler.interactions.interactOnLeg(2, assisted))
                    return "could not strike the partner's Waterbirth support"
            }
            if (!await(30_000) { Waterbirth.open(bot.world, own) && Waterbirth.open(bot.world, other) })
                return "both Waterbirth supports were not knocked down"
            if (bot.combat.target === assisted) bot.combat.target = null
            party.advance(WaterbirthParties.Phase.SUPPORTS, WaterbirthParties.Phase.ARRIVAL)
            if (!walk(CENTER)) return "the central Waterbirth room was not reached"
            success = true
            return null
        } finally {
            if (assisted != null && bot.combat.target === assisted) bot.combat.target = null
            WaterbirthParties.leave(bot.world, party, bot, success)
        }
    }
}
