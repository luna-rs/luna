package api.bot.action

import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearPurpose.MAGIC
import engine.bot.gear.BotGearPurpose.MELEE
import io.luna.game.model.item.Equipment
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PrayerIcon
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * Compares carried styles over a short attack window when an opponent protects against Ancient Magicks.
 * The caller confirms switches across scheduled decisions and keeps supply and attack admission native.
 *
 * @author lare96
 */
internal class PkAncientPrayer(private val bot: Bot) {
    private companion object {
        const val PRESSURE_TICKS = 10
        const val TICKS_PER_EQUIPMENT_CLICK = 3
        val ICE_SPELLS = listOf(CombatSpell.ICE_RUSH, CombatSpell.ICE_BURST, CombatSpell.ICE_BLITZ,
            CombatSpell.ICE_BARRAGE)
    }

    /**
     * Returns the strongest reachable alternative that gains damage after equipping. While away from Magic
     * because of this policy, checks whether retaining the current style still offers that advantage.
     */
    fun counter(target: Player, current: Pair<BotGearPurpose, CombatLoadout>,
                choices: Map<BotGearPurpose, CombatLoadout>): BotGearPurpose? {
        if (target.prayerIcon != PrayerIcon.PROTECT_FROM_MAGIC || bot.position == target.position) return null
        val stayingWithMagic = current.first == MAGIC
        val magic = if (stayingWithMagic) current.second else choices[MAGIC] ?: return null
        val magicPressure = pkLoadoutPressure(bot, target, MAGIC, magic, current = stayingWithMagic) ?: return null
        val cooldown = bot.combat.attackDelay
        val inMagicRange = reached(target, MAGIC, magicPressure.range)
        if (inMagicRange && canFreezeDuringWindow(target, magic, cooldown, magicPressure.speed)) return null
        val stayingDamage = if (inMagicRange) damage(magicPressure, cooldown) else 0.0

        var best: BotGearPurpose? = null
        var bestDamage = stayingDamage
        for ((purpose, loadout) in choices) {
            if (purpose == MAGIC || !stayingWithMagic && purpose != current.first) continue
            val alreadyEquipped = purpose == current.first
            val setup = if (alreadyEquipped) 0 else equipmentTicks(loadout) ?: continue
            val candidate = pkLoadoutPressure(bot, target, purpose,
                if (alreadyEquipped) current.second else loadout, current = alreadyEquipped) ?: continue
            if (!reached(target, purpose, candidate.range)) continue
            val projected = damage(candidate, maxOf(cooldown, setup))
            if (projected > bestDamage) {
                best = purpose
                bestDamage = projected
            }
        }
        return best
    }

    /**
     * Uses the prospective weapon's native reach policy from the current tile.
     */
    private fun reached(target: Player, purpose: BotGearPurpose, range: Int): Boolean {
        val type = if (purpose == MELEE) InteractionType.SIZE else InteractionType.LINE_OF_SIGHT
        return bot.world.collisionManager.reached(bot, target, InteractionPolicy(type, range))
    }

    /**
     * Each equip click waits for acknowledgement before the next. Implicit shield removal uses the weapon click.
     */
    private fun equipmentTicks(loadout: CombatLoadout): Int? {
        val weapon = loadout.gear[Equipment.WEAPON] ?: return null
        val wornWeapon = bot.equipment[Equipment.WEAPON]
        if (wornWeapon != null && wornWeapon.id != weapon && bot.equipment[Equipment.SHIELD] != null &&
            bot.inventory.computeRemainingSize() == 0 &&
            EquipmentDefinition.ALL.get(weapon).orElse(null)?.isTwoHanded == true) return null
        return TICKS_PER_EQUIPMENT_CLICK * loadout.gear.indices.count { slot ->
            val id = loadout.gear[slot]
            id != null && bot.equipment[slot]?.id != id
        }
    }

    /**
     * Projects ordinary launch damage with the current cooldown, cadence and carried consumable limits.
     */
    private fun damage(pressure: PkLoadoutPressure, first: Int): Double {
        if (first >= PRESSURE_TICKS) return 0.0
        val launches = 1 + (PRESSURE_TICKS - 1 - first) / pressure.speed
        return minOf(launches, pressure.availableLaunches) * pressure.expectedDamagePerLaunch
    }

    /**
     * Preserves even a lower-tier carried Ice spell if it could freeze at a later impact within the window.
     * Eligibility improves as the existing hold and immunity expire, so the latest supplied impact is sufficient.
     */
    private fun canFreezeDuringWindow(target: Player, magic: CombatLoadout, first: Int, speed: Int): Boolean {
        val now = bot.world.currentTick
        val weapon = magic.gear[Equipment.WEAPON]
        for (ice in ICE_SPELLS) {
            val spell = ice.def
            if (bot.skill(Skill.MAGIC).level < spell.level) continue
            val casts = CombatLoadout.runeCosts(spell, weapon).minOfOrNull { (id, quantity) ->
                bot.inventory.computeAmountForId(id) / quantity
            } ?: continue
            val projectileCycles = spell.projectile?.apply(bot, target)?.endCycle ?: 0
            val impactDelay = maxOf(1, 1 + projectileCycles / 30)
            val lastLaunch = PRESSURE_TICKS - 1 - impactDelay
            if (casts == 0 || first > lastLaunch) continue
            val launches = minOf(casts, 1 + (lastLaunch - first) / speed)
            val impact = now + first + (launches - 1) * speed + impactDelay
            if (!target.status.isFreezeImmuneAt(impact)) return true
        }
        return false
    }
}
