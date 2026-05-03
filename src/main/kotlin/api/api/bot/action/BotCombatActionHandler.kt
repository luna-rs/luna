package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.zone.SubZone
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.PkBotScript.Companion.LOW_LEVEL_ANCHOR_POINTS
import game.skill.magic.Magic
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.seconds

/**
 * Handles combat-related bot actions that are not part of the core combat engine itself.
 *
 * @author lare96
 */
class BotCombatActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    // TODO@0.5.0 More functions like get/equip(Strongest)Weapon, get/equip(Strongest)Armor, drinkPotion, etc.

    /**
     * Attempts to escape from the Wilderness.
     *
     * Low-level Wilderness bots teleport home immediately. Higher-level Wilderness bots first attempt to navigate to
     * one of the configured low-level anchor points before teleporting home.
     *
     * Combat is temporarily disabled while the bot is fleeing so it does not keep re-engaging targets during escape.
     *
     * @return `true` if the bot is no longer in the Wilderness or successfully reached home, otherwise `false`.
     */
    suspend fun fleeWilderness(): Boolean {
        // TODO@1.0 Bots need to support zones and area recognition. Specialized cases such as KBD lair, Mage Arena,
        //  resource area, and Wilderness agility should not blindly path to generic Wilderness anchors.
        bot.walking.isRunning = true

        if (bot.inWilderness()) {
            bot.isWandering = false
            bot.combat.isDisabled = true

            if (bot.wildernessLevel < 20) {
                bot.output.sendCommand("home")
                bot.combat.isDisabled = false
                val success = waitFor(10.seconds) { bot.subZone == SubZone.HOME }
                if (success) {
                    return true
                }
            }

            val outside = bot.subZone?.outside?.invoke(bot)
            val parent = bot.subZone?.parent?.invoke(bot)
            if (outside != null && parent != null) {
                bot.subZone.leave(bot, parent, outside)
            }

            // TODO@0.5.0 Fall back to reverse-pursuit action previously mentioned?
            if (bot.subZone == SubZone.HOME ||
                bot.navigator.navigate(LOW_LEVEL_ANCHOR_POINTS.random(), true).await() == NavigationResult.REACHED) {
                bot.output.sendCommand("home")
                return waitFor(10.seconds) { bot.subZone == SubZone.HOME }
            }

            bot.combat.isDisabled = false
            return false
        }

        return true
    }

    /**
     * Attempts to flee from the bot's current combat situation.
     *
     * Wilderness combat uses the dedicated Wilderness escape behaviour. Non-Wilderness combat either teleports home
     * when critically low on health, or falls back to future reverse-pursuit movement logic.
     */
    suspend fun fleeCombat() {
        bot.walking.isRunning = true

        if (bot.inWilderness()) {
            handler.combat.fleeWilderness()
        } else if (bot.healthPercent < 15) {
            // TODO@1.0 Only smart bots should always flee using ::home. Less intelligent bots should sometimes
            //  panic-run, misclick, hesitate, or continue fighting too long.
            bot.output.sendCommand("home")
        } else {
            // TODO@0.5.0 Add a reverse-pursuit action for bots. First check nearby tiles in the opposite direction
            //  of the threat. For example, if the attacker is east of the bot, prefer west, north-west, and
            //  south-west tiles. If none are pathable, widen the search to lateral directions, then finally allow
            //  less safe directions if the bot is boxed in.
        }
    }

    /**
     * Selects a combat spell for the bot if the bot meets the spell requirements.
     *
     * This only selects the spell on the bot's combat magic state. It does not cast the spell by itself.
     *
     * @param spell The combat spell to select.
     * @return `true` if the spell was selected, otherwise `false`.
     */
    fun selectAndUseSpell(spell: CombatSpell): Boolean {
        if (Magic.checkRequirements(bot, spell.def, false) != null) {
            bot.combat.magic.selectedSpell = spell.def
            return true
        }
        return false
    }
}