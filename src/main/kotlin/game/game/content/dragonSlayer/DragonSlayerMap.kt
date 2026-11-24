/*
 * Map-piece mechanics adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/quests/quest_dragon.
 */
package game.content.dragonSlayer

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.addItem
import api.predef.ext.addObject
import api.predef.ext.removeObject
import api.predef.ext.scheduleOnce
import game.obj.doors.QuestDoors
import game.player.Sound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_ORACLE
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_WORMBRAIN
import io.luna.game.model.`object`.GameObject

/**
 * Records the map challenges for the character who uses a key or makes an offering. Ordinary doors stay usable.
 *
 * @author lare96
 */
object DragonSlayerMap {
    const val MAZE_ENTRANCE = 2595
    const val MAGIC_DOOR = 2586
    const val MAZE_CHEST = 2603
    const val MAZE_CHEST_OPEN = 2604
    const val ORACLE_CHEST = 2587
    const val ORACLE_CHEST_OPEN = 2588
    const val WORMBRAIN = 745
    const val ASK_MAP = "I hear you have a piece of the Crandor map."
    const val BUY_MAP = "I'll pay 10,000 coins for it."

    val mazeDoors = (2596..2601).toList()
    val mazeKeyNpcs = listOf(748, 749, 750, 751, 753, 752)
    val colouredKeys = (1543..1548).toList()
    val offerings = listOf(Item(1907), Item(950), Item(301), Item(1791))
    val oracleChestTile = Position(3057, 9841)
    val mazeChestTile = Position(2935, 9657)
    val magicDoorTile = Position(3051, 9840)
    val mazeEntranceTile = Position(2941, 3248)

    var Player.mazeDoorsUsed by Attr.int().persist("dragon_slayer_maze_doors_used")
        private set

    private val wormbrainPrice = Item(995, 10_000)

    /**
     * Records an entrance key use followed by the six coloured keys in their original order.
     */
    fun useMazeKey(player: Player, door: GameObject, key: Int) {
        if (!DragonSlayer.collectingMap(player) || !player.inventory.contains(key)) return
        val step = if (door.id == MAZE_ENTRANCE) 0 else mazeDoors.indexOf(door.id) + 1
        if (step > player.mazeDoorsUsed) {
            player.sendMessage("Use the maze keys in order, starting with the entrance key.")
            return
        }
        if (step == player.mazeDoorsUsed) {
            if (step > 0) player.inventory.remove(key)
            player.mazeDoorsUsed = step + 1
            player.sendMessage(if (step == 0) "You use the key Oziach gave you." else "The key crumbles as you turn it.")
        }
        QuestDoors.cross(player, door)
    }

    /**
     * The maze monster drops its own key when it is the next key this player needs.
     */
    fun mazeKeyDrop(player: Player, npc: Npc, index: Int) {
        if (!DragonSlayer.collectingMap(player) || player.mazeDoorsUsed != index + 1) return
        val key = colouredKeys[index]
        if (!owns(player, key)) drop(player, npc, key)
    }

    fun offer(player: Player, door: GameObject) {
        if (!DragonSlayer.collectingMap(player)) return
        val oracle = player.varpManager.getValue(DRAGON_SLAYER_ORACLE)
        if (oracle < 2) {
            player.sendMessage("Ask the Oracle about the map before making the offerings.")
            return
        }
        if (oracle == 2) {
            if (!player.inventory.containsAll(offerings)) {
                player.sendMessage("Bring all four offerings the Oracle described.")
                return
            }
            player.inventory.removeAll(offerings)
            player.varpManager.setAndSendValue(DRAGON_SLAYER_ORACLE, 3)
            player.sendMessage("The four offerings disappear in a brief glow.")
        }
        QuestDoors.cross(player, door)
    }

    fun oracleOfferingMade(player: Player): Boolean = player.varpManager.getValue(DRAGON_SLAYER_ORACLE) >= 3

    fun open(player: Player, closed: GameObject, openId: Int) {
        val world = player.world
        if (!world.removeObject(closed)) return
        player.animation(Animation(536))
        player.playSound(Sound.CHEST_OPEN, 1)
        val opened = world.addObject(openId, closed.position, closed.objectType, closed.direction)
        world.scheduleOnce(300) {
            if (world.removeObject(opened)) world.addObject(closed)
        }
    }

    fun close(player: Player, opened: GameObject, closedId: Int) {
        val world = player.world
        if (!world.removeObject(opened)) return
        world.addObject(closedId, opened.position, opened.objectType, opened.direction)
        player.playSound(Sound.CHEST_CLOSE, 1)
    }

    fun search(player: Player, piece: Int) {
        if (!needsPiece(player, piece)) {
            player.sendMessage("You find nothing else in the chest.")
            return
        }
        val unlocked = when (piece) {
            DragonSlayer.MAZE_MAP_PART -> player.mazeDoorsUsed > mazeDoors.size
            DragonSlayer.ORACLE_MAP_PART -> oracleOfferingMade(player)
            else -> false
        }
        if (!unlocked) {
            player.sendMessage(if (piece == DragonSlayer.MAZE_MAP_PART) "Use all the maze keys before taking the map piece."
                else "Make the Oracle's four offerings before taking the map piece.")
            return
        }
        if (player.inventory.add(Item(piece))) player.sendMessage("You find a piece of the Crandor map.")
        else player.sendMessage("You need room in your inventory for the map piece.")
    }

    fun wormbrain(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(WORMBRAIN, "Wormbrain's got nothing to say to the guards.")
        if (player.varpManager.getValue(DRAGON_SLAYER_WORMBRAIN) == 1 && needsPiece(player, DragonSlayer.WORMBRAIN_MAP_PART)) {
            dialogue.options(ASK_MAP, { p ->
                p.newDialogue().npc(WORMBRAIN, "You want my map? It'll cost you 10,000 coins.")
                    .options(BUY_MAP, { buy(it, npc) }, "That's too much.", { }).open()
            }, "Carry on, then.", { })
        }
        dialogue.open()
    }

    private fun buy(player: Player, npc: Npc) {
        val piece = DragonSlayer.WORMBRAIN_MAP_PART
        if (!DragonSlayer.nearby(player, npc, 4) || !needsPiece(player, piece)) return
        val inventory = player.inventory
        val coins = inventory.computeAmountForId(wormbrainPrice.id)
        if (coins < wormbrainPrice.amount) {
            player.sendMessage("You need 10,000 coins for Wormbrain's piece.")
            return
        }
        if (coins > wormbrainPrice.amount && inventory.isFull) {
            player.sendMessage("You need room in your inventory for the map piece.")
            return
        }
        inventory.updateAll(listOf(Item(piece)), listOf(wormbrainPrice))
        player.sendMessage("You buy a piece of the Crandor map from Wormbrain.")
    }

    private fun drop(player: Player, npc: Npc, id: Int) {
        val view = ChunkUpdatableView.localView(player)
        player.world.addItem(DeathGroundItem(player.context, id, 1, npc.position, view, npc))
    }

    private fun needsPiece(player: Player, piece: Int): Boolean =
        DragonSlayer.collectingMap(player) && !owns(player, piece) && !owns(player, DragonSlayer.CRANDOR_MAP)

    private fun owns(player: Player, id: Int): Boolean = player.inventory.contains(id) || player.bank.contains(id)
}
