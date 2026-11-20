package api.bot.action

import api.bot.isRealBot
import game.bot.scripts.combat.PkBotScript
import engine.controllers.MultiCombatAreaListener
import engine.controllers.WildernessLocatableController.levelAt
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference.Purpose
import java.util.concurrent.ThreadLocalRandom

/**
 * Team-cape clan identity and session cooperation, using existing equipment and online bot state.
 * Saved assignments identify real bot members; visible capes identify perceived allies, including disguised humans.
 * CLAN-purpose outings need another assigned member online. Group patrols use active CLAN sessions only.
 * Confident bots can choose a solo outing once.
 *
 * @author lare96
 */
internal class PkClanPolicy(private val bot: Bot, private val purpose: Purpose? = bot.preferences.pk?.purpose()) {
    /**
     * Every permanent PK profile participates; an outing purpose does not determine membership.
     */
    val enabled: Boolean get() = !bot.preferences.isSkiller && bot.preferences.pk != null

    /**
     * Group departure and patrol rules belong only to CLAN-purpose outings.
     */
    val clanOuting: Boolean get() = enabled && purpose == Purpose.CLAN

    /**
     * The assigned unnoted cape from the external registry, shared by every PK purpose.
     */
    val cape: Int? get() = bot.world.botManager.clanRegistry.capeFor(bot.username).takeIf { enabled }

    /**
     * Requires the assigned cape rather than treating a different clan's disguise as this bot's membership.
     */
    fun wearingCape(): Boolean = cape?.let { bot.equipment[Equipment.CAPE]?.id == it } ?: !enabled

    /**
     * Replenishes only the explicitly authorized clan cape, once across all physical owned containers.
     * A full bank fails cleanly. Ordinary combat gear, food and ammunition are never created here.
     */
    fun ensureCape(): Boolean {
        val id = cape ?: return true
        if ((bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
                .any { it.id == id || it.id == id + 1 }) return true
        val item = Item(id)
        return bot.bank.hasSpaceFor(item) && bot.bank.add(item)
    }

    /**
     * Recognition uses only worn equipment, allowing human players to mask their allegiance with a matching cape.
     */
    fun apparentAlly(player: Player): Boolean = cape?.let { player.equipment[Equipment.CAPE]?.id == it } == true

    /**
     * Confirms membership for coordination and spell safety. The other bot must be on an active group outing,
     * wearing its actual assignment and on the same Wilderness multi plane.
     */
    fun combatAlly(player: Player): Boolean {
        val id = cape ?: return false
        return player !== bot && player.isRealBot() && player.state == EntityState.ACTIVE && player.isAlive &&
            player.position.z == bot.position.z && levelAt(player.position) > 0 &&
            MultiCombatAreaListener.inside(player.position) && player.equipment[Equipment.CAPE]?.id == id &&
            bot.world.botManager.clanRegistry.capeFor(player.username) == id &&
            player.scriptStack.findActive(PkBotScript::class.java).orElse(null)?.isGroupOuting() == true
    }

    /**
     * Counts recent native attack launches that clanmates are still pursuing. A queued follow, incidental splash,
     * abandoned target or stale record cannot establish focus. Only nearby combatants can influence this fight.
     */
    fun combatSupport(players: Collection<Player>, now: Long): Map<Player, Int> = players.asSequence()
        .filter { near(it, GROUP_RADIUS) && combatAlly(it) }.mapNotNull { ally ->
            val attack = ally.combat.lastAttackSent ?: return@mapNotNull null
            (attack.victim as? Player)?.takeIf { ally.combat.target === it && now - attack.launchTick in 0..12 }
        }.groupingBy { it }.eachCount()

    /**
     * Online assigned clanmates must be alive. Trip eligibility can include members doing other activities;
     * tactical cooperation requires this same active PK script. A paused PK parent beneath combat counts.
     * Newly started members count before bank preparation so simultaneous departures cannot deadlock.
     */
    fun onlineMembers(activePkOnly: Boolean = true): List<Bot> {
        val id = cape ?: return emptyList()
        return membersByCape(bot.world)[id].orEmpty().filter {
            it !== bot && it.state == EntityState.ACTIVE && it.isAlive && !it.client.isPendingLogout &&
                (!activePkOnly || it.scriptStack.findActive(PkBotScript::class.java).orElse(null)?.isGroupOuting() == true)
        }
    }

    /**
     * Formation members must actually be on a surface clan patrol, wearing their assigned cape.
     * Solo, expired and resupplying sessions cannot lead a group away from its hunting area.
     */
    fun patrolMembers(): List<Bot> = onlineMembers().filter {
        apparentAlly(it) &&
            it.scriptStack.findActive(PkBotScript::class.java).orElse(null)?.readyForClanPatrol() == true
    }

    /**
     * Uses the same stable leader for every member, including while that leader has a combat child.
     */
    fun patrolLeader(members: List<Bot> = patrolMembers()): Bot = (members + bot).minBy { it.usernameHash }

    /**
     * Group combat requires local support. Clanmates who are still travelling can join the existing party.
     */
    fun inFormation(members: List<Bot> = patrolMembers()): Boolean =
        members.any { near(it, GROUP_RADIUS) }

    /**
     * A scout can start a fight while another member is on the same clan outing, including the approach.
     */
    fun hasOutingSupport(): Boolean = onlineMembers(activePkOnly = false).any {
        it.scriptStack.findActive(PkBotScript::class.java).orElse(null)?.canSupportClanOuting() == true
    }

    /**
     * Checks proximity on the same plane rather than treating a distant online assignment as support.
     */
    fun near(member: Player, radius: Int): Boolean = bot.position.z == member.position.z &&
        bot.position.computeLongestDistance(member.position) <= radius

    /**
     * Per-outing decisions keep transient confidence choices separate from permanent preference identity.
     *
     * @author lare96
     */
    companion object {
        const val FOLLOW_RADIUS = 4
        const val GROUP_RADIUS = 10

        private var indexedWorld: World? = null
        private var indexedTick = Long.MIN_VALUE
        private var indexedMembers = emptyMap<Int, List<Bot>>()

        /**
         * Shares one clan roster scan across all game-thread patrol and formation checks in a tick.
         * State that can change during the tick is checked by the caller.
         */
        private fun membersByCape(world: World): Map<Int, List<Bot>> {
            val tick = world.currentTick
            if (world === indexedWorld && tick == indexedTick) return indexedMembers
            val registry = world.botManager.clanRegistry
            indexedMembers = world.players.asSequence().filterIsInstance<Bot>()
                .filter { it.isRealBot() && it.preferences.pk != null && !it.preferences.isSkiller }
                .mapNotNull { member -> registry.capeFor(member.username)?.let { it to member } }
                .groupBy({ it.first }, { it.second })
            indexedWorld = world
            indexedTick = tick
            return indexedMembers
        }

        /**
         * Keeps a viable focus without a stronger reason to help elsewhere. Fresh defence takes priority, then
         * committed clan pressure. A wounded target can break a support tie only with a clear health advantage.
         * The caller supplies only locally legal candidates with at least one reason to engage.
         */
        fun selectFocus(current: Player?, candidates: Collection<Player>, support: Map<Player, Int>,
                        defending: (Player) -> Boolean): Player? {
            val best = candidates.asSequence().filter { it !== current }.maxWithOrNull(
                compareBy<Player> { defending(it) }.thenBy { support[it] ?: 0 }.thenBy { -it.healthPercent }
            ) ?: return null
            if (current == null) return best
            val bestDefends = defending(best)
            val currentDefends = defending(current)
            if (bestDefends != currentDefends) return best.takeIf { bestDefends }
            val bestSupport = support[best] ?: 0
            val currentSupport = support[current] ?: 0
            return best.takeIf {
                bestSupport > currentSupport || (bestSupport > 0 && bestSupport == currentSupport &&
                    best.healthPercent <= 35 && current.healthPercent - best.healthPercent >= 20)
            }
        }

        /**
         * Gives confident personalities a 7–10 percent solo chance per outing, rather than retrying every tick.
         * The selected mode is retained by the session snapshot; permanent clan and PK profile do not change.
         */
        fun chooseSolo(bot: Bot, roll: Double = ThreadLocalRandom.current().nextDouble()): Boolean {
            val confidence = bot.personality.confidence
            return !bot.preferences.isSkiller && bot.preferences.pk?.purpose() == Purpose.CLAN &&
                confidence.isFinite() && confidence >= 0.7 && roll >= 0 && roll < confidence.coerceAtMost(1.0) * 0.10
        }
    }
}
