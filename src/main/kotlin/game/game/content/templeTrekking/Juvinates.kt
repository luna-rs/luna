package game.content.templeTrekking

import api.predef.*
import api.predef.ext.*
import game.content.treasureTrails.TrailClues
import game.content.treasureTrails.TrailTier
import game.content.treasureTrails.TreasureTrails
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import kotlin.random.Random

/**
 * The trek's vampyre juvinates. Only silver hurts them; the Rod of Ivandis holds one that is below half health,
 * and Guthix balance poured on a held one kills it, cures it or angers it.
 *
 * @author TheLining
 */
object Juvinates {

    /**
     * The juvinates on each route.
     */
    val IDS = setOf(3576, 3577, 3578)

    /**
     * The Rod of Ivandis, from ten charges to one, and the dust it leaves.
     */
    val RODS = (7639..7648).toList()
    const val ROD_DUST = 7636

    /**
     * The silver weapons that hurt a juvinate.
     */
    val SILVER = setOf(2961, 2963, 2952, 2402, 6745, 6746) + RODS

    /**
     * Guthix balance from four doses to one, and the vial it leaves.
     */
    val BALANCE = listOf(7660, 7662, 7664, 7666)
    const val VIAL = 229

    /**
     * Who a cured juvinate turns back into.
     */
    val HUMANS = listOf(3570, 3571, 3572, 3573)

    const val VAMPIRE_DUST = 3325

    /**
     * How long the rod holds a juvinate: 30 seconds.
     */
    const val HOLD_TICKS = 50

    /**
     * Prayer XP for destroying a juvinate with Guthix balance.
     */
    const val DESTROY_XP = 100.0

    private const val CAST = 3547
    private const val CAST_GRAPHIC = 604
    private const val HOLD_PROJECTILE = 605
    private const val HOLD_HIT = 606
    private const val POUR = 3553
    private const val SPIRIT_FADE = 3554
    private const val SPIRIT_GRAPHIC = 607
    private const val CRUMBLE = 3555
    private const val DUST_GRAPHIC = 608

    /**
     * When each held juvinate is let go.
     */
    private val held = HashMap<Npc, Long>()

    /**
     * A cured juvinate's thanks: one item, noted where it can be, out of 256.
     */
    private class Reward(val id: Int, val amount: Int, val weight: Int)

    private val REWARDS = listOf(
        Reward(1381, 1, 1), Reward(1387, 1, 2), Reward(1383, 1, 2), Reward(1385, 1, 1), Reward(4580, 1, 5),
        Reward(1367, 1, 4), Reward(1313, 1, 4), Reward(1369, 1, 3), Reward(1315, 1, 3), Reward(1329, 1, 17),
        Reward(1299, 1, 10), Reward(1343, 1, 3), Reward(1357, 1, 10), Reward(1287, 1, 3), Reward(1430, 1, 9),
        Reward(1331, 1, 2), Reward(1083, 1, 16), Reward(1119, 1, 3), Reward(1165, 1, 14), Reward(1107, 1, 8),
        Reward(1077, 1, 3), Reward(1089, 1, 3), Reward(1195, 1, 9), Reward(1179, 1, 15), Reward(1159, 1, 8),
        Reward(1109, 1, 3), Reward(1071, 1, 22), Reward(1181, 1, 7), Reward(1197, 1, 3), Reward(1145, 1, 3),
        Reward(561, 10, 12), Reward(560, 10, 8), Reward(1519, 5, 16), Reward(1515, 5, 10), Reward(1641, 1, 9),
        Reward(1727, 1, 3),
        // A medium clue scroll.
        Reward(-1, 1, 2))

    /**
     * Whether [weaponId] hurts a juvinate.
     */
    fun isSilver(weaponId: Int?) = weaponId != null && weaponId in SILVER

    /**
     * Whether [npc] is a juvinate the rod is holding.
     */
    fun isHeld(npc: Npc) = (held[npc] ?: 0L) > world.currentTick && npc.state == EntityState.ACTIVE

    /**
     * The rod's special, Retainer: holds a juvinate with less than half its health, and uses a charge.
     */
    fun retain(plr: Player, victim: Npc?): Boolean {
        if (victim == null || victim.id !in IDS) {
            plr.sendMessage("This spell can only be used on vampyre juveniles or vampyre juvinates.")
            return false
        }
        if (victim.health * 2 >= victim.totalHealth) {
            plr.sendMessage("This spell only works against vampyres with less than half of their health remaining.")
            return false
        }
        plr.animation(Animation(CAST))
        plr.graphic(Graphic(CAST_GRAPHIC, 0, 0))
        LocalProjectile.followEntity(ctx).setSourceEntity(plr).setTargetEntity(victim).setId(HOLD_PROJECTILE)
            .setStartHeight(43).setEndHeight(31).setTicksToStart(20).setTicksToEnd(50).setInitialSlope(16)
            .build().display()
        victim.graphic(Graphic(HOLD_HIT, 100, 60))
        victim.combat.target = null
        victim.walking.clear()
        victim.lock(HOLD_TICKS)
        held[victim] = world.currentTick + HOLD_TICKS
        useCharge(plr)
        return true
    }

    private fun useCharge(plr: Player) {
        val rod = plr.equipment[Equipment.WEAPON] ?: return
        val index = RODS.indexOf(rod.id)
        if (index == -1) return
        val next = if (index == RODS.size - 1) ROD_DUST else RODS[index + 1]
        plr.equipment[Equipment.WEAPON] = Item(next)
        if (next == ROD_DUST) plr.sendMessage("Your Rod of Ivandis crumbles to dust.")
    }

    /**
     * Pours a dose of Guthix balance on a held juvinate: it dies, is cured, or flies into a rage.
     */
    fun pour(plr: Player, victim: Npc, balanceIndex: Int) {
        val balance = plr.inventory[balanceIndex] ?: return
        if (victim.state != EntityState.ACTIVE || !victim.isAlive) return
        if (!isHeld(victim)) {
            plr.sendMessage("The vampyre is moving too much for you to do that.")
            return
        }
        val dose = BALANCE.indexOf(balance.id)
        if (dose == -1) return
        plr.inventory[balanceIndex] = Item(if (dose == BALANCE.size - 1) VIAL else BALANCE[dose + 1])
        plr.face(victim.position)
        plr.animation(Animation(POUR))
        held.remove(victim)
        val roll = Random.nextInt(100)
        when {
            roll < 16 -> destroy(plr, victim)
            roll < 84 -> cure(plr, victim)
            else -> enrage(plr, victim)
        }
    }

    private fun destroy(plr: Player, victim: Npc) {
        victim.animation(Animation(CRUMBLE))
        victim.graphic(Graphic(DUST_GRAPHIC, 0, 0))
        plr.playSound(Sound.VAMPIRE_DEATH)
        plr.prayer.addExperience(DESTROY_XP)
        plr.sendMessage("The vampyre crumbles to dust.")
        defeated(plr, victim)
        world.scheduleOnce(3) {
            if (victim.state == EntityState.ACTIVE) {
                world.addItem(VAMPIRE_DUST, 1, victim.position, plr)
                world.removeNpc(victim)
            }
        }
    }

    private fun cure(plr: Player, victim: Npc) {
        victim.graphic(Graphic(SPIRIT_GRAPHIC, 0, 0))
        victim.animation(Animation(SPIRIT_FADE))
        defeated(plr, victim)
        world.scheduleOnce(2) {
            if (victim.state != EntityState.ACTIVE) return@scheduleOnce
            victim.unlock()
            victim.transform(HUMANS.random())
            victim.speak("Thank you! You've freed me from the curse!")
            reward(plr)
            world.scheduleOnce(4) { if (victim.state == EntityState.ACTIVE) world.removeNpc(victim) }
        }
    }

    private fun enrage(plr: Player, victim: Npc) {
        victim.unlock()
        victim.health = victim.totalHealth
        victim.speak("Grrrr!")
        plr.sendMessage("The Guthix balance only makes the vampyre angry!")
        if (plr.isAlive) victim.combat.attack(plr)
    }

    /**
     * Counts a destroyed or cured juvinate as beaten in the player's event.
     */
    private fun defeated(plr: Player, victim: Npc) {
        val monster = victim as? TrekMonster ?: return
        if (monster.beaten) return
        monster.beaten = true
        TrekEncounter.of(monster)?.killed(monster, plr)
    }

    private fun reward(plr: Player) {
        var roll = Random.nextInt(REWARDS.sumOf { it.weight })
        var reward = REWARDS.first { roll -= it.weight; roll < 0 }
        if (reward.id == -1 && TreasureTrails.hasTrail(plr)) reward = REWARDS[0]
        val item = if (reward.id == -1) {
            val clue = TrailClues.all.filter { it.tier == TrailTier.MEDIUM }.random()
            TreasureTrails.acquired(plr, clue.id)
            Item(clue.id)
        } else {
            val noted = itemDef(reward.id).notedId
            Item(if (noted.isPresent) noted.asInt else reward.id, reward.amount)
        }
        plr.giveItem(item)
    }
}
