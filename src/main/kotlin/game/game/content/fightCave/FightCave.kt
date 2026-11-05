package game.content.fightCave

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.item.Item
import io.luna.game.model.map.DynamicMapController
import io.luna.game.model.map.builder.DynamicMapBuilder
import io.luna.game.model.map.builder.DynamicMapPalette
import io.luna.game.model.mob.Player
import java.util.function.Function

/**
 * The TzHaar Fight Cave. Every player who enters fights alone, in their own copy of the cave.
 *
 * @author TheLining
 */
object FightCave {

    /**
     * The cave entrance in TzHaar City.
     */
    const val ENTRANCE = 9356

    /**
     * The way back out, inside the cave.
     */
    const val EXIT = 9357

    /**
     * TzHaar-Mej-Jal, who guards the entrance.
     */
    const val MEJ_JAL = 2617

    /**
     * The reward for beating TzTok-Jad.
     */
    const val FIRE_CAPE = 6570

    /**
     * The TzHaar currency.
     */
    const val TOKKUL = 6529

    /**
     * Tokkul for a win, and for a win by a player who already has a fire cape.
     */
    const val WIN_TOKKUL = 8_032
    const val REPEAT_WIN_TOKKUL = 16_064

    /**
     * The cave's map square.
     */
    val REGION = Region(9551)

    /**
     * Where players arrive in the cave, as a tile of the real cave.
     */
    val START = Position(2400, 5084)

    /**
     * Where players come out, beside the entrance.
     */
    val OUTSIDE = Position(2438, 5168)

    /**
     * The wave a run saved between waves resumes at, or 0 if there's none.
     */
    var Player.fightCaveWave by Attr.int().persist("fight_cave_wave")

    /**
     * Builds a copy of the cave for [plr] and moves them into it, to start at [wave].
     */
    fun enter(plr: Player, wave: Int = 1) {
        val palette = DynamicMapPalette()
        // The ceiling rock is on planes 1 and 2.
        for (plane in 0..2) {
            palette.setRegion(4, plane, REGION, plane)
        }
        val map = DynamicMapBuilder(ctx)
            .setPalette(palette.getChunk(6, 6, 0).chunk, palette)
            .setController(Function<Player, DynamicMapController> { FightCaveController(it, wave) })
            .build()
        map.create()
        map.add(plr, map.getInstancePosition(START))
    }

    /**
     * Takes [plr] out of the cave by its exit.
     */
    fun leave(plr: Player) {
        val controller = plr.controllers.primary as? FightCaveController
        if (controller != null) controller.leave() else plr.move(OUTSIDE)
    }

    /**
     * The Tokkul for ending a run on [wave]: more the further the player got.
     */
    fun tokkul(wave: Int) = wave * (wave + 1)

    /**
     * Whether [plr] has a fire cape anywhere: carried, worn or banked.
     */
    fun hasFireCape(plr: Player) =
        plr.inventory.contains(FIRE_CAPE) || plr.equipment.contains(FIRE_CAPE) || plr.bank.contains(FIRE_CAPE)

    /**
     * Gives [plr] an item, or drops it under them if it won't fit.
     */
    fun give(plr: Player, id: Int, amount: Int) {
        if (!plr.inventory.add(Item(id, amount))) world.addItem(id, amount, plr.position, plr)
    }
}
