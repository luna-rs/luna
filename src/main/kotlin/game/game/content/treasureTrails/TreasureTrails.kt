package game.content.treasureTrails

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.treasureTrails.EasyClues.Kind
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetItemsMessageWriter
import kotlin.random.Random

/**
 * Treasure Trails, progressed synchronously by native item and world interactions.
 * Progress follows the current scroll or casket through banking and logout.
 *
 * @author lare96
 */
object TreasureTrails {
    var Player.trailItem by Attr.int().persist("treasure_trail_item")
    var Player.trailSteps by Attr.int().persist("treasure_trail_steps")
    var Player.trailRevision by Attr.int()

    fun hasTrail(player: Player): Boolean = EasyClues.trailItems.any {
        player.inventory.contains(it) || player.bank.contains(it)
    }

    fun canAcquire(player: Player, id: Int): Boolean {
        val auxiliary = TrailClues.byAuxiliary[id]
        if (auxiliary != null) {
            val sameKind = TrailClues.byAuxiliary.values.filter { it.puzzle == auxiliary.puzzle }
            if (sameKind.any { player.inventory.contains(it.auxiliary) || player.bank.contains(it.auxiliary) }) {
                player.sendMessage("You already have a ${if (auxiliary.puzzle) "puzzle box" else "challenge scroll"}.")
                return false
            }
        }
        if (id !in EasyClues.trailItems || !hasTrail(player)) return true
        player.sendMessage("You already have a clue scroll or casket in your inventory or bank.")
        return false
    }

    fun acquired(player: Player, id: Int) {
        if (id in EasyClues.trailItems) {
            TrailGuardians.leave(player)
            TrailPuzzle.clear(player)
            player.trailItem = id
            player.trailSteps = 0
            player.trailRevision++
        }
    }

    private fun available(player: Player): Boolean = player.isAlive && !player.isLocked

    private fun bind(player: Player, id: Int) {
        if (player.trailItem != id) acquired(player, id)
    }

    fun drop(player: Player, npc: Npc, random: (Int) -> Int = { Random.nextInt(it) }) {
        TrailGuardians.defeated(player, npc)
        for (clue in TrailClues.medium.filter { it.key != -1 }) {
            if (npc.id in clue.keyNpcs && player.inventory.contains(clue.id) &&
                !player.inventory.contains(clue.key) && !player.bank.contains(clue.key)) {
                world.addItem(clue.key, position = npc.position, plr = player)
            }
        }
        val tier = TrailClues.tierForNpc(npc.id) ?: return
        if (hasTrail(player) || random(128) != 0) return
        val choices = TrailClues.all.filter { it.tier == tier }
        val clue = choices[random(choices.size)]
        world.addItem(clue.id, position = npc.position, plr = player)
    }

    /**
     * Keeps existing Luna clue sources while avoiding a second roll for Lost City's explicit sources.
     */
    fun nativeDrop(player: Player?, npcId: Int, item: Item,
                   random: (Int) -> Int = { Random.nextInt(it) }): Item? {
        if (item.id !in EasyClues.trailItems) return item
        if (player == null || TrailClues.tierForNpc(npcId) != null || hasTrail(player)) return null
        val tier = TrailClues.byId[item.id]?.tier ?: return null
        val choices = TrailClues.all.filter { it.tier == tier }
        return Item(choices[random(choices.size)].id)
    }

    fun read(player: Player, id: Int) {
        if (!available(player) || !player.inventory.contains(id)) return
        val challenge = TrailClues.challenges[id]
        if (challenge != null) {
            readText(player, challenge)
            return
        }
        val clue = TrailClues.byId[id] ?: return
        if (clue.map != -1) {
            player.overlays.open(StandardInterface(clue.map))
        } else {
            readText(player, clue.text)
        }
    }

    private fun readText(player: Player, text: String) {
        val lines = text.split('|')
        val children = if (lines.size > 4) (6968..6975).toList() else listOf(6991, 6992, 6993, 7331)
        player.overlays.open(StandardInterface(if (lines.size > 4) 6965 else 6988))
        val offset = (children.size - lines.size) / 2
        children.forEachIndexed { index, child ->
            player.sendText(lines.getOrNull(index - offset) ?: "", child)
        }
    }

    fun dig(player: Player): Boolean {
        if (!available(player) || !player.inventory.contains(952)) return false
        val clue = TrailClues.all.firstOrNull {
            it.casket != -1 && it.position != null && it.kind == null && it.targetObjects.isEmpty() && player.inventory.contains(it.id) &&
                player.position.isWithinDistance(it.position, 1)
        } ?: return false
        bind(player, clue.id)
        if (clue.coordinate && !TrailNavigation.hasEquipment(player)) {
            player.sendMessage("You need your sextant, watch and navigation chart to dig here.")
            return true
        }
        if (clue.guardian && !TrailGuardians.canDig(player, clue)) return true
        if (!player.inventory.replace(clue.id, clue.casket)) return false
        player.trailItem = clue.casket
        player.sendMessage("You've found a casket!")
        return true
    }

    fun open(player: Player, id: Int, random: (Int) -> Int = { Random.nextInt(it) }): Boolean {
        if (id !in TrailClues.byCasket) return false
        return advance(player, id, random)
    }

    fun talk(player: Player, npc: Npc, random: (Int) -> Int = { Random.nextInt(it) }): Boolean {
        if (npc.state != EntityState.ACTIVE || !player.position.isWithinDistance(npc.position, 2)) return false
        val clue = TrailClues.all.firstOrNull { it.npcId == npc.id && player.inventory.contains(it.id) }
            ?: return TrailNavigation.talk(player, npc)
        if (!available(player)) return false
        bind(player, clue.id)
        if (clue.auxiliary != -1) {
            if (!player.inventory.contains(clue.auxiliary)) {
                if (!canAcquire(player, clue.auxiliary)) return true
                if (!player.inventory.hasSpaceFor(Item(clue.auxiliary))) {
                    player.sendMessage("You need a free inventory slot for the ${if (clue.puzzle) "puzzle box" else "challenge scroll"}.")
                    return true
                }
                player.inventory.add(Item(clue.auxiliary))
                if (clue.puzzle) TrailPuzzle.start(player, clue.auxiliary)
                player.overlays.closeWindows()
                player.sendMessage("You have been given a ${if (clue.puzzle) "puzzle box" else "challenge scroll"}. Solve it and speak to me again.")
                return true
            }
            if (clue.puzzle) {
                if (!TrailPuzzle.solved(player, clue.auxiliary)) {
                    player.sendMessage("You must solve the puzzle before I can give you another clue.")
                    return true
                }
                player.inventory.remove(clue.auxiliary)
            } else {
                val revision = player.trailRevision
                player.overlays.closeWindows()
                player.sendMessage("Enter the answer to your challenge scroll.")
                player.overlays.open(object : NumberInput() {
                    override fun input(player: Player, value: Int) {
                        answer(player, npc, clue.id, revision, value)
                    }
                })
                return true
            }
        }
        return advance(player, clue.id, random)
    }

    fun answer(player: Player, npc: Npc, clueId: Int, revision: Int, value: Int,
               random: (Int) -> Int = { Random.nextInt(it) }): Boolean {
        val clue = TrailClues.byId[clueId] ?: return false
        if (!available(player) || revision != player.trailRevision || clue.answer < 0 ||
            clue.npcId != npc.id || npc.state != EntityState.ACTIVE ||
            !player.position.isWithinDistance(npc.position, 2) ||
            !player.inventory.contains(clue.id) || !player.inventory.contains(clue.auxiliary)) return false
        if (value != clue.answer) {
            player.sendMessage("That isn't the correct answer. Try again.")
            return false
        }
        player.inventory.remove(clue.auxiliary)
        return advance(player, clue.id, random)
    }

    fun search(player: Player, obj: GameObject, option: Int,
               random: (Int) -> Int = { Random.nextInt(it) }): Boolean {
        if (!available(player) || obj.state != EntityState.ACTIVE ||
            !player.position.isWithinDistance(obj.position, 2)) return false
        val clue = TrailClues.all.firstOrNull {
            (it.kind != null || it.targetObjects.isNotEmpty()) && it.position == obj.position && player.inventory.contains(it.id)
        } ?: return false
        val matches = when (clue.kind) {
            Kind.CHEST_CLOSED -> option == 1 && obj.id in EasyClues.closedChests ||
                option == 2 && obj.id in EasyClues.openChests
            Kind.EMPTY_CRATE -> option == 1 && obj.id in EasyClues.emptyCrate
            Kind.EMPTY_CRATES -> option == 1 && obj.id in EasyClues.emptyCrates
            Kind.EMPTY_BOXES -> option == 1 && obj.id in EasyClues.emptyBoxes
            // Falador's rebuilt 377 map uses these searchable crates at both original clue tiles.
            Kind.FALADOR_CRATES -> option == 1 && obj.id == 11745
            Kind.DRAWER -> option == 1 && obj.id in EasyClues.drawer ||
                option == 2 && obj.id in EasyClues.emptyDrawer
            Kind.EMPTY_DRAWER -> option == 2 && obj.id in EasyClues.emptyDrawer
            else -> option == 1 && obj.id in clue.targetObjects
        }
        if (matches) {
            if (clue.key != -1) {
                if (!player.inventory.contains(clue.key)) {
                    val hint = when (clue.id) {
                        2831 -> "Property of Black Heather."
                        2833 -> "Beware of the dog!"
                        2835 -> "It's a guard's life."
                        2837 -> "Don't forget to feed the chickens."
                        else -> "Stand by your man."
                    }
                    player.sendMessage("It's locked! The inscription reads: $hint")
                    return true
                }
                player.inventory.remove(clue.key)
            }
            return advance(player, clue.id, random)
        }
        if (clue.kind == Kind.EMPTY_DRAWER && option == 1) {
            val openId = EasyClues.drawerOpen[obj.id] ?: return false
            if (!world.removeObject(obj)) return false
            player.animation(Animation(536))
            val opened = world.addObject(openId, obj.position, obj.objectType, obj.direction)
            world.scheduleOnce(300) {
                if (world.removeObject(opened)) world.addObject(obj)
            }
            return true
        }
        return false
    }

    internal fun complete(steps: Int, random: (Int) -> Int, tier: TrailTier): Boolean =
        steps >= tier.maximumSteps || steps >= tier.maximumSteps - 2 && random(2) == 0

    internal fun complete(steps: Int, random: (Int) -> Int): Boolean = complete(steps, random, TrailTier.EASY)

    private fun advance(player: Player, id: Int, random: (Int) -> Int): Boolean {
        if (!available(player) || !player.inventory.contains(id)) return false
        val clue = TrailClues.byId[id] ?: TrailClues.byCasket[id] ?: return false
        bind(player, id)
        val steps = player.trailSteps + 1
        if (!complete(steps, random, clue.tier)) {
            val choices = TrailClues.all.filter { it.tier == clue.tier }
            val next = choices[random(choices.size)].id
            if (!player.inventory.replace(id, next)) return false
            player.trailItem = next
            player.trailSteps = steps
            player.overlays.closeWindows()
            player.sendMessage("You've found another clue scroll!")
        } else {
            val rewards = TrailRewards.roll(clue.tier, random)
            if (!player.inventory.remove(id)) return false
            player.trailItem = 0
            player.trailSteps = 0
            // Award once, before opening the display; closing it cannot lose or reroll rewards.
            rewards.forEach { reward ->
                if (player.inventory.hasSpaceFor(reward)) player.inventory.add(reward)
                else world.addItem(reward, player.position, player)
            }
            player.overlays.open(StandardInterface(6960))
            player.queue(WidgetItemsMessageWriter(6963, rewards.toTypedArray()))
            player.sendMessage("You've completed a ${clue.tier.name.lowercase()} Treasure Trail! Any items that didn't fit are at your feet.")
        }
        player.trailRevision++
        TrailPuzzle.clear(player)
        TrailGuardians.leave(player)
        return true
    }
}
