package api.bot.action

import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference.Purpose
import java.util.concurrent.ThreadLocalRandom

/**
 * Team-cape clan identity and session cooperation, using existing equipment and online bot state.
 * Saved assignments identify real bot members; visible capes identify perceived allies, including disguised humans.
 * Ordinary outings need another assigned member online. Tactical cooperation uses active PK sessions only.
 * Confident bots can choose a solo outing once.
 *
 * @author lare96
 */
internal class PkClanPolicy(private val bot: Bot) {
    /**
     * Only permanent CLAN identities participate; a worn cape cannot enroll an ordinary bot or skiller.
     */
    val enabled: Boolean get() = !bot.preferences.isSkiller && bot.preferences.pk?.purpose() == Purpose.CLAN

    /**
     * The immutable unnoted cape ID selected through the original preference lifecycle.
     */
    val cape: Int? get() = bot.preferences.pk?.teamCape()?.takeIf { enabled }

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
     * Online assigned clanmates must be alive. Trip eligibility can include members doing other activities;
     * tactical cooperation requires this same active PK script. A paused PK parent beneath combat counts.
     * Newly started members count before bank preparation so simultaneous departures cannot deadlock.
     */
    fun onlineMembers(activePkOnly: Boolean = true): List<Bot> {
        val id = cape ?: return emptyList()
        return bot.world.players.stream().filter { it is Bot && it !== bot }.map { it as Bot }.filter {
            it.state == EntityState.ACTIVE && it.isAlive && !it.preferences.isSkiller &&
                it.preferences.pk?.purpose() == Purpose.CLAN && it.preferences.pk?.teamCape() == id &&
                (!activePkOnly || it.scriptStack.containsActive(PkBotScript::class.java))
        }.toList()
    }

    /**
     * Per-outing decisions keep transient confidence choices separate from permanent preference identity.
     *
     * @author lare96
     */
    companion object {
        /**
         * Gives confident personalities a 17.5–25 percent solo chance per outing, rather than retrying every tick.
         * The selected mode is retained by the session snapshot; permanent clan and PK identity do not change.
         */
        fun chooseSolo(bot: Bot, roll: Double = ThreadLocalRandom.current().nextDouble()): Boolean {
            val confidence = bot.personality.confidence
            return !bot.preferences.isSkiller && bot.preferences.pk?.purpose() == Purpose.CLAN &&
                confidence.isFinite() && confidence >= 0.7 && roll >= 0 && roll < confidence.coerceAtMost(1.0) * 0.25
        }
    }
}
