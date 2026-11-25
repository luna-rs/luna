package game.content.mortMyre

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.mortMyre.MortMyre.DRUID_POUCH
import game.content.mortMyre.MortMyre.EMPTY_POUCH
import game.player.Sound
import game.skill.cooking.cookFood.Food
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatAction

/**
 * Mort Myre's ghasts. An invisible ghast hunts players nearby and swipes at them: most swipes miss, the rest rot a
 * piece of food, or hurt a player who carries none. A filled druid pouch spends a charge to reveal the ghast instead,
 * and the revealed ghast fights until it dies or is left alone, then fades back.
 *
 * @author TheLining
 */
object MortMyreGhasts {

    const val INVISIBLE = 1052
    const val VISIBLE = 1053

    /**
     * How near an invisible ghast notices a player.
     */
    private const val HUNT_RANGE = 5

    /**
     * The fewest ticks between two ghast attacks on the same player.
     */
    private const val ATTACK_DELAY = 26

    /**
     * The chance a swing lands instead of missing.
     */
    private val HIT_CHANCE = 3 of 10

    /**
     * How long a revealed ghast stays visible with nobody fighting it, and the longest it stays visible without a
     * target.
     */
    private const val IDLE_TICKS = 10
    private const val VISIBLE_TICKS = 500

    /**
     * Prayer XP for killing a ghast, and how near its killer must stand.
     */
    private const val KILL_XP = 30.0
    private const val KILL_RANGE = 3

    private const val ROTTEN_FOOD = 2959
    private const val INVISIBLE_ATTACK_ANIMATION = 1093
    private const val POUCH_PROJECTILE = 268
    private const val POUCH_IMPACT = 269

    /**
     * When a ghast last swung at this player.
     */
    private var Player.lastGhastAttack by Attr.long { Long.MIN_VALUE }

    /**
     * The player each invisible ghast is closing in on.
     */
    private val hunting = HashMap<Npc, Player>()

    /**
     * A revealed ghast's tick of becoming visible, and its last tick in a fight.
     */
    private class Reveal(val at: Long, var active: Long)

    /**
     * The revealed ghasts.
     */
    private val revealed = HashMap<Npc, Reveal>()

    /**
     * Moves every ghast on by a tick. Called once per tick.
     */
    fun process() {
        hunting.keys.removeIf { it.state != EntityState.ACTIVE || it.id != INVISIBLE }
        revealed.keys.removeIf { it.state != EntityState.ACTIVE || it.id != VISIBLE }
        for (ghast in world.npcs.byBaseNpcIds(setOf(INVISIBLE))) {
            if (ghast.state != EntityState.ACTIVE || !ghast.isAlive) continue
            when (ghast.id) {
                INVISIBLE -> hunt(ghast)
                VISIBLE -> fadeIfIdle(ghast)
            }
        }
    }

    /**
     * Closes in on a player near [ghast] and attacks them once it reaches them.
     */
    private fun hunt(ghast: Npc) {
        var target = hunting[ghast]
        if (target == null || !canAttack(ghast, target)) {
            target = world.locator.findPlayers(ghast, HUNT_RANGE) { canAttack(ghast, it) }.firstOrNull()
        }
        if (target == null) {
            hunting.remove(ghast)
            if (ghast.navigator.currentTarget is Player) ghast.navigator.cancel()
            return
        }
        hunting[ghast] = target
        if (nextTo(ghast, target)) {
            hunting.remove(ghast)
            ghast.navigator.cancel()
            attack(ghast, target)
        } else if (ghast.navigator.currentTarget !== target) {
            ghast.navigator.follow(target)
        }
    }

    /**
     * Whether [plr] stands on or next to any tile [ghast] covers, diagonals included, since a following ghast can
     * stop on a diagonal.
     */
    private fun nextTo(ghast: Npc, plr: Player): Boolean {
        val from = ghast.position
        val size = ghast.size() - 1
        val dx = maxOf(from.x - plr.position.x, plr.position.x - (from.x + size), 0)
        val dy = maxOf(from.y - plr.position.y, plr.position.y - (from.y + size), 0)
        return from.z == plr.position.z && maxOf(dx, dy) <= 1
    }

    /**
     * Whether [ghast] can go after [plr]: alive, nearby and not swung at by a ghast too recently.
     */
    private fun canAttack(ghast: Npc, plr: Player) =
        plr.state == EntityState.ACTIVE && plr.isAlive && !plr.isLocked &&
            plr.position.computeLongestDistance(ghast.position) <= HUNT_RANGE + 1 &&
            world.currentTick >= plr.lastGhastAttack + ATTACK_DELAY

    /**
     * Swipes at [plr]. A filled druid pouch reveals [ghast] instead of letting the swipe land.
     */
    private fun attack(ghast: Npc, plr: Player) {
        plr.lastGhastAttack = world.currentTick
        ghast.animation(Animation(INVISIBLE_ATTACK_ANIMATION))
        plr.playSound(Sound.GHAST_ATTACK)
        if (plr.inventory.contains(DRUID_POUCH)) {
            reveal(ghast, plr)
        } else if (rand(HIT_CHANCE)) {
            ghast.speak("Oouuaarrr!")
            if (!rotFood(plr)) {
                plr.sendMessage("A supernatural force draws energy from you.")
                plr.damage(rand(1, 3))
            }
        } else {
            ghast.speak("OOooooohhh")
            plr.sendMessage("An attacking Ghast just misses you.")
        }
    }

    /**
     * Turns a random piece of [plr]'s food rotten. Returns `false` if they carry none.
     */
    private fun rotFood(plr: Player): Boolean {
        val food = plr.inventory.withIndex().filter { (_, item) -> item != null && item.id in Food.COOKED_TO_FOOD }
        if (food.isEmpty()) return false
        plr.inventory[food.random().index] = Item(ROTTEN_FOOD)
        plr.playSound(Sound.FOOD_ROT)
        plr.sendMessage("You feel something attacking your backpack, and smell a terrible stench.")
        return true
    }

    /**
     * Spends a charge of [plr]'s druid pouch to make [ghast] visible, and sets it on them.
     */
    private fun reveal(ghast: Npc, plr: Player) {
        plr.sendMessage("The druid pouch makes the Ghast visible.")
        plr.inventory.remove(Item(DRUID_POUCH))
        if (!plr.inventory.contains(DRUID_POUCH)) {
            plr.sendMessage("You have nothing left in your druid pouch!")
            plr.inventory.add(Item(EMPTY_POUCH))
        }
        LocalProjectile.followEntity(ctx).setSourceEntity(plr).setTargetEntity(ghast).setId(POUCH_PROJECTILE)
            .setStartHeight(43).setEndHeight(31).setTicksToStart(20).setTicksToEnd(50).setInitialSlope(16)
            .build().display()
        ghast.graphic(Graphic(POUCH_IMPACT, 124, 90))
        world.scheduleOnce(1) {
            if (ghast.state != EntityState.ACTIVE || ghast.id != INVISIBLE) return@scheduleOnce
            ghast.transform(VISIBLE)
            revealed[ghast] = Reveal(world.currentTick, world.currentTick)
            if (plr.state == EntityState.ACTIVE && plr.isAlive) ghast.combat.attack(plr)
        }
    }

    /**
     * Fades [ghast] back out of sight once nobody has fought it for a while, or once it has been visible too long
     * without a target.
     */
    private fun fadeIfIdle(ghast: Npc) {
        val now = world.currentTick
        val reveal = revealed.getOrPut(ghast) { Reveal(now, now) }
        if (ghast.isLocked || ghast.combat.target != null || ghast.actions.contains(CombatAction::class.java) ||
            ghast.combat.inCombat()) {
            reveal.active = now
        }
        val idle = now - reveal.active >= IDLE_TICKS
        val tooLong = now - reveal.at >= VISIBLE_TICKS && ghast.combat.target == null
        if (!idle && !tooLong) return
        revealed.remove(ghast)
        ghast.navigator.cancel()
        ghast.walking.clear()
        ghast.interact(null)
        ghast.combat.damageStack.clear()
        ghast.resetTransform()
    }

    /**
     * Gives the player who killed a revealed ghast its Prayer XP, if they stood close enough.
     */
    fun onKill(ghast: Npc, killer: Player?) {
        if (killer == null || killer.state != EntityState.ACTIVE) return
        if (killer.position.computeLongestDistance(ghast.position) <= KILL_RANGE) {
            killer.prayer.addExperience(KILL_XP)
        }
    }
}
