package game.content.templeTrekking

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.dialogue.Expression

/**
 * What happens in an event room.
 *
 * @author TheLining
 */
abstract class TrekEncounter(val controller: TrekController) {

    companion object {

        /**
         * Creates the encounter for an event of [type].
         */
        fun create(type: TrekEventType, controller: TrekController): TrekEncounter {
            val level = controller.trek.route.ordinal
            return when (type) {
                TrekEventType.SNAKES -> Fight(controller, 3599 + level, sticky = true)
                TrekEventType.SNAILS -> Fight(controller, 3612 + level)
                TrekEventType.GHASTS -> Ghasts(controller, 3606 + level)
                TrekEventType.JUVINATES -> Fight(controller, 3576 + level, sticky = true)
                TrekEventType.SHADES -> Fight(controller, 3615 + level)
                TrekEventType.TENTACLES -> Tentacles(controller)
                TrekEventType.BOG -> TrekPuzzles.Bog(controller)
                TrekEventType.RIVER -> TrekPuzzles.River(controller)
                TrekEventType.BRIDGE -> TrekPuzzles.Bridge(controller)
                TrekEventType.ABIDOR_CRANK -> AbidorCrank(controller)
            }
        }

        /**
         * The encounter whose room holds [npc].
         */
        fun of(npc: Npc): TrekEncounter? = (npc as? TrekMonster)?.let { TrekEvents.controller(it.owner)?.encounter }
    }

    val player: Player get() = controller.player
    val trek: Trek get() = controller.trek

    /**
     * Whether the player may continue the trek.
     */
    abstract val complete: Boolean

    /**
     * Whether half of the enemies, rounded up, are beaten.
     */
    open val halfDefeated: Boolean get() = false

    /**
     * What the Continue-trek path says before the event is done.
     */
    open val unfinished = "You'd better deal with the danger here before you continue."

    /**
     * Whether the villager stays where it is instead of following, as it does until a puzzle is solved.
     */
    open val holdsVillager = false

    /**
     * Sets the event going once the player and villager are in the room.
     */
    open fun start() {}

    /**
     * Runs a tick of the event.
     */
    open fun process() {}

    /**
     * Called when [npc], one of the event's monsters, dies by [killer].
     */
    open fun killed(npc: TrekMonster, killer: Player?) {}

    /**
     * Pays the event's points as the player continues.
     */
    open fun completed() {
        if (controller.type.points > 0) trek.addPoints(controller.type.points)
    }

    /**
     * Stops the event's monsters as the player moves on.
     */
    open fun clear() {}

    /**
     * Spawns a monster for this event at a tile of the real room.
     */
    fun spawn(id: Int, real: Position, sticky: Boolean = false): TrekMonster {
        val npc = TrekMonster(player, id, controller.tile(real), sticky)
        world.npcs.add(npc)
        return npc
    }

    /**
     * The villager says [line] over its head.
     */
    fun villagerSays(line: String) {
        controller.follower?.speak(line)
    }

    /**
     * A fight with a pack of one kind of monster, as many as the villager's tier brings. The monsters give the player
     * a few seconds to get between them and the villager, then go for whichever is nearer.
     */
    open class Fight(controller: TrekController, val id: Int, val sticky: Boolean = false) : TrekEncounter(controller) {

        companion object {

            /**
             * Ticks the monsters wait before attacking (5 seconds).
             */
            const val WAIT_TICKS = 8
        }

        val monsters = ArrayList<TrekMonster>()
        var kills = 0
        private var wait = WAIT_TICKS

        override val complete get() = monsters.none { it.isAlive && it.state == EntityState.ACTIVE && !it.beaten }

        override val halfDefeated get() = kills >= (monsters.size + 1) / 2

        override val unfinished = "You can't continue the trek until you've dealt with these monsters."

        override fun start() {
            val count = trek.villager.tier.monsters
            for (index in 0 until count) {
                val tile = controller.room.spawns[index % controller.room.spawns.size]
                monsters += spawn(id, tile, sticky)
            }
            monsters.forEach { it.face(player.position) }
        }

        override fun process() {
            if (wait > 0) {
                wait--
                return
            }
            val villager = controller.follower
            for (monster in monsters) {
                if (!monster.isAlive || monster.state != EntityState.ACTIVE || monster.isLocked || monster.beaten) continue
                if (monster.combat.target != null) continue
                monster.combat.attack(target(monster, villager))
            }
        }

        /**
         * Who [monster] goes for: the nearer of the player and the villager.
         */
        open fun target(monster: TrekMonster, villager: TrekFollower?): io.luna.game.model.mob.Mob {
            if (villager == null || !villager.isAlive) return player
            val toPlayer = monster.position.computeLongestDistance(player.position)
            val toVillager = monster.position.computeLongestDistance(villager.position)
            return if (toVillager < toPlayer) villager else player
        }

        override fun killed(npc: TrekMonster, killer: Player?) {
            if (npc !in monsters) return
            kills++
            trek.addPoints(trek.route.killPoints)
        }

        override fun clear() {
            monsters.forEach { it.combat.target = null }
        }
    }

    /**
     * Ghasts: hidden ones go only for the villager, and a filled druid pouch used on one shows it and sets it on the
     * player.
     */
    class Ghasts(controller: TrekController, val hidden: Int) : Fight(controller, hidden) {

        companion object {
            const val DRUID_POUCH = 2958
            const val EMPTY_POUCH = 2957
            const val POUCH_PROJECTILE = 268
            const val POUCH_IMPACT = 269
        }

        val visible = hidden + 3

        override fun target(monster: TrekMonster, villager: TrekFollower?): io.luna.game.model.mob.Mob =
            if (monster.id == visible || villager == null || !villager.isAlive) player else villager

        /**
         * Spends a charge of the player's druid pouch to show [ghast] and draw it onto them.
         */
        fun reveal(plr: Player, ghast: TrekMonster) {
            if (ghast.id != hidden || ghast !in monsters || !ghast.isAlive) return
            plr.inventory.remove(Item(DRUID_POUCH))
            plr.sendMessage("The druid pouch makes the Ghast visible.")
            if (!plr.inventory.contains(DRUID_POUCH)) {
                plr.sendMessage("You have nothing left in your druid pouch!")
                plr.inventory.add(Item(EMPTY_POUCH))
            }
            plr.face(ghast.position)
            io.luna.game.model.LocalProjectile.followEntity(ctx).setSourceEntity(plr).setTargetEntity(ghast)
                .setId(POUCH_PROJECTILE).setStartHeight(43).setEndHeight(31).setTicksToStart(20).setTicksToEnd(50)
                .setInitialSlope(16).build().display()
            ghast.graphic(Graphic(POUCH_IMPACT, 124, 90))
            world.scheduleOnce(1) {
                if (ghast.state != EntityState.ACTIVE || ghast.id != hidden) return@scheduleOnce
                ghast.transform(visible)
                if (plr.isAlive) ghast.combat.attack(plr)
            }
        }
    }

    /**
     * The swamp beast on the hard route's boat: four tentacles rise around the boat one by one, then its head.
     */
    class Tentacles(controller: TrekController) : TrekEncounter(controller) {

        companion object {
            const val TENTACLE = 3618
            const val HEAD = 3619
            const val HEAD_RISING = 3620
            const val TENTACLE_RISING = 3621
            const val BUBBLES = 625
            const val BOAT = 13864

            /**
             * Ticks a rising part takes to come up.
             */
            const val RISE_TICKS = 4

            val TENTACLE_TILES = listOf(Position(2061, 5034), Position(2064, 5036), Position(2061, 5036),
                                        Position(2064, 5034))
            val HEAD_TILE = Position(2062, 5037)

            /**
             * The villager's lines as the beast comes up, by the tick each is said.
             */
            val LINES = mapOf(2 to "Wh...what are those bubbles?",
                              5 to "This is scary!",
                              8 to "Ahhh, that would be a tentacle...",
                              11 to "Ahhh, two tentacles!",
                              14 to "...three tentacles!",
                              17 to "I think I'm going to faint!",
                              20 to "Blimey! Look at that beast! You're gonna have to sort that thing out on your own!")
        }

        private var tick = 0
        private val parts = ArrayList<TrekMonster>()
        private var head: TrekMonster? = null

        override val complete get() = parts.size == 5 && parts.none { it.isAlive && it.state == EntityState.ACTIVE }

        override val unfinished = "You can't go on until the beast is dead."

        override fun start() {
            // The boat's options sit on a dummy on plane 2, over the boat the player stands in. The tiles aren't
            // bridged, so a click on it is never found; the dummy is put on the player's plane instead.
            val dummy = controller.tile(Position(2062, 5034, 2))
            val boat = world.locator.findObjectsOnTile(dummy) { it.id == BOAT }.firstOrNull() ?: return
            world.removeObject(boat)
            world.addObject(BOAT, Position(dummy.x, dummy.y, 0), boat.objectType, boat.direction)
        }

        override fun process() {
            tick++
            LINES[tick]?.let { villagerSays(it) }
            if (tick == 2) {
                for (tile in TENTACLE_TILES + HEAD_TILE) {
                    LocalGraphic(ctx, BUBBLES, 0, 0, controller.tile(tile), ChunkUpdatableView.globalView()).display()
                }
            }
            val rise = (tick - 8) / 3
            if (tick >= 8 && (tick - 8) % 3 == 0 && rise in 0..4) {
                if (rise < 4) {
                    parts += rise(TENTACLE_RISING, TENTACLE, TENTACLE_TILES[rise], Sound.TREK_TENTACLE_APPEAR)
                } else {
                    val beast = rise(HEAD_RISING, HEAD, HEAD_TILE, Sound.TREK_TENTACLE_HEAD_APPEAR)
                    parts += beast
                    head = beast
                }
            }
            regenerate()
            val villager = controller.follower
            for (part in parts) {
                if (!part.isAlive || part.state != EntityState.ACTIVE || part.isLocked ||
                    part.id == TENTACLE_RISING || part.id == HEAD_RISING) continue
                // The beast can't leave the water: each part strikes whoever stands right beside it on the boat.
                val reachable = listOfNotNull(player, villager).filter { it.isAlive && beside(part, it) }
                val target = part.combat.target
                if (target != null && target in reachable) continue
                if (reachable.isEmpty()) {
                    if (target != null) part.combat.target = null
                } else {
                    part.combat.attack(reachable.first())
                }
            }
        }

        private fun beside(part: TrekMonster, mob: io.luna.game.model.mob.Mob): Boolean {
            val dx = kotlin.math.abs(part.position.x - mob.position.x)
            val dy = kotlin.math.abs(part.position.y - mob.position.y)
            return dx + dy == 1
        }

        private fun rise(rising: Int, risen: Int, tile: Position, sound: Sound): TrekMonster {
            val part = spawn(rising, tile)
            player.playSound(sound)
            world.scheduleOnce(RISE_TICKS) {
                if (part.state == EntityState.ACTIVE && part.isAlive) part.transform(risen)
            }
            return part
        }

        /**
         * The head heals itself while any tentacle lives, once it's nearly dead.
         */
        private fun regenerate() {
            val beast = head ?: return
            if (!beast.isAlive || beast.health > beast.totalHealth / 10) return
            if (parts.any { it !== beast && it.isAlive && it.state == EntityState.ACTIVE }) {
                beast.health = beast.totalHealth
            }
        }

        override fun clear() {
            parts.forEach { it.combat.target = null }
        }
    }

    /**
     * Abidor Crank, the good samaritan, who heals and blesses the player and the villager.
     */
    class AbidorCrank(controller: TrekController) : TrekEncounter(controller) {

        companion object {
            const val ABIDOR = 3635
            const val ROTTEN_FOOD = 2959
            const val STEW = 2003
            const val HEAL_ANIMATION = 3745
            const val GLOW = 626
        }

        private var blessed = false
        private var abidor: Npc? = null

        override val complete = true

        override fun start() {
            val real = controller.room.villager
            val tile = controller.tile(Position(real.x + 1, real.y + 2))
            abidor = world.addNpc(Npc(ctx, ABIDOR, tile)).also {
                it.respawnTicks = -1
                it.face(player.position)
            }
        }

        /**
         * Talks to Abidor, who blesses the pair the first time.
         */
        fun talk(npc: Npc) {
            if (blessed) {
                player.newDialogue().player("Hello there...")
                    .npc(ABIDOR, "Ohhh, hello again... sorry my friend, but I have to get going.").open()
                return
            }
            player.newDialogue()
                .player("Hello there...")
                .npc(ABIDOR, Expression.HAPPY, "Ahhh, hello there my friend. You look like you could do with some " +
                    "assistance.")
                .player("We do?")
                .npc(ABIDOR, "Yes, of course... Here, prepare yourselves!")
                .then {
                    bless(npc)
                    it.newDialogue()
                        .npc(ABIDOR, "There you go... Right, I must be off now. Good luck with your travels.")
                        .open()
                }
                .open()
        }

        private fun bless(npc: Npc) {
            if (blessed) return
            blessed = true
            npc.animation(Animation(HEAL_ANIMATION))
            player.playSound(Sound.TREK_HEAL_CAST)
            player.graphic(Graphic(GLOW, 0, 0))
            player.hitpoints.level = maxOf(player.hitpoints.level, player.hitpoints.staticLevel + 15)
            player.defence.boost(player.defence.staticLevel / 10 + 3)
            val villager = controller.follower
            if (villager != null && villager.isAlive) {
                villager.graphic(Graphic(GLOW, 0, 0))
                villager.health = villager.totalHealth + 15
            }
            for ((index, item) in player.inventory.withIndex()) {
                if (item?.id == ROTTEN_FOOD) player.inventory[index] = Item(STEW)
            }
        }
    }
}
