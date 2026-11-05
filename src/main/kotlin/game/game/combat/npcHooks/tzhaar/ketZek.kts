package game.combat.npcHooks.tzhaar

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/** The Ket-Zek's melee attack, which hits harder than its magic. */
val KET_ZEK_MELEE_ANIMATION = 2644
val KET_ZEK_MELEE_MAX_HIT = 55

/** The fireball it flings from its tail. */
val KET_ZEK_MAGIC_ANIMATION = 2647
val KET_ZEK_FIREBALL = 445
val KET_ZEK_IMPACT = 446
val KET_ZEK_MAGIC_RANGE = 15

/**
 * The Ket-Zek's fireball. It leaves from the middle of its body and lands 32 cycles plus 10 per tile later, like other
 * NPC spells; the hit comes when it lands. Max hit and accuracy come from its data.
 */
class KetZekMagic(attacker: Npc, victim: Mob) : MagicCombatAttack<Npc>(
    attacker,
    victim,
    Animation(KET_ZEK_MAGIC_ANIMATION),
    null,
    { npc, target ->
        val source = JadAttack.launchTile(npc as Npc, target)
        LocalProjectile.followEntity(npc.context)
            .setSourceEntity(npc)
            .setSourcePosition(source)
            .setTargetEntity(target)
            .setId(KET_ZEK_FIREBALL)
            .setTiming(32, 32 + source.computeLongestDistance(target.position) * 10)
            .setStartHeight(100)
            .setEndHeight(5)
            .setInitialSlope(16)
            .build()
    },
    Graphic(KET_ZEK_IMPACT),
    null,
    null,
    attacker.combatDef().attackSpeed,
    KET_ZEK_MAGIC_RANGE) {

    override fun usesProjectileTiming() = true

    override fun calculateDamage(other: Mob): CombatDamage =
        CombatDamageRequest.Builder(attacker, other, CombatDamageType.MAGIC)
            .setBaseMaxHit(attacker.combatDef().maximumHit).build().resolve()
}

/**
 * The Ket-Zek flings fireballs from up to 15 tiles away. Beside one of its edges it mixes them with melee, which hits
 * harder.
 *
 * @author TheLining
 */
combat(2743, 2744) {
    attack {
        if (JadAttack.besideEdge(npc, other) && randBoolean()) {
            melee(animationId = KET_ZEK_MELEE_ANIMATION, maxHit = KET_ZEK_MELEE_MAX_HIT)
        } else {
            KetZekMagic(npc, other)
        }
    }
}
