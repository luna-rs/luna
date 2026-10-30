package game.combat.npcHooks.zygomite

import api.predef.slayer
import io.luna.game.model.LocalProjectile
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.RangedCombatAttack
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Zanaris fungi reveal their matching zygomite when picked. Charged fungicide finishes a weakened creature through
 * the ordinary attributed execution pipeline, without letting earlier queued hits or poison substitute for a spray.
 * Charge state is the cache item type and survives ordinary inventory, bank and account persistence.
 *
 * @author lare96
 */
object Zygomites {

    /**
     * Dormant cache forms and their matching live task IDs.
     */
    val FORMS = mapOf(3344 to 3346, 3345 to 3347)

    /**
     * Full and empty spray IDs delimit the ten cache charge states. Each subsequent ID has one fewer charge.
     */
    const val FULL_SPRAY = 7421
    const val EMPTY_SPRAY = 7431
    const val FUNGICIDE = 7432

    /**
     * Later client weakness data supports spraying at seven remaining hitpoints or fewer.
     * This conservative boundary is used because the revision 377 encounter script is unavailable.
     */
    const val FINISH_HEALTH = 7

    /**
     * Reconstructed line-of-sight range and contact spore frequency. Later descriptions establish occasional ranged
     * spores, but do not recover these revision 377 constants; the contact roll succeeds once in four attempts.
     */
    const val SPORE_RANGE = 5
    const val SPORE_CHANCE = 4

    /**
     * Reconstructed presentation delays prevent a first attack from replacing the emergence animation immediately.
     * The finishing delay also leaves the pump animation visible before attributed execution.
     */
    const val AWAKEN_DELAY = 2
    const val FINISH_DELAY = 1

    /**
     * Creates magical ranged spores with their own damage family, magic-defence accuracy and native missile prayer.
     * Damage maxima follow later normal-Zygomite observations. Cache graphic 576 is verified; its straight flight,
     * heights and 46-plus-five-cycles-per-tile timing are reconstructed using Luna's projectile arrival pipeline.
     */
    internal fun sporeAttack(npc: Npc, other: Mob): CombatAttack<Npc> =
        object : RangedCombatAttack<Npc>(npc, other, 3326, null, { attacker, victim ->
            LocalProjectile.followEntity(attacker.context).setSourceEntity(attacker).setTargetEntity(victim)
                .setId(576).setTicksToStart(0)
                .setTicksToEnd(46 + 5 * attacker.position.computeLongestDistance(victim.position))
                .setStartHeight(40).setEndHeight(36).setInitialSlope(0).build()
        }, null, 4, SPORE_RANGE) {
            override fun usesProjectileTiming(): Boolean = true

            override fun calculateDamage(other: Mob): CombatDamage =
                CombatDamageRequest.Builder(attacker, other, CombatDamageType.RANGED)
                    .setBaseMaxHit(if (attacker.id == 3346) 6 else 10)
                    .setBaseAccuracy(CombatFormula.calculateMagicDefendedRangedHitChance(attacker, other))
                    .build().resolve()
        }

    /**
     * Returns the remaining doses represented by this cache item type, or zero for another item.
     */
    fun charges(id: Int): Int = if (id in FULL_SPRAY..EMPTY_SPRAY) EMPTY_SPRAY - id else 0

    /**
     * Finds a real charged spray in inventory; equipped sprays do not authorize item-on-NPC packets.
     */
    fun carriedSpray(player: Player): Int? = player.inventory.firstOrNull { it != null && charges(it.id) > 0 }?.id

    /**
     * Reveals one dormant NPC in place, preserving its identity for the targeting session and later respawn.
     */
    internal fun pick(player: Player, npc: Npc): Boolean {
        val form = FORMS[npc.id] ?: return false
        if (!ready(player, npc) || player.slayer.level < 57 || npc.combat.isExecutionPending) return false
        player.animation(Animation(3335))
        npc.navigator.cancel()
        npc.walking.clear()
        npc.transform(form)
        if (npc.respawnTicks < 1) npc.respawnTicks = npc.combatDef().respawnTime
        npc.walking.isLocked = false
        npc.combat.isDisabled = false
        npc.animation(Animation(3330))
        npc.combat.attack(player)
        npc.combat.attackDelay = AWAKEN_DELAY
        return true
    }

    /**
     * Replaces the exact spray slot and consumes one refill only after both inventory slots have been validated.
     * Bulk mutation prevents a full inventory from losing either item through a remove/add capacity race.
     */
    internal fun refill(player: Player, sprayIndex: Int, refillIndex: Int): Boolean {
        if (player.isLocked || !player.isAlive || sprayIndex == refillIndex ||
            sprayIndex !in 0 until player.inventory.capacity() || refillIndex !in 0 until player.inventory.capacity()) return false
        val spray = player.inventory[sprayIndex] ?: return false
        val refill = player.inventory[refillIndex] ?: return false
        if (spray.id != EMPTY_SPRAY || spray.amount != 1 || refill.id != FUNGICIDE) return false
        player.inventory.startBulkUpdate()
        try {
            player.inventory[sprayIndex] = Item(FULL_SPRAY)
            player.inventory[refillIndex] = if (refill.amount == 1) null else refill.withAmount(refill.amount - 1)
        } finally {
            player.inventory.finishBulkUpdate()
        }
        player.sendMessage("You refill the fungicide spray.")
        return true
    }

    /**
     * Reserves one adjacent finishing sequence. State is checked again before the charge changes, and a spawn counter
     * prevents an interrupted old action from killing a new life of the same NPC. The one-tick sequence is a
     * reconstructed presentation delay, rather than a recovered Lost City script constant.
     */
    internal fun spray(player: Player, npc: Npc, sprayIndex: Int): Boolean {
        if (!ready(player, npc) || npc.id !in FORMS.values || npc.health > FINISH_HEALTH ||
            player.slayer.level < 57 || npc.combat.isExecutionPending ||
            sprayIndex !in 0 until player.inventory.capacity() || charges(player.inventory[sprayIndex]?.id ?: -1) == 0)
            return false
        val life = npc.spawnCount
        val form = npc.id
        val source = SprayAttack(player, npc)
        val action = object : LockedAction(player, false, FINISH_DELAY) {
            override fun beforeLock(): Boolean = ready(player, npc) && npc.spawnCount == life && npc.id == form &&
                npc.health <= FINISH_HEALTH && charges(player.inventory[sprayIndex]?.id ?: -1) > 0

            override fun onLock() {
                val item = player.inventory[sprayIndex]!!
                player.inventory[sprayIndex] = Item(item.id + 1)
                npc.combat.prepareForExecution()
                player.animation(Animation(3331))
                player.graphic(Graphic(575, 0, 0))
                player.sendMessage("You spray fungicide on the zygomite.")
            }

            override fun onUnlock() {
                npc.combat.clearExecution(this)
            }

            override fun run(): Boolean {
                if (npc.state == EntityState.ACTIVE && npc.spawnCount == life && npc.id == form && npc.isAlive) {
                    npc.combat.finishExecution(source)
                }
                return true
            }
        }
        npc.combat.trackExecution(action)
        player.submitAction(action)
        return !action.isFinished
    }

    /**
     * Shared native item reach and participant safety, rechecked when the delayed sequence acquires its lock.
     */
    private fun ready(player: Player, npc: Npc): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && npc.state == EntityState.ACTIVE && npc.isAlive &&
        player.world === npc.world && player.world.collisionManager.reached(player, npc, InteractionPolicy.STANDARD_SIZE) &&
        player.combat.checkMultiCombat(npc)

    /**
     * An attributed item finish for the existing native execution API. It never launches a second ordinary hit.
     *
     * @author lare96
     */
    private class SprayAttack(player: Player, npc: Npc) :
        CombatAttack<Player>(player, npc, InteractionPolicy.STANDARD_SIZE, 1) {
        override fun attack() = Unit
        override fun calculateDamage(other: Mob): CombatDamage = CombatDamageRequest.effect(attacker, other).resolve()
    }
}
