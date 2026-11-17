package api.bot.boss

import api.bot.isRealBot
import api.bot.zone.SubZone
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.bot.Bot

/**
 * Temporary bot-only recruitment and travel ownership. Every operation belongs to the game thread; native
 * preparation, movement and combat remain with each member's script. Arrival releases that member immediately.
 *
 * @author lare96
 */
object BossOutings {
    const val RECRUITMENT_MILLIS = 5 * 60_000L
    const val STRAGGLER_MILLIS = 60_000L
    const val TRAVEL_MILLIS = 10 * 60_000L
    const val FORMATION_DISTANCE = 12
    private const val MEETING_DISTANCE = 8

    internal var clock: () -> Long = System::currentTimeMillis
    private val memberships = HashMap<Bot, Member>()
    private val recruitment = HashMap<Pair<BossTarget, SubZone>, MutableList<Session>>()

    /**
     * Only recruiting and travelling members retain temporary ally identity.
     *
     * @author lare96
     */
    enum class Phase { RECRUITING, TRAVELLING }

    /**
     * One identity-stable membership; a late script cleanup cannot remove a replacement membership.
     *
     * @author lare96
     */
    class Member internal constructor(val bot: Bot, val session: Session) {
        val target: BossTarget get() = session.target
        val phase: Phase get() = session.phase
        var prepared = false
            internal set
    }

    /**
     * A finite recruitment lease followed by a train. Expected arrivals reduce the train's required headcount;
     * an unexpected loss or separated link pauses departure until its single recovery deadline expires.
     *
     * @author lare96
     */
    class Session internal constructor(val host: Bot, val target: BossTarget, val bank: SubZone,
                                       val requiredCompanions: Int, val deadline: Long) {
        internal val roster = ArrayList<Member>()
        internal var closed = false
        internal var hostArrived = false
        internal var departureSize = 0
        internal var arrivals = 0
        internal var separatedAt: Long? = null
        var travelDeadline: Long? = null
            internal set
        var phase = Phase.RECRUITING
            internal set
        val members: List<Member> get() = roster.toList()

        /**
         * The first remaining member leads after the host has already reached the boss.
         */
        fun leader(): Bot? = roster.firstOrNull()?.bot
    }

    /**
     * Finds an eligible advertisement at this meeting bank before creating another. Only the listener's
     * dislike of the host gates joining; humans and permanent clan assignments never supply membership.
     */
    fun joinOrCreate(bot: Bot, target: BossTarget, bank: SubZone): Member? {
        if (!active(bot)) return null
        member(bot)?.let { return it.takeIf { it.target == target && it.session.bank == bank } }
        val key = target to bank
        val candidates = recruitment[key]?.toList().orEmpty()
        candidates.forEach(::refresh)
        val available = candidates.firstOrNull {
            !it.closed && it.phase == Phase.RECRUITING && it.roster.size <= it.requiredCompanions &&
                !bot.preferences.dislikesPlayer(it.host.username)
        }
        val session = available ?: Session(bot, target, bank, BossReadiness.requiredCompanions(bot),
                                           clock() + RECRUITMENT_MILLIS).also {
            recruitment.getOrPut(key) { ArrayList() } += it
        }
        return Member(bot, session).also { session.roster += it; memberships[bot] = it }
    }

    /**
     * Revalidates only this member's finite group rather than polling the world population.
     */
    fun member(bot: Bot): Member? {
        val membership = memberships[bot] ?: return null
        refresh(membership.session)
        return memberships[bot]?.takeIf { it === membership }
    }

    /**
     * Records completed native banking and equipment preparation. Physical arrival is checked separately,
     * so a prepared bot still travelling to the meeting bank cannot count toward departure.
     */
    fun markPrepared(bot: Bot): Boolean {
        val membership = member(bot) ?: return false
        membership.prepared = true
        refresh(membership.session)
        return memberships[bot] === membership
    }

    /**
     * Reports native preparation without giving an unprepared participant a place in the departure quorum.
     */
    fun prepared(bot: Bot): Boolean = member(bot)?.prepared == true

    /**
     * Leaders wait while this is false; followers may still close gaps or execute native crossing legs.
     */
    fun canTravel(bot: Bot): Boolean {
        val membership = member(bot) ?: return false
        return membership.phase == Phase.TRAVELLING && membership.session.separatedAt == null
    }

    /**
     * Supplies the previous remaining bot in the original join order, preserving a train rather than a fan.
     */
    fun predecessor(bot: Bot): Bot? {
        val membership = member(bot) ?: return null
        val roster = membership.session.roster
        val index = roster.indexOf(membership)
        return roster.getOrNull(index - 1)?.bot
    }

    /**
     * Clears this member's group protection before its independent boss or banking behavior starts.
     */
    fun arrived(bot: Bot) {
        val membership = member(bot) ?: return
        val session = membership.session
        if (session.phase != Phase.TRAVELLING) return
        if (bot === session.host) session.hostArrived = true
        session.arrivals++
        remove(membership)
        if (session.roster.isEmpty()) close(session) else refresh(session)
    }

    /**
     * Cancels the current membership. Host loss ends recruitment/travel; other unexpected travel losses
     * retain a bounded recovery period. Callers holding an old token should use the token overload.
     */
    fun cancel(bot: Bot) {
        memberships[bot]?.let(::cancel)
    }

    /**
     * Identity-safe cancellation cannot erase a newly selected outing after an old coroutine finishes.
     */
    fun cancel(member: Member) {
        if (memberships[member.bot] !== member) return
        val session = member.session
        if (member.bot === session.host && !session.hostArrived) close(session)
        else {
            remove(member)
            if (session.roster.isEmpty()) close(session) else refresh(session)
        }
    }

    /**
     * Real humans, arrivals and stale memberships cannot be perceived as temporary outing allies.
     */
    @JvmStatic
    fun allies(first: Mob, second: Mob): Boolean {
        if (!first.isRealBot() || !second.isRealBot() || first === second) return false
        val one = member(first) ?: return false
        val two = member(second) ?: return false
        return one.session === two.session
    }

    /**
     * Identifies temporary Wilderness parties without relying on cape disguises or permanent PK preferences.
     */
    @JvmStatic
    fun isWildernessMember(bot: Bot): Boolean = member(bot)?.target in
        setOf(BossTarget.KBD, BossTarget.CHAOS_ELEMENTAL)

    private fun active(bot: Bot): Boolean = bot.isRealBot() && bot.state == EntityState.ACTIVE && bot.isAlive

    private fun refresh(session: Session) {
        if (session.closed) return
        val now = clock()
        if ((!session.hostArrived && !active(session.host)) ||
            session.phase == Phase.RECRUITING && now >= session.deadline ||
            session.travelDeadline?.let { now >= it } == true) {
            close(session); return
        }
        session.roster.toList().filter { !active(it.bot) }.forEach(::remove)
        if (session.roster.isEmpty()) { close(session); return }
        if (session.phase == Phase.RECRUITING) {
            if (session.roster.size < session.requiredCompanions + 1 || session.roster.any {
                    !it.prepared || !session.bank.area.contains(it.bot.position) ||
                        !it.bot.isWithinDistance(session.host, MEETING_DISTANCE)
                }) return
            session.phase = Phase.TRAVELLING
            session.departureSize = session.roster.size
            session.travelDeadline = now + TRAVEL_MILLIS
            removeAdvertisement(session)
        }
        val separated = session.roster.size < session.departureSize - session.arrivals ||
            session.roster.zipWithNext().any { (first, second) ->
                !first.bot.isWithinDistance(second.bot, FORMATION_DISTANCE)
            }
        if (!separated) session.separatedAt = null
        else {
            val since = session.separatedAt ?: now.also { session.separatedAt = it }
            if (now - since >= STRAGGLER_MILLIS) close(session)
        }
    }

    private fun remove(member: Member) {
        if (memberships[member.bot] === member) memberships.remove(member.bot)
        member.session.roster.remove(member)
        BossOutingDefence.forget(member.bot)
    }

    private fun removeAdvertisement(session: Session) {
        val key = session.target to session.bank
        recruitment[key]?.let { list ->
            list.remove(session)
            if (list.isEmpty()) recruitment.remove(key)
        }
    }

    private fun close(session: Session) {
        if (session.closed) return
        session.closed = true
        session.roster.toList().forEach(::remove)
        removeAdvertisement(session)
    }

    internal fun clear() {
        memberships.values.map { it.session }.distinct().forEach(::close)
    }
}
