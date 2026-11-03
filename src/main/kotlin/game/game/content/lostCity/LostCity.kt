/*
 * Adapted from LostCityRS/Content scripts/quests/quest_zanaris and areas/area_lumbridge,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 * MIT License
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
package game.content.lostCity

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.scheduleOnce
import game.obj.entrances.Zanaris
import game.player.QuestJournalInterface
import game.player.Sound
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Lost City's adventurer, Shamus, Dramen guardian and staff progression, following the pinned 377 quest scripts.
 * Existing staff-based Zanaris travel remains available independently of quest progression.
 *
 * @author lare96
 */
object LostCity {
    const val WARRIOR = 650
    const val SHAMUS = 654
    const val SPIRIT = 655
    const val SHAMUS_TREE = 2409
    const val DRAMEN_TREE = 1292
    const val BRANCH = 771
    const val KNIFE = 946
    const val JOURNAL_BUTTON = 7367
    const val STARTED = 1
    const val SPOKEN_SHAMUS = 2
    const val SPIRIT_DEFEATED = 3
    const val TREE_CHOPPED = 4
    const val STAFF_MADE = 5
    const val COMPLETE = 6
    val SHAMUS_SPAWN = Position(3139, 3211)
    val SPIRIT_SPAWN = Position(2860, 9737)
    private var Player.guardian by Attr.nullableObj(DramenSpirit::class)

    /**
     * Reads the native saved quest word; later clicks cannot reset completed progress.
     */
    fun stage(player: Player): Int = player.varpManager.getValue(PersistentVarp.LOST_CITY).coerceIn(0, COMPLETE)

    /**
     * Advances monotonically and refreshes the native quest-list colour.
     */
    internal fun advance(player: Player, next: Int) {
        if (next > stage(player)) player.varpManager.setAndSendValue(PersistentVarp.LOST_CITY, next.coerceAtMost(COMPLETE))
        refresh(player)
    }

    /**
     * Makes the journal's native sidebar entry visible with its saved progress on login.
     */
    fun refresh(player: Player) {
        val colour = when (stage(player)) { 0 -> 0xff0000; COMPLETE -> 0x00ff00; else -> 0xffff00 }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    /**
     * Reveals the leprechaun instead of treating his home as a normal log tree.
     */
    fun shakeTree(player: Player, tree: GameObject) {
        if (!reached(player, tree, SHAMUS_TREE)) return
        if (Axe.computeAxeType(player) == null) { Axe.sendNoAxeMessages(player); return }
        val existing = player.world.npcs.firstOrNull {
            it.id == SHAMUS && it.state == EntityState.ACTIVE && it.z == SHAMUS_SPAWN.z &&
                it.position.isWithinDistance(SHAMUS_SPAWN, 15)
        }
        if (existing != null) {
            player.newDialogue().npc(SHAMUS, "Hey! Yer big elephant! Don't go choppin' down me house, now!").open()
            return
        }
        val shamus = Npc(player.context, SHAMUS, SHAMUS_SPAWN)
        shamus.respawnTicks = -1
        if (player.world.npcs.add(shamus)) {
            shamus.speak("Hey! Yer big elephant! Don't go choppin' down me house, now!")
            shamus.navigator.navigate(SHAMUS_SPAWN.translate(10, 0), false)
            player.world.scheduleOnce(100) {
                if (shamus.state == EntityState.ACTIVE) player.world.npcs.remove(shamus)
            }
        }
    }

    /**
     * Checks quest knowledge before tools and level, then requires the guardian before giving any branches.
     */
    fun cutDramen(player: Player, tree: GameObject) {
        if (!reached(player, tree, DRAMEN_TREE)) return
        if (stage(player) < SPOKEN_SHAMUS) {
            player.newDialogue().text("The tree seems to have an ominous aura to it.",
                "You do not feel like chopping it down.").open()
            return
        }
        val axe = Axe.computeAxeType(player)
        if (axe == null) { Axe.sendNoAxeMessages(player); return }
        if (player.skill(Skill.WOODCUTTING).level < 36) {
            player.sendMessage("You are not a high enough Woodcutting level to chop down this tree.")
            player.sendMessage("You need a Woodcutting level of 36.")
            return
        }
        if (stage(player) == SPOKEN_SHAMUS) {
            summonSpirit(player)
            return
        }
        if (player.inventory.isFull) { player.sendMessage("You don't have space to take a branch."); return }
        player.submitAction(object : LockedAction(player, false, 1) {
            override fun beforeLock(): Boolean = reached(mob, tree, DRAMEN_TREE) && stage(mob) >= SPIRIT_DEFEATED &&
                mob.skill(Skill.WOODCUTTING).level >= 36 && Axe.hasAxe(mob, axe) && !mob.inventory.isFull

            override fun onLock() {
                if (mob.inventory.add(Item(BRANCH))) {
                    mob.animation(axe.animation)
                    mob.playSound(Sound.TREE_FALLEN)
                    mob.sendMessage("You cut a branch from the Dramen tree.")
                    advance(mob, TREE_CHOPPED)
                }
            }

            override fun run(): Boolean = true
            override fun onUnlock() { mob.animation(Animation.CANCEL) }
        })
    }

    /**
     * Creates at most one live guardian per player. Its timer, departure and death never grant quest progress.
     */
    private fun summonSpirit(player: Player) {
        val current = player.guardian?.takeIf { it.state == EntityState.ACTIVE }
        if (current != null) {
            current.speak("You must defeat me before touching the tree!")
            if (current.isAlive && current.combat.target == null) current.combat.attack(player)
            return
        }
        val spirit = DramenSpirit(player, SPIRIT_SPAWN)
        player.guardian = spirit
        if (!player.world.npcs.add(spirit)) { player.guardian = null; return }
        spirit.speak("You must defeat me before touching the tree!")
        spirit.combat.attack(player)
    }

    /**
     * Credits only the owner who defeated this exact live encounter, once.
     */
    fun defeated(spirit: DramenSpirit, source: Mob?) {
        val player = spirit.owner
        if (player.guardian === spirit && stage(player) == SPOKEN_SHAMUS && source === player &&
            player.state == EntityState.ACTIVE && player.isAlive && player.z == spirit.z &&
            spirit.position.isWithinDistance(player.position, 15)) {
            advance(player, SPIRIT_DEFEATED)
            player.sendMessage("With the Tree Spirit defeated you can now chop the tree.")
        }
        spirit.world.npcs.remove(spirit)
    }

    /**
     * Clears encounter ownership without changing the persistent quest stage.
     */
    fun clear(player: Player) {
        player.guardian?.let { if (it.state == EntityState.ACTIVE) player.world.npcs.remove(it) }
        player.guardian = null
    }

    /**
     * Drops only a reference to this spirit, preserving any later replacement encounter.
     */
    fun removed(spirit: DramenSpirit) {
        if (spirit.owner.guardian === spirit) spirit.owner.guardian = null
    }

    /**
     * Carves the actual current branch slot with a knife, including in a full inventory, with no XP reward.
     */
    fun carve(player: Player): Boolean {
        if (!ready(player) || !player.inventory.contains(KNIFE)) return false
        val slot = player.inventory.computeIndexForId(BRANCH)
        if (slot == -1) return false
        if (player.skill(Skill.CRAFTING).level < 31) {
            player.sendMessage("You need at least level 31 Crafting to make this into a staff.")
            return false
        }
        player.inventory[slot] = Item(Zanaris.STAFF)
        player.sendMessage("You carve the branch into a staff.")
        if (stage(player) == TREE_CHOPPED) advance(player, STAFF_MADE)
        return true
    }

    /**
     * Awards Lost City's three quest points once after the staff-making player actually arrives in Zanaris.
     * Existing players with a staff can still enter without starting or completing this quest.
     */
    fun complete(player: Player): Boolean {
        if (stage(player) != STAFF_MADE || !player.isAlive || player.z != Zanaris.LANDING.z ||
            !player.position.isWithinDistance(Zanaris.LANDING, 2)) return false
        advance(player, COMPLETE)
        player.varpManager.setAndSendValue(PersistentVarp.QUEST_POINTS,
            player.varpManager.getValue(PersistentVarp.QUEST_POINTS) + 3)
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\nLost City of Zanaris Quest!", 12144)
                plr.sendText("3 Quest Points", 12150)
                plr.sendText("Access to Zanaris", 12151)
                for (id in 12152..12155) plr.sendText("", id)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, 995))
            }
        })
        return true
    }

    /**
     * Uses the normal object interaction geometry, including the Dramen tree's complete footprint.
     */
    private fun reached(player: Player, tree: GameObject, id: Int): Boolean = tree.id == id &&
        tree.state == EntityState.ACTIVE && ready(player) && tree.isVisibleTo(player) &&
        player.world.collisionManager.reached(player, tree, InteractionPolicy.STANDARD_SIZE)

    private fun ready(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    /**
     * Shows useful progress from the native Lost City quest-list button.
     */
    fun journal(player: Player) {
        val journal = QuestJournalInterface("Lost City")
        val lines = when (stage(player)) {
            0 -> listOf("@dbl@I can start this quest by speaking to the adventurers in",
                "@dbl@the swamp just south of Lumbridge.", "", "@dbl@To complete this quest I need:",
                "@dre@Level 31 Crafting", "@dre@Level 36 Woodcutting",
                "@dbl@and to defeat a level 101 Spirit without weapons.")
            STARTED -> listOf("@dbl@A leprechaun hiding in a tree near the adventurers",
                "@dbl@can tell me how to enter the Lost City of Zanaris.")
            SPOKEN_SHAMUS -> listOf("@dbl@Shamus told me to enter the shed in Lumbridge Swamp",
                "@dbl@while holding a Dramen staff.", "@dbl@A Dramen tree grows in a cave on Entrana.")
            SPIRIT_DEFEATED -> listOf("@str@I defeated the Tree Spirit.", "@dbl@I can now cut a branch from the Dramen tree.")
            TREE_CHOPPED -> listOf("@str@I cut a branch from the Dramen tree.", "@dbl@I should carve it into a staff using a knife.")
            STAFF_MADE -> listOf("@str@I crafted a Dramen staff.", "@dbl@I should enter the shed in Lumbridge Swamp with it equipped.")
            else -> listOf("@str@I found and entered the Lost City of Zanaris.", "", "@red@QUEST COMPLETE!", "3 Quest Points")
        }
        lines.forEach(journal::addLine)
        player.overlays.open(journal)
    }
}
