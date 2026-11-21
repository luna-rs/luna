package game.bot.scripts

import api.predef.*
import game.content.blastFurnace.BlastFurnace
import game.content.blastFurnace.BlastFurnaceWorkAction
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeech
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
     * Below this reading the gauge reader calls for pumping. Chosen to leave a few degrees above the cold end of
     * the 60 to 90 smelting band, since heat falls one degree every five ticks.
     */
    const val PUMP_BELOW = 65

    /**
     * At or above this reading the gauge reader calls stop. Chosen well under 90 because each pumping tick adds four
     * degrees, a reading takes a few ticks and the pumper only stops a tick or two after hearing the call.
     */
    const val STOP_AT = 70

    /**
     * Ticks a gauge call stays current before a reading is needed again. A chosen value.
     */
    const val FRESH_TICKS = 25L

    /**
     * Ticks after a call during which its caller still counts as the crew's gauge reader. A chosen value.
     */
    const val READER_TICKS = 50L

    /**
     * Ticks a bot keeps the gauge to itself after claiming or reading it, so two bots do not walk there at once.
     */
    private const val READ_CLAIM_TICKS = 10L

    /**
     * Ticks before a bot repeats the same crew line, about 20 seconds.
     */
    private const val CHAT_TICKS = 33L

    /**
     * Fuel below which the gauge shows the stove as low, matching the furnace's published fuel field.
     */
    private const val LOW_FUEL = 20

    private val calls = WeakHashMap<World, GaugeCall>()
    private val readers = WeakHashMap<World, Pair<Bot, Long>>()
    private val said = WeakHashMap<Bot, MutableMap<Call, Long>>()

    /**
     * What a gauge reader tells the crew, with the short public-chat lines for it. [HOLD] is a reading with
     * nothing to say; [PIPE], [BELT] and [COKE] are alarms said alongside the temperature call.
     *
     * @author TheLining
     */
    enum class Call(vararg val lines: String) {
        PUMP("pump", "start pumping", "pump pls", "pump it", "need heat pump"),
        STOP("stop", "stop pumping", "ok stop", "stop thats enough", "stop pump"),
        HOLD,
        PIPE("pipe broke", "pipe is broken", "fix the pipe pls"),
        BELT("belt broke", "belt is broken", "fix the belt pls"),
        COKE("need coke", "stove needs coke", "more coke pls", "stove is low")
    }

    /**
     * One call made from a real gauge reading: the [temperature] read, the world [tick] and the [caller].
     *
     * @author TheLining
     */
    data class GaugeCall(val call: Call, val temperature: Int, val tick: Long, val caller: Bot)

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
            it.arrived && (role != Role.REPAIR ||
                it.bot.crafting.level >= 30 && it.bot.inventory.contains(BlastFurnace.HAMMER))) }
    }

    /**
     * Keeps an active furnace staffed before ordinary activity selection leaves a repair vacancy unattended.
     */
    fun needsRepairer(bot: Bot): Boolean = !occupied(bot, Role.REPAIR) &&
        (members(bot).isNotEmpty() || BlastFurnace.damage(bot) != null)

    /**
     * Reports a job the furnace cannot run without: a repair as above, or the stove, pump or pedals while a
     * smelter is assigned.
     */
    fun vacant(bot: Bot, role: Role): Boolean = when (role) {
        Role.SMELTER -> false
        Role.REPAIR -> needsRepairer(bot)
        else -> !occupied(bot, role) && members(bot).any { it.role == Role.SMELTER }
    }

    /**
     * Returns the crew's last gauge call while it is no older than [maxAge] ticks.
     */
    fun call(bot: Bot, maxAge: Long = FRESH_TICKS): GaugeCall? =
        calls[bot.world]?.takeIf { bot.world.currentTick - it.tick <= maxAge }

    /**
     * Whether another present bot has been reading the gauge for the crew recently.
     */
    fun readerNearby(bot: Bot): Boolean = call(bot, READER_TICKS)?.caller.let {
        it != null && it !== bot && BlastFurnace.present(it)
    }

    /**
     * Gives [bot] the gauge unless another bot claimed or read it within the last few ticks.
     */
    fun claimReading(bot: Bot): Boolean {
        val now = bot.world.currentTick
        val current = readers[bot.world]
        if (current != null && current.first !== bot && BlastFurnace.present(current.first) &&
            now - current.second < READ_CLAIM_TICKS) return false
        readers[bot.world] = bot to now
        return true
    }

    /**
     * Records a reading [reader] took at the gauge and calls it to the crew. A pump already running is kept going
     * until [STOP_AT]; a cold stove gets no pump call. A changed call, a broken part and a low stove are said in
     * public chat when another crew member is in the room to hear them.
     */
    fun read(reader: Bot, temperature: Int): GaugeCall {
        val world = reader.world
        val fuel = BlastFurnace.fuel(reader)
        val pumping = BlastFurnace.operator(reader, BlastFurnaceWorkAction.Role.PUMP) != null
        val call = when {
            temperature >= STOP_AT -> Call.STOP
            fuel > 0 && (temperature < PUMP_BELOW || pumping) -> Call.PUMP
            else -> Call.HOLD
        }
        val previous = call(reader)
        val heard = GaugeCall(call, temperature, world.currentTick, reader)
        calls[world] = heard
        readers[world] = reader to world.currentTick
        if (members(reader).any { it.bot !== reader && it.arrived }) {
            if (call != previous?.call) say(reader, call)
            BlastFurnace.damage(reader)?.let { say(reader, if (it.pipe) Call.PIPE else Call.BELT) }
            if (fuel < LOW_FUEL) say(reader, Call.COKE)
        }
        return heard
    }

    /**
     * Says one line for [call] in public chat, at most once per [CHAT_TICKS] for each call and bot.
     */
    private fun say(bot: Bot, call: Call) {
        if (call.lines.isEmpty() || bot.speechStack.isAllDisabled) return
        val now = bot.world.currentTick
        val last = said.getOrPut(bot) { mutableMapOf() }
        if (now - (last[call] ?: Long.MIN_VALUE / 2) < CHAT_TICKS) return
        last[call] = now
        val line = call.lines.random()
        bot.log("Blast Furnace: called \"$line\".")
        bot.speechStack.pushHead(BotSpeech(line, 0))
    }

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
