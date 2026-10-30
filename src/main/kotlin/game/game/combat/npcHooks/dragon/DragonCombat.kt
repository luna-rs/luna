package game.combat.npcHooks.dragon

import api.attr.Attr
import api.combat.npc.dsl.NpcAttackCombatHookReceiver
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.path.route.LineOfSight
import java.lang.ref.WeakReference
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * Adult chromatic and metal dragon approach/operation modes from Lost City's `npc/scripts/dragon.rs2`.
 * Reach checks follow the current mode without consuming attack rolls. A launch chooses its attack and next mode
 * only after pursuit reaches the required tile, including the first attack reached during post-movement processing.
 * The reference describes these adult mode transitions as a reconstruction; these rules follow that supplied source.
 *
 * @author lare96
 */
object DragonCombat {

    /**
     * The source's operation mode, which pursues melee reach, and approach mode, which breathes from five tiles.
     *
     * @author lare96
     */
    internal enum class Mode { MELEE, FIRE }

    /**
     * The attack to deliver now and the mode that will govern its next launch.
     *
     * @author lare96
     */
    internal data class Choice(val fire: Boolean, val nextMode: Mode)

    /**
     * One combat action's mode. Both target and action are weak: a finished action may still reference its previous
     * pursuit target. Only encounters already owned by a live action are cached; attack inspection without an action
     * cannot prepare state that a later hunt would inherit.
     *
     * @author lare96
     */
    private class Encounter(target: Mob, combatAction: CombatAction?, var mode: Mode) {
        val target = WeakReference(target)
        val action = WeakReference(combatAction)
    }

    /**
     * Transient encounter ownership is reset with the NPC's other attributes on respawn.
     */
    private var Npc.encounter by Attr.nullableObj(Encounter::class)

    /**
     * Starts the source's default melee mode only after native retaliation accepts its target and action. Failed
     * attempts cannot prepare state, and ordinary hits in an already engaged encounter retain their current mode.
     */
    internal fun retaliate(npc: Npc, attacker: Mob, action: CombatAction) {
        npc.encounter = Encounter(attacker, action, Mode.MELEE)
    }

    /**
     * Fire mode still delivers fire when it switches to operation mode. Chromatic dragons may make that switch at
     * a distance when there is a direct walking line; metal dragons also require the source's coordinate distance
     * to be below two. Operation mode independently rolls its next mode and its current one-in-four breath attack.
     */
    internal fun choose(mode: Mode, metal: Boolean, adjacent: Boolean, lineOfWalk: Boolean,
                        random: Random = ThreadLocalRandom.current()): Choice {
        if (mode == Mode.FIRE) {
            val melee = (!metal || adjacent) && random.nextInt(2) == 0 && lineOfWalk
            return Choice(true, if (melee) Mode.MELEE else Mode.FIRE)
        }
        val next = if (random.nextInt(8) == 0) Mode.FIRE else Mode.MELEE
        return Choice(random.nextInt(4) == 0, next)
    }

    /**
     * Initializes the source hunt mode only for a new encounter: metal dragons hunt in approach mode, while the
     * chromatic cowardly hunt mode selects operation mode. Calls outside an active combat action remain uncached.
     */
    private fun encounter(npc: Npc, target: Mob, metal: Boolean): Encounter {
        val action = npc.actions.first(CombatAction::class.java)
        val previous = npc.encounter
        if (action != null && previous != null && previous.target.get() === target && previous.action.get() === action) {
            return previous
        }
        return Encounter(target, action, if (metal) Mode.FIRE else Mode.MELEE).also {
            npc.encounter = if (action == null) null else it
        }
    }

    /**
     * Supplies one mixed native attack without selecting an outcome or changing the mode during cooldown/pursuit.
     */
    internal fun attack(receiver: NpcAttackCombatHookReceiver, metal: Boolean,
                        random: Random = ThreadLocalRandom.current()): CombatAttack<out Npc> = with(receiver) {
        Attack(npc, other, metal, encounter(npc, other, metal), random)
    }

    /**
     * A single committed dragon turn. Fire reuses the ordinary dragonfire implementation; melee returns a real melee
     * damage payload and queues the native NPC melee hit through the same attack instance. The shared apply pipeline
     * runs once, so attack timers, launch metadata, poison handling, history and MonsterStyles retain one source.
     *
     * @author lare96
     */
    private class Attack(npc: Npc, victim: Mob, private val metal: Boolean,
                         private val encounter: Encounter, private val random: Random) :
        DragonFireCombatAttack(npc, victim, range = 5) {

        /**
         * The current attack is captured during damage preparation; its next mode is committed by onAttack.
         */
        private var choice = Choice(false, encounter.mode)

        override fun getInteractionPolicy(): InteractionPolicy =
            if (encounter.mode == Mode.FIRE) InteractionPolicy(InteractionType.LINE_OF_SIGHT, 5)
            else InteractionPolicy.STANDARD_SIZE

        override fun calculateDamage(other: Mob): CombatDamage {
            val from = attacker.position
            val to = other.position
            val walk = from.z == to.z && LineOfSight.hasLineOfWalk(attacker.world.collisionManager.view(false),
                from.z, from.x, from.y, to.x, to.y, 1, 1, 1, 1, 0)
            choice = choose(encounter.mode, metal, from.computeLongestDistance(to) < 2, walk, random)
            return if (choice.fire) super.calculateDamage(other)
            else CombatDamageRequest.Builder(attacker, other, CombatDamageType.MELEE).build().resolve()
        }

        override fun onAttack(damage: CombatDamage?): CombatDamage? {
            if (damage != null) encounter.mode = choice.nextMode
            return super.onAttack(damage)
        }

        override fun getNpcAttackSound(): Int = if (choice.fire) -1 else attacker.combatDef().attackSound

        override fun attack() {
            if (choice.fire) {
                super.attack()
            } else {
                attacker.animation(Animation(if (random.nextBoolean()) 91 else 80, Animation.AnimationPriority.HIGH))
                victim.combat.impacts.schedule(0, {
                    victim.submitAction(CombatDamageAction(nextDamage, this, true))
                }, { interrupted -> onProjectileFinished(interrupted) })
            }
        }
    }
}
