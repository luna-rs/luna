package game.content.mageArena

import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import engine.combat.prayer.CombatPrayer
import io.luna.game.model.mob.combat.CombatSpell
import kotlin.random.Random

/**
 * The pinned Kolodion AP/OP spell ranges and the battle mages' own-god protection.
 * RuneScript random_range is inclusive: Flames=42, Claws=43, Saradomin=44.
 *
 * @author lare96
 */
object MageArenaCombat {
    fun bossSpells(id: Int, close: Boolean): List<CombatSpell> = when (id) {
        907, 908 -> if (close) listOf(CombatSpell.CLAWS_OF_GUTHIX, CombatSpell.SARADOMIN_STRIKE)
                    else listOf(CombatSpell.FLAMES_OF_ZAMORAK, CombatSpell.CLAWS_OF_GUTHIX, CombatSpell.SARADOMIN_STRIKE)
        909 -> listOf(CombatSpell.FLAMES_OF_ZAMORAK, CombatSpell.CLAWS_OF_GUTHIX)
        910 -> listOf(CombatSpell.CLAWS_OF_GUTHIX, CombatSpell.SARADOMIN_STRIKE)
        911 -> listOf(CombatSpell.FLAMES_OF_ZAMORAK, CombatSpell.CLAWS_OF_GUTHIX, CombatSpell.SARADOMIN_STRIKE)
        else -> emptyList()
    }

    fun spell(id: Int, close: Boolean, random: Random = Random.Default): CombatSpell = when (id) {
        912 -> CombatSpell.FLAMES_OF_ZAMORAK
        913 -> CombatSpell.SARADOMIN_STRIKE
        914 -> CombatSpell.CLAWS_OF_GUTHIX
        else -> bossSpells(id, close).random(random)
    }

    fun matchingEquipment(npc: Npc, player: Player): Boolean {
        val god = when (npc.id) { 912 -> MageArena.God.ZAMORAK; 913 -> MageArena.God.SARADOMIN; 914 -> MageArena.God.GUTHIX; else -> return false }
        return player.equipment.computeIdForIndex(Equipment.WEAPON) == god.staff &&
            player.equipment.computeIdForIndex(Equipment.CAPE) == god.cape
    }

    fun mayAggress(npc: Npc, player: Player): Boolean = MageArena.live(player) && MageArena.completed(player) &&
        MageArena.arena(player.position) && !matchingEquipment(npc, player)

    fun mayAttack(npc: Npc, player: Player): Boolean = MageArena.live(player) && MageArena.completed(player) &&
        (npc.combat.lastAttackReceived?.attacker === player || !matchingEquipment(npc, player))

    /**
     * The NPC spell table applies its stat_change fields on accurate casts, before impact. Unlike the
     * player equipment effects, these do not require a cape or an unchanged base stat. Protection from Magic
     * makes the reference's NPC attack roll zero, so protected targets receive neither damage nor drains.
     */
    fun castEffect(player: Player, spell: CombatSpell, accurate: Boolean) {
        if (!accurate || !player.isAlive || player.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MAGIC)) return
        val skill = when (spell) {
            CombatSpell.SARADOMIN_STRIKE -> player.skill(Skill.PRAYER)
            CombatSpell.CLAWS_OF_GUTHIX -> player.skill(Skill.DEFENCE)
            CombatSpell.FLAMES_OF_ZAMORAK -> player.skill(Skill.MAGIC)
            else -> return
        }
        skill.adjustLevel(-(1 + if (spell == CombatSpell.SARADOMIN_STRIKE) 0 else skill.level / 20))
        if (spell == CombatSpell.SARADOMIN_STRIKE && skill.level == 0) player.combat.prayers.deactivateAll()
        player.sendMessage("You feel weakened.")
    }
}
