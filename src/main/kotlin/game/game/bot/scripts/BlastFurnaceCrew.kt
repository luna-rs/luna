package game.bot.scripts

import api.predef.*
import game.content.blastFurnace.BlastFurnace
import game.content.blastFurnace.BlastFurnaceWorkAction
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import java.util.WeakHashMap

/**
 * Shares live and travelling furnace assignments on the game thread.
 * Claims are transient: script cleanup, missing ownership and bounded leases release abandoned stations.
 *
 * @author lare96
 */
object BlastFurnaceCrew {
    private const val RESERVATION_MILLIS = 10_000L
    private const val TRAVEL_MILLIS = 20 * 60_000L
    private const val ACTIVE_MILLIS = 120_000L
    private val crews = WeakHashMap<World, MutableMap<Bot, Assignment>>()

    /**
     * Associates each reserved station with the native skill it trains.
     *
     * @author lare96
     */
    enum class Role(val skillId: Int) {
        SMELTER(SKILL_SMITHING), PUMP(SKILL_STRENGTH), PEDALS(SKILL_AGILITY),
        STOVE(SKILL_FIREMAKING), REPAIR(SKILL_CRAFTING)
    }

    /**
     * A detached view of crew intent, with physical arrival evaluated at query time.
     *
     * @author lare96
     */
    data class Member(val bot: Bot, val role: Role, val arrived: Boolean)

    /**
     * Associates a role with its exact owning script rather than only a character name.
     *
     * @author lare96
     */
    private class Assignment(val script: BlastFurnaceBotScript, val role: Role, var deadline: Long,
                             val reservedUntil: Long, var leaseUntil: Long)

    private fun crew(bot: Bot, now: Long = System.currentTimeMillis()): MutableMap<Bot, Assignment> {
        val crew = crews.getOrPut(bot.world) { linkedMapOf() }
        crew.entries.removeIf { (member, assignment) ->
            val script = assignment.script
            member.state != EntityState.ACTIVE || !member.isAlive || member.world !== bot.world ||
                now >= assignment.deadline || now >= assignment.leaseUntil || script.trainingComplete() ||
                script.isTerminated() || script.isFinished() || script.isPaused() ||
                (member.scriptStack.current() !== script &&
                    !(!script.isRunning() && now < assignment.reservedUntil))
        }
        return crew
    }

    /**
     * Returns current assignments, including reservations for bots preparing or travelling.
     */
    fun members(bot: Bot): List<Member> = crew(bot).map { (member, assignment) ->
        Member(member, assignment.role, BlastFurnace.present(member))
    }

    /**
     * Counts other assigned bots and live human operators without competing with the requester itself.
     */
    fun occupied(bot: Bot, role: Role, arrivedOnly: Boolean = false): Boolean {
        val operator = when (role) {
            Role.PUMP -> BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PUMP)
            Role.PEDALS -> BlastFurnace.operator(bot, BlastFurnaceWorkAction.Role.PEDALS)
            else -> null
        }
        if (operator != null && operator !== bot) return true
        return members(bot).any { it.bot !== bot && it.role == role && (!arrivedOnly ||
            it.arrived && BlastFurnace.hasAccess(it.bot) && (role != Role.REPAIR ||
                it.bot.crafting.level >= 30 && it.bot.inventory.contains(BlastFurnace.HAMMER))) }
    }

    /**
     * Keeps an active furnace staffed before ordinary activity selection leaves a repair vacancy unattended.
     */
    fun needsRepairer(bot: Bot): Boolean = !occupied(bot, Role.REPAIR) &&
        (members(bot).isNotEmpty() || BlastFurnace.damage(bot) != null)

    /**
     * Registers every smelter and reserves each exclusive worker station before selection can suspend.
     */
    fun claim(bot: Bot, script: BlastFurnaceBotScript, role: Role, deadline: Long): Boolean {
        val now = System.currentTimeMillis()
        val crew = crew(bot, now)
        if (bot.state != EntityState.ACTIVE || !bot.isAlive || deadline <= now) return false
        val existing = crew[bot]
        if (existing != null && existing.script !== script) return false
        if (existing != null && existing.script === script && existing.role == role) {
            existing.deadline = deadline
            refresh(bot, script)
            return true
        }
        if (role != Role.SMELTER && occupied(bot, role)) return false
        crew[bot] = Assignment(script, role, deadline, now + RESERVATION_MILLIS,
            minOf(deadline, now + TRAVEL_MILLIS))
        return true
    }

    /**
     * Renews an active script's bounded lease; preparations and bank trips retain a travel allowance.
     */
    fun refresh(bot: Bot, script: BlastFurnaceBotScript, travelling: Boolean = false) {
        val assignment = crew(bot)[bot]?.takeIf { it.script === script } ?: return
        val allowance = if (!travelling && BlastFurnace.present(bot)) ACTIVE_MILLIS else TRAVEL_MILLIS
        assignment.leaseUntil = minOf(assignment.deadline, System.currentTimeMillis() + allowance)
    }

    /**
     * Releases only the script that owns the claim, including cancellation and initialization failures.
     */
    fun release(bot: Bot, script: BlastFurnaceBotScript) {
        val crew = crews[bot.world] ?: return
        if (crew[bot]?.script === script) crew.remove(bot)
        if (crew.isEmpty()) crews.remove(bot.world)
    }
}
