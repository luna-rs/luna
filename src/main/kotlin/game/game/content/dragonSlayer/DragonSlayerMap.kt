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
import game.obj.doors.Doors
import game.obj.doors.QuestDoors
import game.player.Sound
import io.luna.game.model.EntityState
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
 * The map piece challenges. Melzar's Maze doors open only with their keys and the magic door only for the Oracle's
 * offerings.
 *
 * @author lare96
 */
object DragonSlayerMap {
    const val MAZE_ENTRANCE = 2595
    const val MAZE_EXIT = 2602
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

    /**
     * The maze doors unlocked in order since the player last used the entrance key. The quest bot reads its progress
     * from this.
     */
    var Player.mazeDoorsUsed by Attr.int().persist("dragon_slayer_maze_doors_used")
        private set

    private val wormbrainPrice = Item(995, 10_000)

    /**
     * Opens a maze door with its key, from either side. The entrance key is kept and each coloured key is used up.
     */
    fun useMazeKey(player: Player, door: GameObject, key: Int) {
        if (!player.inventory.contains(key)) return
        if (door.id == MAZE_ENTRANCE) {
            player.mazeDoorsUsed = 1
            player.sendMessage("You use the key Oziach gave you.")
        } else {
            val index = mazeDoors.indexOf(door.id)
            player.inventory.remove(key)
            if (player.mazeDoorsUsed == index + 1) player.mazeDoorsUsed = index + 2
            player.sendMessage("The key disintegrates as it unlocks the door...")
        }
        QuestDoors.cross(player, door)
    }

    fun lockedMazeDoor(player: Player, door: GameObject) {
        if (door.id == MAZE_ENTRANCE) {
            player.playSound(Sound.LOCKED)
            player.sendMessage("The door is securely locked.")
        } else {
            player.sendMessage("This door is securely locked.")
        }
    }

    /**
     * The maze's exit doors only open from inside, the side of the door's own tile.
     */
    fun mazeExit(player: Player, door: GameObject) {
        if (Doors.isEntering(door.position, door.direction, player.position)) QuestDoors.cross(player, door)
        else player.sendMessage("The door is locked.")
    }

    /**
     * Every maze monster drops its key, whether or not its killer is on the quest.
     */
    fun mazeKeyDrop(player: Player, npc: Npc, index: Int) = drop(player, npc, colouredKeys[index])

    /**
     * The magic door opens freely from inside and once the offerings are made. Otherwise opening it makes the
     * offerings if the player carries all four.
     */
    fun openMagicDoor(player: Player, door: GameObject) {
        if (oracleOfferingMade(player) || Doors.isEntering(door.position, door.direction, player.position)) {
            QuestDoors.cross(player, door)
        } else if (DragonSlayer.collectingMap(player) && player.varpManager.getValue(DRAGON_SLAYER_ORACLE) == 2 &&
            player.inventory.containsAll(offerings)) {
            offer(player, door)
        } else {
            player.sendMessage("The door is locked.")
        }
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
            // A map object that was removed stays hidden if it is added back, so a new closed chest is placed.
            if (opened.state == EntityState.ACTIVE && world.removeObject(opened)) {
                world.addObject(closed.id, closed.position, closed.objectType, closed.direction)
            }
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
        if (piece == DragonSlayer.ORACLE_MAP_PART && !oracleOfferingMade(player)) {
            player.sendMessage("Make the Oracle's four offerings before taking the map piece.")
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
