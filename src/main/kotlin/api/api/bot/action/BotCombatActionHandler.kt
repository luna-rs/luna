package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.zone.SubZone
import api.predef.*
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.PkBotScript.Companion.LOW_LEVEL_ANCHOR_POINTS
import game.skill.magic.Magic
import game.skill.magic.Staff
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.seconds

/**
 * Handles combat-related bot actions that are not part of the core combat engine itself.
 *
 * @author lare96
 */
class BotCombatActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Determines whether the given mob is considered an extreme combat threat to this bot.
     *
     * A mob is treated as threatening when its combat level is more than double the bot's combat level. This is a
     * simple fear check meant for obvious danger cases, such as a low-level bot encountering a much stronger enemy.
     *
     * @param mob The mob being evaluated.
     *
     * @return `true` if [mob] is more than twice this bot's combat level.
     */
    fun isThreat(mob: Mob)

            : Boolean {
        // TODO@.5.0 Expand by checking mob type, if it's a boss, equipment, skills, etc.
        return mob.combatLevel > bot.combatLevel * 2
    }

    /**
     * Configures [spell] as the bot's active autocast spell.
     *
     * The bot must be using the correct spellbook, wielding a valid staff, and currently meet all spell requirements.
     *
     * @return `true` if autocasting was configured successfully.
     */
    fun setAutocastSpell(spell: CombatSpellDefinition): Boolean {
        if (spell == CombatSpellDefinition.NONE || spell.spellbook != bot.spellbook) {
            return false
        }

        val weaponId = bot.equipment.weapon?.id ?: return false
        if (bot.combat.weapon.type != Weapon.STAFF) {
            return false
        }

        if (bot.spellbook == Spellbook.ANCIENT && weaponId !in Staff.AUTOCAST_ANCIENTS) {
            return false
        }

        if (Magic.checkRequirements(bot, spell, true) == null) {
            return false
        }

        val magic = bot.combat.magic
        magic.selectedSpell = CombatSpellDefinition.NONE
        magic.autocastSpell = spell
        magic.isAutocasting = true
        magic.refreshAutocast()
        return true
    }

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
        bot.walking.isRunning = true

        if (bot.inWilderness()) {
            bot.isWandering = false
            bot.combat.isDisabled = true
            try {
                if (bot.wildernessLevel < 20) {
                    bot.output.sendCommand("home")
                    bot.combat.isDisabled = false
                    val success = waitFor(10.seconds) { SubZone.HOME in bot.subZones }
                    if (success) {
                        return true
                    }
                }

                val subZone = bot.subZones.firstOrNull()
                val outside = subZone?.outside?.invoke(bot)
                val parent = subZone?.parent?.invoke(bot)
                if (outside != null && parent != null) {
                    subZone.leave(bot, parent, outside)
                }

                // TODO@0.5.0 Fall back to reverse-pursuit action previously mentioned?
                if (SubZone.HOME in bot.subZones ||
                    bot.navigator.navigate(LOW_LEVEL_ANCHOR_POINTS.random(), true)
                        .await() == NavigationResult.REACHED
                ) {
                    bot.output.sendCommand("home")
                    return waitFor(10.seconds) { SubZone.HOME in bot.subZones }
                }
            } finally {
                bot.combat.isDisabled = false
            }
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