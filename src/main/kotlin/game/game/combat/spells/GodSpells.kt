package game.combat.spells

import engine.combat.status.StatusEffectType
import game.content.mageArena.MageArena
import game.player.Sound
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import io.luna.game.model.EntityState
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatSpell

/**
 * Revision-377 god spell combat effects and the Charge spell.
 * Matching god capes and staves enable the drains and charged maximum hit. Drains occur on accurate casts,
 * including zero damage, before flight; later equipment changes cannot grant or remove their effects.
 * Mage Arena progression controls access to Charge; combat cast progression is checked before rune removal.
 *
 * @author lare96
 */
object GodSpells {

    /**
     * Charge costs three fire, blood, and air runes, with the normal staff and combination-rune substitutions.
     */
    private val chargeRunes = listOf(RuneRequirement(Rune.FIRE, 3), RuneRequirement(Rune.BLOOD, 3),
                                   RuneRequirement(Rune.AIR, 3))

    /**
     * Casts or refreshes Charge after checking the regular spellbook, level 80, and rune supplies.
     * Successful casts grant 180 Magic experience and seven minutes of online charge time.
     */
    fun charge(player: Player): Boolean {
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.isActionLocked ||
            player.spellbook != Spellbook.REGULAR) return false
        if (MageArena.stage(player) < MageArena.STAFF_GIVEN) {
            player.sendMessage("This spell can only be learnt in the Mage Arena.")
            return false
        }
        val runes = Magic.checkRequirements(player, 80, chargeRunes) ?: return false
        if (!player.inventory.containsAll(runes)) return false
        player.inventory.removeAll(runes)
        player.overlays.closeWindows()
        player.status.add(ChargeStatusEffect(player))
        player.animation(Animation(811))
        player.graphic(Graphic(111, 128, 32))
        player.playSound(Sound.CHARGE)
        player.skill(Skill.MAGIC).addExperience(180.0)
        return true
    }

    /**
     * Resolves the spell's maximum at damage-roll time. Charge raises matching god spells from 20 to 30;
     * other spells, missing capes, and mismatched equipment retain their configured maximum.
     */
    fun maxHit(player: Player, spell: CombatSpellDefinition): Int =
        if (StatusEffectType.CHARGED in player.status && hasEquipment(player, spell.spell))
            spell.maxHit * 3 / 2 else spell.maxHit

    /**
     * Applies the reference's cast-time drains. Saradomin drains one player prayer point. Guthix drains Defence
     * and Zamorak drains Magic by one plus five percent, only when the target stat equals its base level.
     * Boosted or already-drained stats are left alone; this restriction does not prevent damage or consume extra runes.
     */
    fun castEffect(player: Player, victim: Mob, spell: CombatSpell, accurate: Boolean) {
        if (!accurate || !victim.isAlive || !hasEquipment(player, spell)) return
        val skillId = when (spell) {
            CombatSpell.SARADOMIN_STRIKE -> {
                if (victim is Player) {
                    val prayer = victim.skill(Skill.PRAYER)
                    prayer.adjustLevel(-1)
                    if (prayer.level == 0) victim.combat.prayers.deactivateAll()
                }
                return
            }
            CombatSpell.CLAWS_OF_GUTHIX -> Skill.DEFENCE
            CombatSpell.FLAMES_OF_ZAMORAK -> Skill.MAGIC
            else -> return
        }
        val skill = victim.skill(skillId)
        if (skill.level != skill.staticLevel) return
        skill.adjustLevel(-(1 + skill.staticLevel / 20))
        if (victim is Player) {
            victim.sendMessage(if (skillId == Skill.DEFENCE) "You feel weakened."
                               else "You feel your magical powers weaken.")
        }
    }

    /**
     * Checks the matching cape and staff slots required by the original god spell scripts.
     */
    private fun hasEquipment(player: Player, spell: CombatSpell): Boolean {
        val cape = when (spell) {
            CombatSpell.SARADOMIN_STRIKE -> 2412
            CombatSpell.CLAWS_OF_GUTHIX -> 2413
            CombatSpell.FLAMES_OF_ZAMORAK -> 2414
            else -> return false
        }
        return player.equipment.computeIdForIndex(Equipment.CAPE) == cape &&
                player.equipment.computeIdForIndex(Equipment.WEAPON) == cape + 3
    }
}
