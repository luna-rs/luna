package api.bot.action.prayer

import api.bot.Suspendable.waitFor
import api.bot.action.PvmFight
import api.bot.action.eating.MonsterMemory.Companion.monsterMemory
import api.predef.*
import engine.combat.prayer.CombatPrayer
import engine.combat.status.StatusEffectType
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageType
import java.util.EnumMap
import java.util.EnumSet
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Prays for a bot while it fights monsters, the way a player watching their prayer points would.
 *
 * Each fight gets its own [PrayerHabit]. Protection prayers go up only where they pay off, against bosses or monsters
 * that can take a big share of the bot's hitpoints in one round of hits, so never against cows. Bots that know their
 * monsters pray against the way the monster usually attacks ([MonsterStyles]); others copy the last hit they took.
 * Unintelligent bots now and then pick the wrong protection, until their next hit. Boost prayers go up only against
 * tough monsters, for bots fighting with melee. The bot clicks its prayers like a player, a moment after it decides to.
 *
 * Prayer points are spent as a budget. Each kind of prayer has a floor: once the bot's points fall to it, that prayer
 * goes off and stays off for the rest of the trip, unless the bot gets [RESUME_POINTS] above the floor again. That
 * gap keeps a bot with few points from switching its prayers off and on as it crosses the floor. A bot close to dying
 * forgets its reserve and spends what it has left on protection.
 *
 * Between kills, and when it leaves the camp, the bot may switch its prayers off to save points.
 *
 * @param bot The bot to pray for.
 * @author lare96
 */
class PvmPrayer(private val bot: Bot) {

    companion object {

        /**
         * How many prayer points above its floor a bot needs before it switches a prayer back on.
         */
        const val RESUME_POINTS = 5

        /**
         * How long a bot leaves a prayer alone after clicking it did nothing, in ticks.
         */
        const val BLOCKED_TICKS = 10

        /**
         * The protection prayer against each style of attack.
         */
        private val PROTECTION = EnumMap<CombatDamageType, CombatPrayer>(CombatDamageType::class.java).apply {
            put(CombatDamageType.MELEE, CombatPrayer.PROTECT_FROM_MELEE)
            put(CombatDamageType.RANGED, CombatPrayer.PROTECT_FROM_MISSILES)
            put(CombatDamageType.MAGIC, CombatPrayer.PROTECT_FROM_MAGIC)
        }

        /**
         * The prayers this class switches on and off. Anything else the bot prays is left alone.
         */
        private val MANAGED: Set<CombatPrayer> = EnumSet.copyOf(PROTECTION.values +
            CombatPrayer.ATTACK_PRAYERS + CombatPrayer.STRENGTH_PRAYERS + CombatPrayer.DEFENCE_PRAYERS)

        /**
         * Returns the best prayer in [group] that a bot with [prayerLevel] can use, or `null` if it can't use any.
         */
        private fun best(group: Set<CombatPrayer>, prayerLevel: Int): CombatPrayer? =
            group.filter { it.level <= prayerLevel }.maxByOrNull { it.level }

        /**
         * The name of [prayer] as a player would write it.
         */
        private fun nameOf(prayer: CombatPrayer): String =
            prayer.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /**
     * How the bot prays in its current or most recent fight, or `null` before its first fight.
     */
    private var habit: PrayerHabit? = null

    /**
     * The monster the current fight is against.
     */
    private var opponent: Npc? = null

    /**
     * The life of [opponent] this fight is against; a respawned NPC is a new fight.
     */
    private var opponentLife = -1

    /**
     * The style the bot knows its monster attacks with, once it has worked it out this fight.
     */
    private var knownStyle: CombatDamageType? = null

    /**
     * The style the bot last read the fight as needing protection from, which can be wrong.
     */
    private var readStyle: CombatDamageType? = null

    /**
     * The style that was right when the bot last read the fight.
     */
    private var readFrom: CombatDamageType? = null

    /**
     * The hit the bot last read the fight on, or `null` if it read it before taking one.
     */
    private var readHit: CombatDamage? = null

    /**
     * Whether the bot has started spending its reserve this fight, because it was close to dying.
     */
    private var usingReserve = false

    /**
     * The tick from which the bot acts on the prayer changes it wants, or `-1` if it wants none.
     */
    private var actAt = -1L

    /**
     * Whether the bot has decided if it switches its prayers off during the current lull between fights.
     */
    private var lullDecided = false

    /**
     * The tick the bot switches its prayers off during the current lull, or `-1` if it leaves them on.
     */
    private var switchOffAt = -1L

    /**
     * The tick until which the bot leaves each prayer alone, after clicking it did nothing.
     */
    private val blockedUntil = EnumMap<CombatPrayer, Long>(CombatPrayer::class.java)

    /**
     * Looks after the bot's prayers once: switches on what the fight calls for, and off what it no longer needs or can
     * no longer afford.
     *
     * @param fight The monsters the bot is fighting, or `null` if it isn't fighting.
     */
    suspend fun check(fight: PvmFight?) {
        if (bot.health <= 0) {
            return
        }
        val now = bot.world.currentTick
        if (fight == null) {
            betweenFights(now)
        } else {
            duringFight(fight, now)
        }
    }

    /**
     * Switches the bot's prayers off as it leaves the fight, if it's the kind of bot that does. A bot leaving while
     * still under attack keeps its protection on. The clicks don't wait for the game, so this is safe while the bot's
     * script is finishing: each click switches off a different prayer that is on.
     */
    fun leaveFight() {
        actAt = -1L
        lullDecided = false
        val underAttack = bot.combat.inCombat()
        val active = activePrayers().filter { !underAttack || it !in PROTECTION.values }
        if (bot.state != EntityState.ACTIVE || active.isEmpty() ||
            Random.nextDouble() >= currentHabit().switchOffChance) {
            return
        }
        bot.log("Switching off ${active.joinToString { nameOf(it) }} as I leave the fight.")
        active.forEach { bot.output.clickButton(it.button) }
    }

    /**
     * Works out which prayers the fight calls for and the bot can afford, and makes the changes once the bot has
     * reacted.
     */
    private suspend fun duringFight(fight: PvmFight, now: Long) {
        val habit = updateFight(fight)
        lullDecided = false
        val prayerLevel = bot.prayer.staticLevel
        val points = bot.prayer.level
        val hp = bot.health
        val maximumHp = bot.totalHealth
        val danger = fight.opponents.sumOf { bot.monsterMemory.expectedMaxHit(it, habit.knowledge) }
        val boss = fight.opponents.any { it.isBoss }
        val paysOff = boss || danger >= habit.threatShare * maximumHp
        val inDanger = paysOff && hp <= 2 * danger
        val reserve = habit.reserve(prayerLevel)

        // What the fight calls for, before thinking about the budget.
        val needed = EnumSet.noneOf(CombatPrayer::class.java)
        val protection = if (paysOff) protectionFor(fight, habit, prayerLevel) else null
        if (protection != null) {
            needed += protection
            if (inDanger && points in 1..reserve && !usingReserve) {
                usingReserve = true
                bot.log("Close to dying at $hp/$maximumHp hitpoints, using my last $points prayer points.")
            }
        }
        val tough = boss || fight.opponents.any { it.combatLevel >= habit.toughShare * bot.combatLevel }
        if (tough && !bot.combat.weapon.isRanged && !bot.combat.magic.isAutocasting) {
            best(CombatPrayer.ATTACK_PRAYERS, prayerLevel)?.let { needed += it }
            best(CombatPrayer.STRENGTH_PRAYERS, prayerLevel)?.let { needed += it }
            if (habit.wasteful) {
                best(CombatPrayer.DEFENCE_PRAYERS, prayerLevel)?.let { needed += it }
            }
        }

        // What the bot can afford. Switching protection over to another style isn't switching it on.
        val protectionFloor = if (usingReserve) 0 else reserve
        val boostFloor = habit.boostFloor(prayerLevel)
        fun floorOf(prayer: CombatPrayer) = if (prayer in PROTECTION.values) protectionFloor else boostFloor
        val protecting = activePrayers().any { it in PROTECTION.values }
        val affordable = needed.filterTo(EnumSet.noneOf(CombatPrayer::class.java)) {
            affords(if (it in PROTECTION.values) protecting else isOn(it), points, floorOf(it))
        }

        // Prayers that are no longer needed stay on unless the bot is tidy; ones it can't afford go off regardless.
        val turnOn = affordable.filter { !isOn(it) }
        val turnOff = activePrayers().filter { it !in affordable && (habit.tidy || points <= floorOf(it)) }
        if (turnOn.isEmpty() && turnOff.isEmpty()) {
            actAt = -1L
            return
        }
        if (actAt < 0) {
            actAt = now + habit.reactionTicks(inDanger)
        }
        if (now < actAt) {
            return
        }
        actAt = -1L
        if (turnOn.isNotEmpty()) {
            bot.log("Praying ${turnOn.joinToString { nameOf(it) }} against ${fight.primary.def().name} " +
                        "($points/$prayerLevel prayer points, expects hits up to $danger of $maximumHp hitpoints).")
            turnOn.forEach { click(it, true) }
        }
        // Switching protection over turns the old one off by itself.
        val stillOn = turnOff.filter { isOn(it) }
        if (stillOn.isNotEmpty()) {
            val saving = stillOn.any { points <= floorOf(it) }
            val why = if (saving) "to save my last $points prayer points" else "no longer needed"
            bot.log("Switching off ${stillOn.joinToString { nameOf(it) }}, $why.")
            stillOn.forEach { click(it, false) }
        }
    }

    /**
     * Between fights, decides once whether to switch the bot's prayers off to save points, and does so after a
     * moment.
     */
    private suspend fun betweenFights(now: Long) {
        val active = activePrayers()
        if (active.isEmpty()) {
            return
        }
        if (!lullDecided) {
            val habit = currentHabit()
            lullDecided = true
            switchOffAt = if (Random.nextDouble() < habit.switchOffChance) now + habit.switchOffTicks() else -1L
        }
        if (switchOffAt < 0 || now < switchOffAt) {
            return
        }
        bot.log("Switching off ${active.joinToString { nameOf(it) }} between kills to save points.")
        active.forEach { click(it, false) }
    }

    /**
     * Starts a new fight with a fresh habit when the bot's opponent changes.
     *
     * @return The habit for this fight.
     */
    private fun updateFight(fight: PvmFight): PrayerHabit {
        val primary = fight.primary
        val current = habit
        if (current != null && primary === opponent && primary.spawnCount == opponentLife) {
            return current
        }
        opponent = primary
        opponentLife = primary.spawnCount
        knownStyle = null
        readStyle = null
        readFrom = null
        readHit = null
        usingReserve = false
        actAt = -1L
        return rollHabit()
    }

    /**
     * Picks the protection prayer for this fight, or `null` if the bot can't tell what to protect from yet or can't
     * use the prayer it would want.
     *
     * The bot reads the fight again whenever it takes a new hit, or works out how its monster attacks. Each reading
     * may come out wrong ([PrayerHabit.wrongProtectionChance]), and stays wrong until the next one.
     */
    private fun protectionFor(fight: PvmFight, habit: PrayerHabit, prayerLevel: Int): CombatPrayer? {
        if (bot.prayer.level <= 0 || StatusEffectType.DRAGON_SCIMITAR in bot.status) {
            // Out of prayer points, or the game said protection prayers are disabled for now.
            return null
        }
        if (habit.knowsStyles && knownStyle == null) {
            val strongest = fight.opponents.maxBy { bot.monsterMemory.expectedMaxHit(it, habit.knowledge) }
            knownStyle = MonsterStyles.usualStyle(strongest)
        }
        val hit = lastHit(fight)
        val style = knownStyle ?: hit?.type ?: return null
        if (readStyle == null || hit !== readHit || style != readFrom) {
            readHit = hit
            readFrom = style
            readStyle = if (Random.nextDouble() < habit.wrongProtectionChance) {
                CombatDamageType.entries.filter { it != style }.random().also {
                    bot.log("Misreading the fight against ${fight.primary.def().name}: $style looks like $it.")
                }
            } else {
                style
            }
        }
        return PROTECTION[readStyle]?.takeIf { it.level <= prayerLevel }
    }

    /**
     * Returns the last hit the bot took from a monster in this fight, or `null` if it hasn't taken one.
     */
    private fun lastHit(fight: PvmFight): CombatDamage? {
        val hit = bot.combat.lastDamageReceived ?: return null
        val attacker = hit.attacker as? Npc ?: return null
        return if (attacker in fight.opponents) hit else null
    }

    /**
     * Determines if the bot affords a prayer. One that's on stays on while the bot has points above [floor]; one that's
     * off only goes on with [RESUME_POINTS] to spare.
     */
    private fun affords(on: Boolean, points: Int, floor: Int): Boolean =
        if (on) points > floor else points >= floor + RESUME_POINTS

    /**
     * Returns the habit for the current or most recent fight, rolling one if the bot hasn't fought yet.
     */
    private fun currentHabit(): PrayerHabit = habit ?: rollHabit()

    /**
     * Rolls a habit from the bot's personality and keeps it.
     */
    private fun rollHabit(): PrayerHabit =
        PrayerHabit.roll(bot.personality.intelligence, bot.personality.confidence, bot.personality.dexterity,
                         bot.emotions.isFeeling(EmotionType.SCARED)).also { habit = it }

    /**
     * Returns the prayers this class looks after that are on.
     */
    private fun activePrayers(): List<CombatPrayer> = bot.combat.prayers.active.filter { it in MANAGED }

    /**
     * Determines if [prayer] is on.
     */
    private fun isOn(prayer: CombatPrayer): Boolean = bot.combat.prayers.isActive(prayer)

    /**
     * Clicks [prayer] on the prayer tab to switch it [on] or off, as a player would, and waits for the game to do it.
     * A click that does nothing, like one the game refused with a message, isn't repeated for a while.
     *
     * @return `true` if the prayer is now on or off as wanted.
     */
    private suspend fun click(prayer: CombatPrayer, on: Boolean): Boolean {
        if (isOn(prayer) == on) {
            return true
        }
        val now = bot.world.currentTick
        if (now < (blockedUntil[prayer] ?: -1L) || !bot.output.clickButton(prayer.button)) {
            return false
        }
        if (waitFor(1.seconds) { isOn(prayer) == on }) {
            return true
        }
        blockedUntil[prayer] = now + BLOCKED_TICKS
        return false
    }
}
