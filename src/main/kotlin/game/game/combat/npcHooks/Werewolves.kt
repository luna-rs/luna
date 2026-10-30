package game.combat.npcHooks

import api.attr.Attr
import api.predef.ext.scheduleOnce
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.damage.CombatDamage

/**
 * Canifis citizens change into their matching werewolf before receiving a numeric player hit, unless that player
 * has Wolfbane equipped. Lost City's `canafis_citizen` damage queue changes type for 500 ticks before applying the
 * original hit. A transformation therefore restores the wolf's combat levels without discarding that hit or
 * awarding a separate kill. Splashes and effect-only spells do not run that numeric damage queue.
 *
 * @author lare96
 */
object Werewolves {

    /**
     * Canifis human forms and their corresponding Wolfman or Wolfwoman forms in the revision 377 cache.
     */
    val FORMS = (1024..1029).associateWith { it + 6 }

    /**
     * The equipped quest dagger that prevents the citizen's transformation.
     */
    private const val WOLFBANE = 2952

    /**
     * A temporary form lasts this many ticks, matching the source's `npc_changetype` duration.
     */
    private const val TRANSFORM_TICKS = 500

    /**
     * Identifies this particular life and transformation. Respawning clears NPC attributes, so an old return task
     * cannot transform a respawned citizen or overwrite a later transformation of the same NPC instance.
     */
    private var Npc.returnToken by Attr.nullableObj(Any::class)

    /**
     * Changes form before the ordinary damage action applies its unchanged incoming hit. Transformation retains
     * combat ownership and the damage stack; the native action remains responsible for retaliation and kill credit.
     * Returns whether the caller should suppress the old human defence animation for this hit.
     */
    internal fun beforeHit(npc: Npc, other: Mob, damage: CombatDamage?): Boolean {
        val wolf = FORMS[npc.id] ?: return false
        val player = other as? Player ?: return false
        if (npc.state != EntityState.ACTIVE || !npc.isAlive || player.state != EntityState.ACTIVE ||
            player.equipment[Equipment.WEAPON]?.id == WOLFBANE ||
            damage == null || damage.amount.isEmpty || damage.rawAmount < 0) return false

        val human = npc.id
        npc.animation(Animation.CANCEL)
        npc.transform(wolf)
        val token = Any()
        npc.returnToken = token
        npc.world.scheduleOnce(TRANSFORM_TICKS) {
            if (npc.returnToken === token) {
                npc.returnToken = null
                if (npc.state == EntityState.ACTIVE && npc.isAlive && npc.id == wolf && npc.baseId == human) {
                    npc.resetTransform()
                }
            }
        }
        return true
    }
}
