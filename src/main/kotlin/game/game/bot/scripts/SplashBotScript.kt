package game.bot.scripts

import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

class SplashBotScript(bot: Bot,
                      duration: Duration) : ZonedBotScript(bot, duration, SPLASH_ZONES) {

    val spell: CombatSpellDefinition? = resolveSplashSpell(bot)
    var usingStaff: Int? = null
    var runes: List<Item> = emptyList()

    companion object {
        val MIN_CAST_RUNES_REQUIRED = 2000 // enough runes for at least <x> casts
        val SPLASH_ZONES = mutableListOf(SubZone.LUMBRIDGE_COURT_YARD)
        val ALWAYS_ALLOWED = setOf("Rat")
        fun resolveSplashSpell(bot: Bot): CombatSpellDefinition? {
            return CombatSpellDefinition.ALL.filter { bot.magic.level >= it.level && it.required.find { req -> req !is RuneRequirement } == null }
                .maxByOrNull { it.level }
        }
    }

    override suspend fun equipment(): BotGearLocator? {
        //val locator = BotGearSelector.find()
        // equip full set of armor with staff
        return null
    }

    override fun onInit(resumed: Boolean): Boolean {

        fun resolveStaffFor(req: RuneRequirement): Int? {
            for (staff in Staff.entries) {
                if (req.rune in staff.represents) {
                    for (id in staff.ids) {
                        if (id in bot.itemTracker) {
                            return id
                        }
                    }
                }
            }
            return null
        }

        if (spell == null) {
            bot.log("No valid spell found [level: ${bot.magic.level}, spellbook: ${bot.spellbook}]")
            return false
        }
        val withdraw = ArrayList<Item>()
        for (req in spell.required) {
            if (req !is RuneRequirement) {
                bot.log("${spell.spell} cannot be used for splashing.")
                return false
            }
            if (usingStaff == null) {
                val staff = resolveStaffFor(req) ?: continue
                usingStaff = staff
            } else if (bot.preferences.requireItem(req.rune.id, req.amount * MIN_CAST_RUNES_REQUIRED)) {
                withdraw += Item(req.rune.id, req.amount * MIN_CAST_RUNES_REQUIRED)
            } else {
                bot.log("Don't have enough ${req.rune} runes to start splashing.")
                return false
            }
        }
        runes = withdraw
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (initial) {
            handler.banking.withdrawAll(runes)
            if (usingStaff != null && bot.equipment.weapon?.id != usingStaff) {
                val staffItem = Item(usingStaff!!)
                if (staffItem.id !in bot.itemTracker) {
                    bot.log("No longer have required staff item.")
                    stop()
                    return
                } else if (staffItem !in bot.inventory) {
                    handler.banking.withdraw(staffItem)
                }
                handler.equipment.equip(staffItem.id)
            }
        }
    }

    override suspend fun executeInZone(): Boolean {
        val area = activeZone?.area ?: return false
        val splashNpcs = world.locator.findNpcs(area.centerPosition, area.tileRadius) {
            (it.combatLevel < bot.combatLevel / 2 || it.def().name in ALWAYS_ALLOWED)
        }
        for (npc in splashNpcs) {
            if (bot.isWithinDistance(npc, 10) && world.collisionManager.raycast(bot.position, npc.position)) {

            }
            // val path = bot.navigator.findPath(bot, npc)
        }

        return true
    }

    override fun snapshot(): BotScriptData? {
        return null
    }
    // dextrous bots use highest avialable "strike" spell
    // every other bot defaults to wind strike

    // first determine spell ^  from above
    // then determine staff needed, see if we have it. or all runes required if staff isnt required
    //

}