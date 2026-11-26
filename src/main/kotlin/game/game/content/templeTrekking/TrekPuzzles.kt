package game.content.templeTrekking

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.agility.AgilityAction
import game.skill.agility.AgilitySound
import game.skill.agility.AgilityStep
import game.skill.agility.AgilityWalk
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import kotlin.random.Random

/**
 * The puzzle events: the bog, the river and the broken bridge.
 *
 * @author TheLining
 */
object TrekPuzzles {

    const val BRANCH = 7773
    const val LONG_VINE = 7777
    const val SHORT_VINE = 7778
    const val KNIFE = 946

    const val BOG = 13839
    const val SPINY_BUSH = 13840
    const val BIG_BRANCH = 13845
    const val VINE_ON_BRANCH = 13846
    const val BACKPACK = 13872
    const val BROKEN_BRIDGE = 13834
    const val FIXED_BRIDGE = 13837
    const val ZOMBIE = 3622
    const val BRONZE_AXE = 1351

    /**
     * The small swamp tree as its vines are cut, from three vines to none.
     */
    val VINE_TREES = listOf(13847, 13849, 13848, 13850)

    /**
     * The logs that mend the bridge.
     */
    val LOGS = listOf(1511, 1521, 1519, 1517, 1515, 1513)

    /**
     * Within reach of a bog tile without walking onto the bog.
     */
    val NEXT_TO = InteractionPolicy(InteractionType.DISTANCE, 1)

    private const val PROD = 3734
    private const val SINK = 3735
    private const val LASSO = 3739
    private const val MAKE_ROPE = 3740
    private const val REPAIR = 3741
    private const val SWING = 751

    /**
     * The chance a branch sticks in the bog and is lost.
     */
    private const val STUCK_CHANCE = 5

    /**
     * Takes the puzzle items off a player leaving an event.
     */
    fun clearItems(plr: Player) {
        for (id in listOf(BRANCH, SHORT_VINE, LONG_VINE)) {
            while (plr.inventory.contains(id)) plr.inventory.remove(Item(id))
        }
    }

    /**
     * The puzzle the player is in, if it's of type [T].
     */
    inline fun <reified T : TrekEncounter> puzzle(plr: Player): T? = TrekEvents.controller(plr)?.encounter as? T

    /**
     * A puzzle: the villager won't go on until the player finds a way across, then follows.
     */
    abstract class Puzzle(controller: TrekController) : TrekEncounter(controller) {

        /**
         * Set once the player is across.
         */
        var crossed = false
            private set

        override val complete get() = crossed

        override val unfinished = "You need to find a way across first."

        override val holdsVillager get() = !crossed

        /**
         * The villager's lines once the player is across, and where it lands.
         */
        abstract val doneLines: List<String>
        abstract val villagerLanding: Position

        /**
         * The villager's lines as the puzzle starts.
         */
        abstract val startLine: String
        abstract val reply: String

        override fun start() {
            controller.follower?.navigator?.cancel()
            player.newDialogue()
                .npc(trek.villager.npc, startLine)
                .player(reply)
                .open()
        }

        /**
         * The player is across: the villager comes over too.
         */
        fun across() {
            if (crossed) return
            crossed = true
            val villager = controller.follower ?: return
            villager.move(controller.tile(villagerLanding))
            villager.navigator.follow(player)
            if (doneLines.isNotEmpty()) {
                val dialogue = player.newDialogue()
                doneLines.forEach { dialogue.npc(trek.villager.npc, it) }
                dialogue.open()
            }
        }

        /**
         * The tile of the real room that [tile] of the copy stands for.
         */
        fun real(tile: Position): Position = world.dynamicMapSpacePool.getRealPosition(tile) ?: tile
    }

    /**
     * The bog: a branch tells firm ground from soft, and only the firm ground holds.
     */
    class Bog(controller: TrekController) : Puzzle(controller) {

        companion object {
            val WEST = 2055
            val EAST = 2064
            val SOUTH = 5005
            val NORTH = 5009
            val SHORE = Position(2060, 5004)
        }

        /**
         * The firm tile's x in each row of the bog, from south to north.
         */
        private val firm = IntArray(NORTH - SOUTH + 1)

        override val startLine = "Well that's torn it... Looks like we can't get over this bog. We'll have to go " +
            "back to Burgh de Rott."
        override val reply = "Oh, I'm sure we can find a way!"
        override val doneLines = listOf("Hey...that's great...you did a great job...I'll follow you.",
                                        "Whahay...that was great!", "But we really should be heading off now.")
        override val villagerLanding = Position(2059, 5011)

        override fun start() {
            var x = Random.nextInt(WEST, EAST + 1)
            for (row in firm.indices) {
                firm[row] = x
                x = (x + Random.nextInt(-1, 2)).coerceIn(WEST, EAST)
            }
            for (y in SOUTH..NORTH) {
                for (bx in WEST..EAST) {
                    world.addObject(BOG, controller.tile(Position(bx, y)), ObjectType.GROUND_DECORATION,
                                    ObjectDirection.WEST)
                }
            }
            super.start()
        }

        override fun process() {
            if (!crossed && real(player.position).y > NORTH && !player.isLocked) across()
        }

        private fun isFirm(tile: Position) = tile.y in SOUTH..NORTH && firm[tile.y - SOUTH] == tile.x

        /**
         * Cuts branches from the spiny bush with a blade.
         */
        fun cutBranches(plr: Player) {
            val space = plr.inventory.computeRemainingSize()
            if (space == 0) {
                plr.sendMessage("You don't have enough space in your inventory.")
                return
            }
            plr.animation(Animation(PROD))
            plr.playSound(Sound.TREK_CUTTING)
            plr.inventory.add(Item(BRANCH, minOf(3, space)))
            plr.sendMessage("You cut some branches from the spiny bush.")
        }

        /**
         * Tests a bog tile with a branch: firm, soft, or the branch is lost in it.
         */
        fun prod(plr: Player, bog: GameObject, index: Int) {
            val tile = real(bog.position)
            if (plr.position.computeLongestDistance(bog.position) > 1) {
                plr.sendMessage("You can't reach that part of the bog from here.")
                return
            }
            plr.face(bog.position)
            plr.animation(Animation(PROD))
            if (Random.nextInt(STUCK_CHANCE) == 0) {
                plr.inventory.remove(index, Item(BRANCH))
                plr.playSound(Sound.TREK_PROD_SUCK)
                plr.sendMessage("Your branch gets stuck in the bog and you lose it.")
            } else if (isFirm(tile)) {
                plr.playSound(Sound.TREK_PROD_FIRM)
                plr.sendMessage("The ground seems firm.")
            } else {
                plr.playSound(Sound.TREK_PROD_SOFT)
                plr.sendMessage("Erk, this ground feels quite soft.")
            }
        }

        /**
         * Steps onto a bog tile. Soft ground swallows the player and spits them back out on the shore.
         */
        fun standOn(plr: Player, bog: GameObject) {
            val tile = real(bog.position)
            if (plr.position.computeLongestDistance(bog.position) != 1 || plr.isLocked) {
                plr.sendMessage("You can't reach that part of the bog from here.")
                return
            }
            val step = AgilityStep(bog.position, 1, walkingAnimation = AgilityWalk.NORMAL)
            if (isFirm(tile)) {
                plr.submitAction(AgilityAction(plr, listOf(step), null, valid = { trek.active }))
                return
            }
            val shore = controller.tile(SHORE)
            val sink = AgilityStep(bog.position, 3, animation = SINK,
                                   sounds = listOf(AgilitySound(Sound.TREK_BOG_SUCK)),
                                   message = "You sink into the bog!")
            val out = AgilityStep(shore, 1, jump = true, sounds = listOf(AgilitySound(Sound.TREK_BOG_LEAVE)))
            plr.submitAction(AgilityAction(plr, listOf(step, sink, out), null, valid = { trek.active }))
        }
    }

    /**
     * The river: three short vines from the small tree, twisted into a long one and thrown over the big tree's
     * branch, swing the player across.
     */
    class River(controller: TrekController) : Puzzle(controller) {

        companion object {
            val BACKPACK_TILE = Position(2088, 5012)
            val SWING_FROM = Position(2093, 5013)
            val LANDING = Position(2093, 5018)
        }

        // The game reuses the bog's line here.
        override val startLine = "Well that's torn it... Looks like we can't get over this bog. We'll have to go " +
            "back to Burgh de Rott."
        override val reply = "Oh, I'm sure we can find a way through!"
        override val doneLines = listOf("That's amazing! I'll have to do the same.",
                                        "Whahay! That was great! But we really should be heading off now.")
        override val villagerLanding = Position(2092, 5018)

        override fun start() {
            world.addObject(BACKPACK, controller.tile(BACKPACK_TILE), ObjectType.DEFAULT, ObjectDirection.WEST)
            super.start()
        }

        /**
         * Searches the old backpack, which holds a knife.
         */
        fun search(plr: Player) {
            if (plr.inventory.contains(KNIFE)) {
                plr.sendMessage("You search the backpack but find nothing else of use.")
            } else if (plr.inventory.add(Item(KNIFE))) {
                plr.sendMessage("You search the backpack and find a knife.")
            } else {
                plr.sendMessage("You find a knife in the backpack, but you have no room to take it.")
            }
        }

        /**
         * Cuts a vine from the small swamp tree.
         */
        fun cutVine(plr: Player, tree: GameObject) {
            val stage = VINE_TREES.indexOf(tree.id)
            if (stage !in 0..2) {
                plr.sendMessage("There are no vines left on this tree.")
                return
            }
            if (!plr.inventory.add(Item(SHORT_VINE))) {
                plr.sendMessage("You don't have enough space in your inventory.")
                return
            }
            plr.playSound(Sound.TREK_CUTTING)
            plr.sendMessage("You cut a vine from the tree.")
            world.removeObject(tree)
            world.addObject(VINE_TREES[stage + 1], tree.position, tree.objectType, tree.direction)
        }

        /**
         * Twists three short vines into a long one.
         */
        fun twist(plr: Player) {
            val vines = plr.inventory.computeAmountForId(SHORT_VINE)
            // Using a vine on a vine reports both ways round; the second finds them already twisted.
            if (vines == 0 && plr.inventory.contains(LONG_VINE)) return
            if (vines < 3) {
                plr.sendMessage("You need three short vines to make a vine long enough.")
                return
            }
            repeat(3) { plr.inventory.remove(Item(SHORT_VINE)) }
            plr.inventory.add(Item(LONG_VINE))
            plr.animation(Animation(MAKE_ROPE))
            plr.playSound(Sound.TREK_MAKE_ROPE)
            plr.sendMessage("You twist the vines together to make a long vine.")
        }

        /**
         * Throws the long vine over the big tree's branch.
         */
        fun lasso(plr: Player, branch: GameObject) {
            if (real(branch.position) != Position(2093, 5014)) {
                plr.sendMessage("You can't reach that branch from here.")
                return
            }
            plr.inventory.remove(Item(LONG_VINE))
            plr.face(branch.position)
            plr.animation(Animation(LASSO))
            plr.playSound(Sound.TREK_LASOO)
            plr.sendMessage("You throw the vine over the branch.")
            world.removeObject(branch)
            world.addObject(VINE_ON_BRANCH, branch.position, branch.objectType, branch.direction)
        }

        /**
         * Swings across the river on the vine.
         */
        fun swing(plr: Player) {
            if (plr.isLocked) return
            val from = controller.tile(SWING_FROM)
            val to = controller.tile(LANDING)
            val swing = AgilityStep(to, 2, animation = SWING, sounds = listOf(AgilitySound(Sound.SWING_ACROSS)),
                                    begin = { plr.face(to) })
            val steps = if (plr.position == from) listOf(swing) else
                listOf(AgilityStep(from, from.computeLongestDistance(plr.position), walkingAnimation = AgilityWalk.NORMAL),
                       swing)
            val action = AgilityAction(plr, steps, null, valid = { trek.active })
            action.onSuccess = { across() }
            plr.submitAction(action)
        }
    }

    /**
     * The broken bridge: the zombie carries a bronze axe, the dead trees give logs, and three logs placed with an
     * axe in hand mend the gap.
     */
    class Bridge(controller: TrekController) : Puzzle(controller) {

        companion object {
            val WEST_END = Position(2089, 5039)
            val EAST_END = Position(2091, 5039)
        }

        override val startLine = "Blimey! Looks like we're going to have to go back to Burgh de Rott. We can't get " +
            "over that bridge!"
        override val reply = "Oh, I'm sure we can find a way!"
        override val doneLines = emptyList<String>()
        override val villagerLanding = Position(2092, 5040)

        private var zombie: TrekMonster? = null
        private var wait = TrekEncounter.Fight.WAIT_TICKS

        override fun start() {
            zombie = spawn(ZOMBIE, controller.room.spawns[0])
            super.start()
        }

        override fun process() {
            val undead = zombie ?: return
            if (wait > 0) {
                wait--
            } else if (undead.isAlive && undead.state == EntityState.ACTIVE && undead.combat.target == null) {
                undead.combat.attack(player)
            }
        }

        override fun killed(npc: TrekMonster, killer: Player?) {
            if (npc === zombie) trek.addPoints(trek.route.killPoints)
        }

        /**
         * Places a log in the gap, with an axe in hand.
         */
        fun mend(plr: Player, bridge: GameObject, index: Int) {
            if (bridge.id !in BROKEN_BRIDGE until FIXED_BRIDGE) return
            if (!wieldingAxe(plr)) {
                plr.sendMessage("You need to be wielding an axe to mend the bridge.")
                return
            }
            plr.inventory.remove(index, plr.inventory[index])
            plr.face(bridge.position)
            plr.animation(Animation(REPAIR))
            plr.playSound(Sound.TREK_BRIDGE_PLACELOG)
            plr.playSound(Sound.TREK_REPAIR_BRIDGE, 2)
            world.removeObject(bridge)
            world.addObject(bridge.id + 1, bridge.position, bridge.objectType, bridge.direction)
            plr.sendMessage(if (bridge.id + 1 == FIXED_BRIDGE) "You mend the bridge." else "You fix part of the bridge.")
        }

        /**
         * Walks over the mended bridge.
         */
        fun walkAcross(plr: Player) {
            if (plr.isLocked || crossed) return
            val from = controller.tile(WEST_END)
            val to = controller.tile(EAST_END)
            val steps = ArrayList<AgilityStep>()
            if (plr.position != from) {
                steps += AgilityStep(from, from.computeLongestDistance(plr.position).coerceAtLeast(1),
                                     walkingAnimation = AgilityWalk.NORMAL)
            }
            steps += AgilityStep(to, 2, walkingAnimation = AgilityWalk.NORMAL)
            val action = AgilityAction(plr, steps, null, valid = { trek.active })
            action.onSuccess = { across() }
            plr.submitAction(action)
        }

        private fun wieldingAxe(plr: Player): Boolean {
            val weapon = plr.equipment[io.luna.game.model.item.Equipment.WEAPON] ?: return false
            val name = weapon.itemDef.name.lowercase()
            return name.endsWith(" axe") || name == "axe" || name.endsWith("battleaxe")
        }
    }
}
