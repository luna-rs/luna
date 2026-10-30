package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.slayer.Slayer.drainCombatSkills
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The wearable earmuffs item id.
 */
val EARMUFFS = 4166

/**
 * The banshee scream graphic.
 */
val GRAPHIC = Graphic(337, 150, 0) // TODO@0.5.0 Projectile looks a bit weird.

/**
 * Missing earmuffs cause a screech on every launched attack: fixed damage of ten percent of base hitpoints,
 * twenty percent drains to offensive skills, and ten percent drains to defence and prayer. Agility is unaffected.
 * Protect from Melee blocks damage but not drains. Equipment, sounds, and graphics are handled at launch so
 * approaching a target cannot trigger effects early or retain an obsolete equipment check.
 */
combat(1612) {
    attack {
        melee(sound = {
            if (other is Player && other.equipment.head?.id != EARMUFFS && Luna.settings().skills().slayerEquipmentNeeded()) {
                Sound.BANSHEE_ATTACK_NORMAL.id
            } else {
                Sound.BANSHEE_ATTACK_EARMUFFS.id
            }
        }) {
            if (other is Player && other.equipment.head?.id != EARMUFFS && Luna.settings().skills().slayerEquipmentNeeded()) {
                val screechHit = CombatDamageRequest.builder(npc, other, CombatDamageType.MELEE)
                    .setBaseAccuracy(1.0)
                    .setFlatBonusAccuracy(1.0)
                    .setBaseMaxHit(0)
                    .setFlatBonusDamage(other.hitpoints.staticLevel / 10)
                    .build().resolve()
                other.drainCombatSkills(20, 10, drainAgility = false)
                other.sendMessage("You have been weakened.")
                npc.graphic(GRAPHIC)
                screechHit
            } else {
                it
            }
        }
    }
}
