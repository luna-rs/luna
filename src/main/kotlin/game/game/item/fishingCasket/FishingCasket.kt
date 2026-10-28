/*
 * Casket rewards adapted from LostCityRS/Content (377-wip),
 * scripts/skill_fishing/scripts/fishing_spots/memberfish.rs2.
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
 *
 */
package game.item.fishingCasket

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import kotlin.random.Random

/**
 * Opens item 405, the casket shared by big-net fishing and the Mysterious Old Man.
 * One owned casket is consumed before its reward is granted. The display never grants items, so
 * closing or reopening it cannot duplicate a gift. Clue-scroll caskets use their own native handler.
 *
 * @author lare96
 */
object FishingCasket {
    /**
     * The shared source item, distinct from every Treasure Trail casket.
     */
    const val CASKET = 405

    /**
     * Lost City's 128-slot members table. Coin results independently choose one of six equally
     * likely quantities: 20, 40, 80, 160, 320 or 640. The supplier is also used by local validation.
     */
    fun reward(random: (Int) -> Int = { Random.nextInt(it) }): Item = when (random(128)) {
        in 0..59 -> Item(995, (1 shl (random(6) + 1)) * 10)
        in 60..91 -> Item(1623)
        in 92..107 -> Item(1621)
        in 108..115 -> Item(1619)
        in 116..123 -> Item(1454)
        in 124..125 -> Item(1617)
        126 -> Item(985)
        else -> Item(987)
    }

    /**
     * Validates the clicked inventory slot again on the game thread, then completes one opening.
     * A full inventory can use the slot freed by the casket. An unaddable coin stack falls to the
     * owner's feet through the normal private-ground-item path rather than being silently lost.
     */
    fun open(player: Player, index: Int, random: (Int) -> Int = { Random.nextInt(it) }): Boolean {
        if (!player.isAlive || player.isLocked || index !in 0 until player.inventory.capacity() ||
            player.inventory[index]?.id != CASKET) return false
        val reward = reward(random)
        if (!player.inventory.remove(index, Item(CASKET))) return false
        player.playSound(Sound.LEVER)
        if (!player.inventory.add(reward)) world.addItem(reward, player.position, player)
        player.overlays.open(RewardDialogue(reward))
        player.playSound(Sound.FOUND_GEM)
        return true
    }

    /**
     * Displays the already-awarded source reward on the native item chatbox interface.
     * Inventory mutation belongs only to [open], including when the display is opened again.
     *
     * @author lare96
     */
    private class RewardDialogue(private val reward: Item) : DialogueInterface(306) {
        override fun init(player: Player): Boolean {
            val contents = when (reward.id) {
                995 -> "some coins"
                985, 987 -> "half a key"
                1454 -> "a cosmic talisman"
                else -> "an ${reward.itemDef.name.lowercase()}"
            }
            player.sendText("You open the casket. Inside you find $contents.", 308)
            player.queue(WidgetItemModelMessageWriter(307, 250, reward.id))
            return true
        }
    }
}
