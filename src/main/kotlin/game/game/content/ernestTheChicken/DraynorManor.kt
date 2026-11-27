/*
 * Adapted from LostCityRS/Content scripts/quests/quest_haunted at
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. See LICENSE.txt.
 */
package game.content.ernestTheChicken

import api.predef.ext.addObject
import api.predef.ext.scheduleOnce
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject

/**
 * Handles the manor's bookcase, compost heap, closet and fountain at their native locations.
 *
 * @author lare96
 */
object DraynorManor {

    const val CLOSET = 131
    const val LADDER_UP = 132
    const val LADDER_DOWN = 133
    const val FRONT_DOOR = 134
    const val SIDE_DOOR = 136
    const val COMPOST = 152
    const val FOUNTAIN = 153
    const val BOOKCASE_SOUTH = 155
    const val BOOKCASE_NORTH = 156
    const val SECRET_LEVER = 160

    const val FISH_FOOD = 272
    const val POISON = 273
    const val POISONED_FISH_FOOD = 274
    const val KEY = 275
    const val SPADE = 952

    val closet = Position(3107, 3367)
    val ladderUp = Position(3117, 9754)
    val ladderDown = Position(3092, 3362)
    val frontDoor = Position(3108, 3353)
    val sideDoor = Position(3123, 3361)
    val compost = Position(3084, 3360)
    val fountain = Position(3087, 3334)
    val bookcaseSouth = Position(3097, 3358)
    val bookcaseNorth = Position(3097, 3359)
    val secretLever = Position(3096, 3357)
    val fishFood = Position(3108, 3356, 1)
    val poison = Position(3097, 3366)
    val rubberTube = Position(3111, 3367)
    val spade = Position(3120, 3359)

    private const val OPEN_BOOKCASE = 157
    private const val LOWERED_LEVER = 161
    private val closetDoor = DoorType(CLOSET, 1532, null, null, null, 3)

    fun crossCloset(player: Player, door: GameObject) {
        if (player.actions.first(ClosetPassage::class.java) == null) player.submitAction(ClosetPassage(player, door))
    }

    fun dig(player: Player, heap: GameObject) {
        player.animation(Animation(830))
        player.sendMessage("You dig through the compost heap.")
        player.world.scheduleOnce(2) {
            if (player.state != EntityState.ACTIVE || !player.isAlive || player.z != heap.position.z ||
                !player.position.isWithinDistance(heap.position, 3) || !player.inventory.contains(SPADE)) return@scheduleOnce
            when {
                player.inventory.contains(KEY) -> player.sendMessage("You find nothing else of interest.")
                player.inventory.add(KEY) -> player.sendMessage("You find a small key.")
                else -> player.sendMessage("You need a free inventory space for the key.")
            }
        }
    }

    fun mixFood(player: Player) {
        val inventory = player.inventory
        if (inventory.contains(FISH_FOOD) && inventory.contains(POISON)) {
            inventory.updateAll(listOf(Item(POISONED_FISH_FOOD)), listOf(Item(FISH_FOOD), Item(POISON)))
            player.sendMessage("You poison the fish food.")
        }
    }

    fun fountainPoisoned(player: Player): Boolean =
        player.varpManager.getValue(PersistentVarp.ERNEST_FOUNTAIN) != 0

    fun feedFish(player: Player) {
        if (!player.inventory.remove(Item(POISONED_FISH_FOOD))) return
        player.animation(Animation(881))
        player.varpManager.setValue(PersistentVarp.ERNEST_FOUNTAIN, 1)
        player.sendMessage("You pour the poisoned fish food into the fountain.")
        player.world.scheduleOnce(3) {
            if (player.state == EntityState.ACTIVE && player.z == fountain.z &&
                player.position.isWithinDistance(fountain, 4)) {
                player.sendMessage("The piranhas eat the food and float to the surface.")
            }
        }
    }

    fun searchFountain(player: Player) {
        player.animation(Animation(881))
        player.playSound(Sound.FISH_SWIM)
        when {
            player.inventory.contains(ErnestTheChicken.PRESSURE_GAUGE) ->
                player.newDialogue().player("It's full of dead fish.").open()
            !fountainPoisoned(player) -> {
                player.damage(1)
                player.newDialogue().player("There's a pressure gauge in there, but the piranhas won't let me get it!").open()
            }
            player.inventory.add(ErnestTheChicken.PRESSURE_GAUGE) ->
                player.sendMessage("You retrieve the pressure gauge from the fountain.")
            else -> player.sendMessage("You need a free inventory space for the pressure gauge.")
        }
    }

    fun searchBookcase(player: Player, bookcase: GameObject) {
        if (player.x <= bookcase.x) return
        player.sendMessage("You've found a secret door!")
        openBookcase(player, bookcase.position.translate(-1, 0))
    }

    fun pullSecretLever(player: Player, lever: GameObject) {
        if (player.x > lever.x) return
        player.animation(Animation(834))
        player.playSound(Sound.LEVER)
        replaceForThreeTicks(lever, LOWERED_LEVER)
        player.sendMessage("The lever opens the secret door!")
        openBookcase(player, bookcaseSouth.translate(1, 0))
    }

    private fun openBookcase(player: Player, destination: Position) {
        for (tile in listOf(bookcaseSouth, bookcaseNorth)) {
            player.world.objects.findAll(tile).filter { it.id == BOOKCASE_SOUTH || it.id == BOOKCASE_NORTH }
                .findFirst().ifPresent { replaceForThreeTicks(it, OPEN_BOOKCASE) }
        }
        player.playSound(Sound.COFFIN_OPEN)
        player.move(destination)
    }

    private fun replaceForThreeTicks(original: GameObject, id: Int) {
        val world = original.world
        val alternate = world.addObject(id, original.position, original.objectType, original.direction)
        world.scheduleOnce(3) {
            if (alternate.state == EntityState.ACTIVE) {
                world.objects.unregister(alternate)
                world.objects.register(original)
                original.show()
            }
        }
    }

    /**
     * The key remains obtainable before, during and after the quest.
     *
     * @author lare96
     */
    private class ClosetPassage(player: Player, door: GameObject) :
        DoorPassageAction(player, door, closetDoor) {

        override fun checkAccess(entering: Boolean): Boolean = mob.inventory.contains(KEY)

        override fun onPassage(entering: Boolean) {
            mob.sendMessage("You unlock the door and go through.")
        }
    }
}
