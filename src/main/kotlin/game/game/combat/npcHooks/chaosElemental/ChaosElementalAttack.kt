package game.combat.npcHooks.chaosElemental

import engine.obj.LadderDestination
import game.player.Sound
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * One of the Chaos Elemental's three attacks: Discord deals damage, Confusion teleports the player nearby and Madness
 * unequips their gear. Each one is a projectile that lands on the tick after the attack, at any distance; the hit,
 * the impact graphic and Madness's unequip come with it, and Confusion's teleport a tick later.
 *
 * @param style The combat style Discord's damage uses. Confusion and Madness deal no damage.
 */
class ChaosElementalAttack(npc: Npc, other: Mob, val kind: Kind, val style: CombatDamageType = randomStyle()) :
    CombatAttack<Npc>(npc, other, InteractionPolicy(InteractionType.LINE_OF_SIGHT, RANGE),
        npc.combatDef().attackSpeed) {

    /**
     * The look and sound of each attack: body animation, cast graphic on the boss, projectile, impact graphic on the
     * player, and the cast and impact sounds.
     */
    enum class Kind(val animation: Int, val cast: Int, val projectile: Int, val impact: Int, val castSound: Sound,
                    val impactSound: Sound) {
        DISCORD(3148, 556, 557, 558, Sound.CHAOS_ELEMENTAL_DISCORD_CAST, Sound.CHAOS_ELEMENTAL_DISCORD_IMPACT),
        CONFUSION(3149, 553, 554, 555, Sound.CHAOS_ELEMENTAL_CONFUSION_CAST,
            Sound.CHAOS_ELEMENTAL_CONFUSION_IMPACT),
        MADNESS(3149, 550, 551, 552, Sound.CHAOS_ELEMENTAL_MADNESS_CAST, Sound.CHAOS_ELEMENTAL_MADNESS_IMPACT)
    }

    companion object {

        const val CHAOS_ELEMENTAL = 3200

        /** Reach of all three attacks, in tiles, with line of sight. */
        const val RANGE = 10

        /** Ticks from the attack to the hit, the impact and Madness's unequip, the same at every distance. */
        const val HIT_DELAY = 1

        /** Ticks from the attack to Confusion's teleport, one after its impact shows. */
        const val TELEPORT_DELAY = 2

        /** Client cycles (30 per tick) from the attack until the projectile leaves, and until it lands with the hit. */
        const val LAUNCH_CYCLE = 15
        const val LANDING_CYCLE = HIT_DELAY * 30

        /** The cast graphic and the projectile start at the top of the cloud: its model floats 100 to 415 units up. */
        const val CAST_HEIGHT = 400
        const val START_HEIGHT = 100
        const val END_HEIGHT = 31
        const val SLOPE = 16
        const val LAUNCH_OFFSET = 64

        /** Impacts sit at chest height, like spell impacts. */
        const val IMPACT_HEIGHT = 100

        /** Confusion moves the player to a random tile up to this far from the middle of the boss. */
        const val TELEPORT_RADIUS = 8
        const val TELEPORT_TRIES = 50

        /** Madness moves at most this many worn items to the inventory. */
        const val MADNESS_ITEMS = 4

        /** Worn slots Madness can empty after the weapon. */
        val OTHER_SLOTS = listOf(Equipment.HEAD, Equipment.CAPE, Equipment.AMULET, Equipment.CHEST, Equipment.SHIELD,
            Equipment.LEGS, Equipment.HANDS, Equipment.BOOTS, Equipment.RING, Equipment.AMMUNITION)

        private val STYLES = listOf(CombatDamageType.MELEE, CombatDamageType.RANGED, CombatDamageType.MAGIC)

        fun randomStyle(random: Random = ThreadLocalRandom.current()): CombatDamageType =
            STYLES[random.nextInt(STYLES.size)]

        /**
         * Picks the next attack at random. Madness is left out when the target has no free inventory slot, so the
         * other two come more often; an NPC target only ever gets Discord.
         */
        fun choose(target: Mob, random: Random): Kind {
            if (target !is Player) return Kind.DISCORD
            val kinds = if (target.inventory.computeRemainingSize() > 0) Kind.entries else
                listOf(Kind.DISCORD, Kind.CONFUSION)
            return kinds[random.nextInt(kinds.size)]
        }

        /** The middle tile of the boss's 3x3 body. */
        fun centre(npc: Npc): Position = npc.position.translate(npc.size() / 2, npc.size() / 2)

        /**
         * Finds where Confusion sends [player]: a random tile within [TELEPORT_RADIUS] of the boss's middle that a
         * player can stand on, outside the boss's body and not the player's own tile. Null if none turns up.
         */
        fun teleportDestination(npc: Npc, player: Player, random: Random,
                                canLand: (Position) -> Boolean = LadderDestination::canLand): Position? {
            val centre = centre(npc)
            repeat(TELEPORT_TRIES) {
                val tile = centre.translate(random.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS,
                    random.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS)
                if (!under(npc, tile) && tile != player.position && canLand(tile)) return tile
            }
            return null
        }

        private fun under(npc: Npc, tile: Position): Boolean =
            tile.x >= npc.position.x && tile.x < npc.position.x + npc.size() &&
                tile.y >= npc.position.y && tile.y < npc.position.y + npc.size()

        /** The worn slots Madness empties, in order: the weapon first, then the rest at random. */
        fun unequipOrder(player: Player, random: Random): List<Int> {
            val weapon = if (player.equipment.get(Equipment.WEAPON) != null) listOf(Equipment.WEAPON) else emptyList()
            return weapon + OTHER_SLOTS.filter { player.equipment.get(it) != null }.shuffled(random)
        }
    }

    override fun calculateDamage(other: Mob): CombatDamage {
        if (kind != Kind.DISCORD) return CombatDamageRequest.zero(attacker, other).resolve()
        val request = CombatDamageRequest.builder(attacker, other, style)
        if (style == CombatDamageType.MELEE) {
            request.setBaseAccuracy(CombatFormula.calculateHitChance(attacker, other, style, 100,
                EquipmentBonus.CRUSH_DEFENCE))
        }
        val damage = request.build().resolve()
        // A missed magic roll shows a 0, as for other NPC magic, rather than a splash.
        return if (damage.amount.isPresent) damage else
            CombatDamageRequest.zero(attacker, other, CombatDamageType.MAGIC).resolve()
    }

    override fun attack() {
        val target = victim
        attacker.animation(Animation(kind.animation))
        attacker.graphic(Graphic(kind.cast, CAST_HEIGHT, 0))
        (target as? Player)?.playSound(kind.castSound)
        LocalProjectile.followEntity(attacker.context)
            .setSourceEntity(attacker)
            .setSourcePosition(centre(attacker))
            .setTargetEntity(target)
            .setId(kind.projectile)
            .setTiming(LAUNCH_CYCLE, LANDING_CYCLE)
            .setLaunchOffset(LAUNCH_OFFSET)
            .setStartHeight(START_HEIGHT)
            .setEndHeight(END_HEIGHT)
            .setInitialSlope(SLOPE)
            .build()
            .display()
        val damage = nextDamage
        target.combat.impacts.schedule(HIT_DELAY, { land(target, damage) }, {})
    }

    /**
     * The projectile reaches [target]: the impact graphic and sound, then the attack's effect.
     */
    private fun land(target: Mob, damage: CombatDamage) {
        if (!target.isAlive) return
        target.graphic(Graphic(kind.impact, IMPACT_HEIGHT, 0))
        (target as? Player)?.playSound(kind.impactSound)
        when (kind) {
            Kind.DISCORD -> target.submitAction(CombatDamageAction(damage, this, true))
            // The player sees the impact where they stand before being moved.
            Kind.CONFUSION -> if (target is Player) target.combat.impacts.schedule(TELEPORT_DELAY - HIT_DELAY, {
                if (target.isAlive && stillNear(target)) confuse(target)
            }, {})
            Kind.MADNESS -> if (target is Player && stillNear(target)) madden(target)
        }
    }

    /**
     * False once the player has left (a teleport away, a ladder), so Confusion never pulls them back to the boss.
     */
    private fun stillNear(player: Player): Boolean =
        player.position.isWithinDistance(attacker.position, Position.VIEWING_DISTANCE) && !player.isActionLocked

    /**
     * Teleports the player nearby. They stop attacking and have to click the boss again.
     */
    private fun confuse(player: Player) {
        val destination = teleportDestination(attacker, player, ThreadLocalRandom.current()) ?: return
        player.navigator.cancel()
        player.move(destination)
        player.combat.target = null
        player.combat.autoRetaliateTarget = null
    }

    /**
     * Moves up to [MADNESS_ITEMS] worn items to the inventory, weapon first, never more than fit.
     */
    private fun madden(player: Player) {
        var moved = 0
        for (slot in unequipOrder(player, ThreadLocalRandom.current())) {
            if (moved == MADNESS_ITEMS) break
            if (player.equipment.unequip(slot)) moved++
        }
    }
}
