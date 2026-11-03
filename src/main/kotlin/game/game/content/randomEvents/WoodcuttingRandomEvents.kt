/*
 * Adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 * MIT License
 *
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package game.content.randomEvents

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.interact.InteractionActionListener
import io.luna.game.model.path.Pathfinders
import io.luna.game.model.path.PathResultType
import io.luna.game.model.path.route.LineOfSight
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * World-owned sixty-tick Ents and recoverable axe-head hazards from the source woodcutting pool.
 * Only explicitly mapped source trees can become Ents. The temporary collision objects and tree
 * restoration keep their exact lifetime independently of the triggering player's login state.
 *
 * @author lare96
 */
object WoodcuttingRandomEvents {

    /**
     * Cache item identities and Bob's source repair prices. Dragon metadata supplies a head's fixed
     * axe and a broken-axe cost, so it follows the same recovery path as the other axe tiers.
     *
     * @author lare96
     */
    enum class Parts(val axe: Axe, val broken: Int, val head: Int, val repairCost: Int) {
        BRONZE(Axe.BRONZE, 494, 508, 0),
        IRON(Axe.IRON, 496, 510, 0),
        STEEL(Axe.STEEL, 498, 512, 0),
        BLACK(Axe.BLACK, 500, 514, 10),
        MITHRIL(Axe.MITHRIL, 502, 516, 18),
        ADAMANT(Axe.ADAMANT, 504, 518, 43),
        RUNE(Axe.RUNE, 506, 520, 427),
        DRAGON(Axe.DRAGON, 6741, 6743, 1800)
    }

    /**
     * The common handle used by the source detachment script, including its dragon axe branch.
     */
    const val HANDLE = 492

    /**
     * Tree object to Ent NPC mappings explicitly present in source tree configuration.
     */
    internal val TREE_ENTS = mapOf(
        2023 to 1746, 1384 to 1745, 2289 to 1749, 4060 to 1750,
        1306 to 1734, 9034 to 2534, 1307 to 1735, 1276 to 1719,
        1278 to 1721, 1282 to 1722, 1285 to 1723, 1286 to 1724,
        1289 to 1727, 1290 to 1728, 1291 to 1729, 1315 to 1741,
        1316 to 1742, 1330 to 1744, 1365 to 1725, 1383 to 1731,
        3879 to 1747, 3883 to 1748, 1281 to 1739, 9036 to 2535,
        1308 to 1736, 5551 to 1737, 5553 to 1738, 1309 to 1740, 4818 to 1791
    )

    /**
     * The source counter belongs to the player and carries between Ents until an axe is damaged.
     */
    private var Player.entAttempts by Attr.int().persist("woodcutting_ent_attempts")

    /**
     * Retains the source's saved counter even during login sessions that never meet an Ent.
     */
    fun login(player: Player) {
        player.entAttempts
    }

    /**
     * Exact tree and collision ownership for one transformation.
     *
     * @author lare96
     */
    private class Ent(val original: GameObject, val blockers: List<GameObject>)

    private val ents = mutableMapOf<Npc, Ent>()

    /**
     * Replaces one live public tree with the corresponding stationary Ent. Private object views
     * cannot safely become public NPCs, so their Ent selection is consumed without transformation.
     */
    fun startEnt(player: Player, tree: GameObject): Boolean {
        val npcId = TREE_ENTS[tree.id] ?: return false
        if (tree.state != EntityState.ACTIVE || !tree.view.isGlobal ||
            tree.id !in TreeStump.TREE_ID_MAP) return false
        val size = tree.def().sizeX
        if (size !in 1..3 || tree.def().sizeY != size) return false
        val world = player.world
        val npc = Npc(player.context, npcId, tree.position).also {
            it.respawnTicks = -1
            it.isWandering = false
            it.walking.isLocked = true
        }
        if (!world.npcs.add(npc)) return false
        if (!world.removeObject(tree)) {
            world.npcs.remove(npc)
            return false
        }
        val blockers = blockerLayout(size).map { (id, x, y) ->
            GameObject.createDynamic(player.context, id, tree.position.translate(x, y), ObjectType.DEFAULT,
                                     ObjectDirection.WEST, tree.view)
        }
        val added = mutableListOf<GameObject>()
        for (blocker in blockers) {
            if (!world.addObject(blocker)) {
                added.forEach { world.removeObject(it) }
                world.npcs.remove(npc)
                restoreTree(tree)
                return false
            }
            added += blocker
        }
        ents[npc] = Ent(tree, blockers)
        world.scheduleOnce(60) { finishEnt(npc) }
        // Source p_opnpc(1) continues the click on the next tick, after ordinary chopping stops.
        val focus = player.interactingWith
        world.scheduleOnce(1) {
            if (ents.containsKey(npc) && player.interactingWith === focus && player.walking.isEmpty &&
                RandomEvents.canAct(player)) continueAtEnt(player, npc)
        }
        return true
    }

    /**
     * Reissues the original tree click as an ordinary NPC interaction. Some source Ent models
     * have a larger footprint than their original tree, requiring a short route to the new edge.
     * Routes are bounded to this tree's perimeter and pass the native NPC reach check before use.
     */
    internal fun continueAtEnt(player: Player, npc: Npc) {
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.isLocked ||
            npc.state != EntityState.ACTIVE || player.position.z != npc.position.z) return
        val world = player.world
        val policy = InteractionPolicy.STANDARD_SIZE
        val event = NpcFirstClickEvent(player, npc)
        if (!player.controllers.checkEvent(event)) return
        if (!world.collisionManager.reached(player, npc, policy)) {
            val origin = npc.position
            val size = npc.size()
            val stands = (origin.x - 1..origin.x + size).flatMap { x ->
                (origin.y - 1..origin.y + size).map { y -> Position(x, y, origin.z) }
            }.filter { world.collisionManager.reached(it, npc, policy) }
                .sortedBy { it.computeLongestDistance(player.position) }
            val finder = Pathfinders.forPlayer(player)
            val route = stands.asSequence().map { finder.find(player.position, it) }
                .firstOrNull { it.type == PathResultType.COMPLETE || it.type == PathResultType.EMPTY }?.path ?: return
            player.navigator.cancel()
            player.walking.replacePath(route)
        }
        val listener = InteractionActionListener(policy) {
            if (ents.containsKey(npc)) chopEnt(player, npc)
        }
        player.submitAction(InteractionAction(player, mutableListOf(listener), npc, event))
    }

    /**
     * Source collision masks for one-, two- and three-tile tree footprints.
     */
    internal fun blockerLayout(size: Int): List<Triple<Int, Int, Int>> = when (size) {
        1 -> listOf(Triple(83, 0, 0))
        2 -> listOf(Triple(2639, 0, 0))
        3 -> listOf(Triple(83, 2, 0), Triple(83, 0, 2), Triple(2639, 1, 1), Triple(2639, 0, 0))
        else -> emptyList()
    }

    /**
     * Ends this exact hazard. A foreign replacement of any blocker cancels tree restoration,
     * preventing an old expiry callback from overwriting newer world content.
     */
    internal fun finishEnt(npc: Npc) {
        val ent = ents.remove(npc) ?: return
        val world = npc.world
        val ownsAll = ent.blockers.all { it.state == EntityState.ACTIVE }
        ent.blockers.filter { it.state == EntityState.ACTIVE }.forEach { world.removeObject(it) }
        if (npc.state == EntityState.ACTIVE) world.npcs.remove(npc)
        if (ownsAll && world.objects.findAll(ent.original.position).noneMatch { ent.original.replaces(it) }) {
            restoreTree(ent.original)
        }
    }

    /**
     * Creates a fresh live object while retaining the original placement, type, orientation and view.
     */
    private fun restoreTree(tree: GameObject) {
        tree.world.addObject(GameObject.createDynamic(tree.context, tree.id, tree.position,
                                                       tree.objectType, tree.direction, tree.view))
    }

    /**
     * Starts cutting an owned live Ent. Every player can encounter the same transformed tree;
     * chopping it awards no logs or XP and the seventh attempted cut damages the current axe.
     */
    fun chopEnt(player: Player, npc: Npc) {
        val ent = ents[npc] ?: return
        val tree = TreeStump.TREE_ID_MAP[ent.original.id]?.tree ?: return
        if (!usable(player, npc, tree.level)) return
        player.sendMessage("You swing your axe at the tree.")
        player.submitAction(object : Action<Player>(player, ActionType.WEAK, false, 3) {
            override fun onSubmit() {
                Axe.computeAxeType(mob)?.let { mob.animation(it.animation) }
                mob.playSound(Sound.CUT_TREE_2)
            }

            override fun run(): Boolean {
                if (ents[npc] !== ent || !usable(mob, npc, tree.level)) return true
                val axe = Axe.computeAxeType(mob) ?: return true
                mob.animation(axe.animation)
                mob.playSound(Sound.CUT_TREE_2)
                if (++mob.entAttempts < 7) return false
                mob.entAttempts = 0
                val parts = Parts.entries.first { it.axe == axe }
                if (replaceAxe(mob, axe, parts.broken)) {
                    mob.sendMessage("That's not a tree, it's an Ent!")
                    mob.sendMessage("The Ent damages your axe!")
                }
                return true
            }

            override fun onFinished() {
                mob.animation(Animation.CANCEL)
            }
        })
    }

    /**
     * Rechecks the action's live target, player, tree level and space before a hazardous cut.
     */
    private fun usable(player: Player, npc: Npc, level: Int): Boolean {
        if (player.state != EntityState.ACTIVE || !player.isAlive || npc.state != EntityState.ACTIVE ||
            player.isLocked || player.position.z != npc.position.z ||
            !player.world.collisionManager.reached(player, npc, InteractionPolicy.STANDARD_SIZE)) return false
        if (player.woodcutting.level < level) {
            player.sendMessage("You need a Woodcutting level of $level to chop down this tree.")
            return false
        }
        if (player.inventory.isFull) {
            player.sendMessage("Your inventory is too full to hold any more logs.")
            return false
        }
        if (Axe.computeAxeType(player) == null) {
            Axe.sendNoAxeMessages(player)
            return false
        }
        return true
    }

    /**
     * Detaches the exact current axe only after finding a reachable landing tile. A worn axe is
     * replaced in its weapon slot, so a full inventory cannot lose the handle.
     */
    fun loseHead(player: Player, axe: Axe): Boolean {
        val parts = Parts.entries.first { it.axe == axe }
        val world = player.world
        val position = landingTile(player)
        if (!replaceAxe(player, axe, HANDLE)) return false
        player.animation(Animation(644))
        player.sendMessage("You swing your axe at the tree.")
        val from = player.position
        val distance = maxOf(kotlin.math.abs(from.x - position.x), kotlin.math.abs(from.y - position.y))
        val cycles = 12 + distance * 10
        if (distance > 0) {
            LocalProjectile.followPath(player.context).setSourcePosition(from).setTargetPosition(position)
                .setId(43).setStartHeight(35).setEndHeight(0).setTiming(14, cycles)
                .setInitialSlope(16).setLaunchOffset(64).build().display()
        }
        world.scheduleOnce(maxOf(1, cycles / 60)) {
            val head = world.addItem(parts.head, 1, position, player)
            // Normal tradeable loot reveals at tick100; this source drop expires at tick200.
            world.scheduleOnce(200) { world.items.expire(head) }
        }
        return true
    }

    /**
     * Searches the source one-to-seven-tile range and requires an unobstructed walking ray.
     */
    private fun landingTile(player: Player): Position {
        val world = player.world
        val positions = (-7..7).flatMap { x -> (-7..7).map { y -> player.position.translate(x, y) } }
            .filter { it != player.position && player.position.isWithinDistance(it, 7) }
        val candidates = positions.asSequence().filter {
            !world.collisionManager.isBlocked(it, false) &&
                LineOfSight.hasLineOfWalk(world.collisionManager.view(false), it.z,
                                           player.position.x, player.position.y, it.x, it.y, 1, 1, 1, 1, 0)
        }.take(100).toList()
        return candidates.randomOrNull() ?: player.position
    }

    /**
     * Replaces one actual tool, preferring the worn copy as in the source script. The inventory
     * branch retains its existing slot, including when no free slots are available.
     */
    internal fun replaceAxe(player: Player, axe: Axe, replacement: Int): Boolean {
        if (player.equipment[Equipment.WEAPON]?.id == axe.id) {
            player.equipment[Equipment.WEAPON] = Item(replacement)
            return true
        }
        val slot = player.inventory.computeIndexForId(axe.id)
        if (slot == -1) return false
        player.inventory[slot] = Item(replacement)
        return true
    }

    /**
     * Repairs one recovered head and handle in place, revalidating both current inventory slots.
     * Repeated or stale item-use callbacks cannot create another axe.
     */
    fun reattach(player: Player, headId: Int): Boolean {
        val parts = Parts.entries.firstOrNull { it.head == headId } ?: return false
        val inventory = player.inventory
        val handleSlot = inventory.computeIndexForId(HANDLE)
        val headSlot = inventory.computeIndexForId(headId)
        if (handleSlot == -1 || headSlot == -1) return false
        inventory.startBulkUpdate()
        try {
            inventory[headSlot] = null
            inventory[handleSlot] = Item(parts.axe.id)
        } finally {
            inventory.finishBulkUpdate()
        }
        player.playSound(Sound.ATTACH_HANDLE)
        player.sendMessage("You carefully attach the two pieces together.")
        return true
    }

    /**
     * Confirms and then revalidates Bob's repair transaction instead of charging from a stale offer.
     */
    fun offerRepair(player: Player, brokenId: Int) {
        val parts = Parts.entries.firstOrNull { it.broken == brokenId } ?: return
        val price = if (parts.repairCost == 0) "free" else "${parts.repairCost}gp"
        player.newDialogue().npc(519, "Quite badly damaged, but easy to repair.",
                                 "Would you like me to repair it for $price?")
            .options("Yes, please.", { repair(it, parts) }, "No, thank you.", { it.resetDialogues() }).open()
    }

    /**
     * Changes only the one broken axe still present when the player accepts the repair price.
     */
    internal fun repair(player: Player, parts: Parts): Boolean {
        player.resetDialogues()
        val inventory = player.inventory
        val slot = inventory.computeIndexForId(parts.broken)
        if (slot == -1) return false
        if (parts.repairCost > 0 && !inventory.contains(Item(995, parts.repairCost))) {
            player.sendMessage("I'll need ${parts.repairCost}gp to repair that.")
            return false
        }
        inventory.startBulkUpdate()
        try {
            if (parts.repairCost > 0) inventory.remove(Item(995, parts.repairCost))
            inventory[slot] = Item(parts.axe.id)
        } finally {
            inventory.finishBulkUpdate()
        }
        player.sendMessage(if (parts.repairCost == 0) "Bob fixes your axe for free!"
                           else "Bob fixes your axe for ${parts.repairCost}gp.")
        return true
    }
}
