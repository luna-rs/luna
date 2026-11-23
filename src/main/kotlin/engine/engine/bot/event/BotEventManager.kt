package engine.bot.event

import api.predef.ext.schedule
import api.predef.ext.toTicks
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import org.apache.logging.log4j.LogManager
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes

/**
 * Manages temporary event rosters and their expiry on the game thread.
 *
 * @author lare96
 */
internal class BotEventManager(private val world: World) {
    /**
     * The visible state of an event in Luna's Toy.
     *
     * @author lare96
     */
    data class Status(val bots: Int, val minutesRemaining: Long, val finishing: Boolean, val starting: Boolean)

    private val sessions = LinkedHashMap<BotEventActivity, Session>()
    private val members = IdentityHashMap<Bot, Member>()
    private val logger = LogManager.getLogger()
    private val ticksPerMinute = 1.minutes.toTicks()

    fun status(activity: BotEventActivity): Status? {
        val session = sessions[activity] ?: return null
        val deadline = session.deadline ?: return Status(0, 0, finishing = false, starting = true)
        val ticksLeft = (deadline - world.currentTick).coerceAtLeast(0)
        val accepting = session.accepting()
        return Status(
            session.roster.count { it.bot.state == EntityState.ACTIVE },
            if (accepting) (ticksLeft + ticksPerMinute - 1) / ticksPerMinute else 0,
            finishing = !accepting,
            starting = false
        )
    }

    fun start(activity: BotEventActivity, count: Int, minutes: Int): CompletableFuture<Boolean> {
        require(count in 1..activity.maxBots) { "Choose between 1 and ${activity.maxBots} bots." }
        require(minutes > 0) { "Choose a duration of at least one minute." }
        check(activity !in sessions) { "${activity.name} already has an event." }
        checkAvailableSlots(count)

        val session = Session(activity)
        sessions[activity] = session
        val login = try {
            world.botManager.scheduleService.withEventUsernames(count) { names ->
                if (sessions[activity] !== session) return@withEventUsernames false
                checkAvailableSlots(count)
                session.deadline = world.currentTick + minutes.toLong() * ticksPerMinute
                names.forEachIndexed { index, username -> session.login(index, count, username) }
                true
            }
        } catch (error: RuntimeException) {
            sessions.remove(activity)
            throw error
        }
        return login.whenCompleteAsync({ _, error ->
            if (error != null) {
                session.stopping = true
                logger.warn("Could not start a ${activity.name} event.", error)
            }
            if (session.roster.isEmpty()) {
                sessions.remove(activity, session)
            } else {
                world.schedule(1) { task ->
                    if (!session.advance()) {
                        sessions.remove(activity)
                        task.cancel()
                    }
                }
            }
        }, world.context.game.gameExecutor)
    }

    fun stop(activity: BotEventActivity) {
        val session = sessions[activity] ?: return
        if (session.deadline == null) sessions.remove(activity)
        else session.stopping = true
    }

    fun onLogout(bot: Bot) {
        val member = members.remove(bot) ?: return
        if (!member.leaving) member.leaveActivity()
    }

    private fun checkAvailableSlots(count: Int) {
        val slots = world.players.capacity() - 1 - world.playerMap.size
        check(count <= slots) { "There are only ${slots.coerceAtLeast(0)} open player slots." }
    }

    /**
     * One activity's deadline and the bots still present for it.
     *
     * @author lare96
     */
    private inner class Session(val activity: BotEventActivity) {
        val roster = mutableListOf<Member>()
        var deadline: Long? = null
        var stopping = false

        fun accepting(): Boolean = !stopping && deadline?.let { world.currentTick < it } == true

        fun login(index: Int, count: Int, username: String) {
            val participant = BotEventParticipant(index, count, activity.spawn(index, count), ::accepting)
            val brain = ActivityBrain(activity, participant)
            val builder = Bot.Builder(world.context).setUsername(username).setSpawnPosition(participant.spawn)
                .setEventBot { participant.canStart() }.setBrain(brain)
            activity.configure(builder, world.context)
            val bot = builder.build()
            val member = Member(this, participant, bot, brain)
            roster.add(member)
            members[bot] = member
            bot.loginForEvent { activity.prepare(it, participant) }
        }

        fun advance(): Boolean {
            val iterator = roster.iterator()
            while (iterator.hasNext()) {
                val member = iterator.next()
                val bot = member.bot
                if (bot.state != EntityState.ACTIVE) {
                    members.remove(bot)
                    iterator.remove()
                    continue
                }
                if (bot.client.isPendingLogout) continue
                try {
                    if (!bot.isAlive || bot.actions.first(MobDeathAction::class.java) != null) {
                        member.died = true
                        continue
                    }
                    val inMatch = activity.inMatch(bot)
                    if (!accepting() && !inMatch) {
                        member.logout()
                    } else if (member.died) {
                        member.died = false
                        if (!inMatch) {
                            bot.scriptStack.stopAll()
                            bot.move(member.participant.spawn)
                        }
                        activity.prepare(bot, member.participant)
                        member.brain.hasKit = true
                    }
                } catch (error: RuntimeException) {
                    logger.warn("Could not update ${bot.username} during ${activity.name}.", error)
                    member.logout()
                }
            }
            return roster.isNotEmpty()
        }
    }

    /**
     * A temporary participant and its cleanup state.
     *
     * @author lare96
     */
    private inner class Member(
        val session: Session,
        val participant: BotEventParticipant,
        val bot: Bot,
        val brain: ActivityBrain
    ) {
        var died = false
        var leaving = false

        fun leaveActivity() {
            leaving = true
            try {
                session.activity.leave(bot)
            } catch (error: RuntimeException) {
                logger.warn("Could not remove ${bot.username} from ${session.activity.name}.", error)
            }
        }

        fun logout() {
            try {
                bot.scriptStack.stopAll()
                bot.navigator.cancel()
                bot.walking.clear()
                bot.actions.getAll(CombatDamageAction::class.java).forEach { it.interrupt() }
                bot.combat.target = null
                bot.combat.autoRetaliateTarget = null
            } catch (error: RuntimeException) {
                logger.warn("Could not stop pending actions for ${bot.username}.", error)
            } finally {
                leaveActivity()
                bot.forceLogout()
            }
        }
    }

    /**
     * Dispatches the assigned gameplay while the event permits a new run.
     *
     * @author lare96
     */
    private class ActivityBrain(
        private val activity: BotEventActivity,
        private val participant: BotEventParticipant
    ) : BotBrain() {
        var hasKit = true

        override fun process(bot: Bot): BotCoordinator? {
            val inMatch = activity.inMatch(bot)
            if (!participant.canStart() && !inMatch) return null
            if (!hasKit && !inMatch) {
                if (bot.combat.inCombat()) return null
                if (bot.position != participant.spawn) bot.move(participant.spawn)
                activity.prepare(bot, participant)
                hasKit = true
            }
            val script = activity.play(bot, participant) ?: return null
            hasKit = false
            return BotCoordinator { it.scriptStack.push(script) }
        }
    }
}
