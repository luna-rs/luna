/*
 * Adapted from LostCityRS/Content, quest_haunted and area_draynor at
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. See LICENSE.txt.
 */
package game.content.ernestTheChicken

import api.predef.ext.playJingle
import api.predef.ext.scheduleOnce
import api.quest.QuestProgress
import game.obj.entrances.KillerwattAccess
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.action.DeathInterruptible
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Tracks Veronica's request and restores Ernest when the professor receives his machine parts.
 *
 * @author lare96
 */
object ErnestTheChicken {

    const val VERONICA = 285
    const val PROFESSOR = KillerwattAccess.PROFESSOR
    private const val ERNEST = 287
    private const val CHICKEN = 3290
    const val JOURNAL_BUTTON = 7339

    const val PRESSURE_GAUGE = 271
    const val RUBBER_TUBE = 276
    const val OIL_CAN = 277

    const val STARTED = 1
    const val FIND_PARTS = 2
    const val COMPLETE = 3

    const val ACCEPT = "I'll help you find Ernest."
    const val ASK_ABOUT_ERNEST = "I'm looking for Ernest."
    const val CHANGE_HIM_BACK = "Can you change him back?"

    private const val COINS = 995
    private const val CASH_REWARD = 300
    private const val FEATHER = 314
    private val progress = QuestProgress(PersistentVarp.ERNEST_THE_CHICKEN, COMPLETE, 4)
    private val parts = linkedMapOf(OIL_CAN to "Oil can", PRESSURE_GAUGE to "Pressure gauge", RUBBER_TUBE to "Rubber tube")
    private val lab = Position(3110, 3366, 2)

    fun stage(player: Player): Int = progress.stage(player)

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            0 -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("Ernest the Chicken")
        val lines = when (stage(player)) {
            0 -> listOf("@dbl@I can start by speaking to @dre@Veronica@dbl@ outside",
                "@dbl@Draynor Manor. There are no requirements.")
            STARTED -> listOf("@str@I spoke to Veronica.", "", "@dbl@I need to find @dre@Ernest@dbl@ inside Draynor Manor.")
            FIND_PARTS -> listOf("@str@I spoke to Veronica.", "@str@Professor Oddenstein turned Ernest into a chicken.", "",
                "@dbl@I need to bring the professor three machine parts:") + parts.map { (id, name) ->
                (if (player.inventory.contains(id)) "@str@" else "@dre@") + name
            }
            else -> listOf("@str@I found the missing parts for Professor Oddenstein.",
                "@str@His machine turned Ernest back into a man.", "@str@Ernest gave me 300 coins.", "",
                "@red@QUEST COMPLETE!", "4 Quest Points", "300 Coins")
        }
        lines.forEach(journal::addLine)
        player.overlays.open(journal)
    }

    fun veronica(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (stage(player)) {
            0 -> dialogue.npc(VERONICA, "Can you help me? I'm in a terrible spot of trouble.")
                .options(ACCEPT, { p ->
                    p.newDialogue().npc(VERONICA, "My fiance Ernest went into the manor to ask for directions.",
                        "He still hasn't come out. Could you look for him?")
                        .player("I'll see what I can do.")
                        .npc(VERONICA, "Thank you. I'm very grateful.")
                        .then { if (near(it, npc)) advance(it, 0, STARTED) }.open()
                }, "No, I've got something else to do.", { p ->
                    p.newDialogue().npc(VERONICA, "Oh dear. I hope someone comes along soon.").open()
                })
            STARTED -> dialogue.npc(VERONICA, "Have you found Ernest yet?").player("No, I'm still looking.")
            FIND_PARTS -> dialogue.npc(VERONICA, "Have you found Ernest yet?")
                .player("Yes, a scientist turned him into a chicken.")
                .npc(VERONICA, "My poor darling! Please find a way to turn him back.")
            COMPLETE -> dialogue.npc(VERONICA, "Thank you for rescuing Ernest.")
                .npc(VERONICA, "He went off to speak to a green warty fellow. He'll be back soon.")
        }
        dialogue.open()
    }

    fun professor(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (stage(player)) {
            FIND_PARTS -> {
                dialogue.npc(PROFESSOR, "Have you found any of the missing parts?")
                val missing = parts.filterKeys { !player.inventory.contains(it) }
                if (missing.isEmpty()) {
                    dialogue.player("I have everything.").npc(PROFESSOR, "Hand them over and I'll start her up.")
                        .then { if (near(it, npc)) it.submitAction(RestoreErnest(it, npc)) }
                } else {
                    dialogue.npc(PROFESSOR, "I still need these parts:", missing.values.joinToString(", ") + ".")
                }
            }
            STARTED -> dialogue.npc(PROFESSOR, "Can I help you?").options(ASK_ABOUT_ERNEST, { p ->
                p.newDialogue().npc(PROFESSOR, "Ernest helped me test my transmutation machine.",
                    "He's that chicken over there.")
                    .options(CHANGE_HIM_BACK, { explainParts(it, npc) },
                        "Veronica probably didn't expect to marry a chicken.", { explainParts(it, npc) }).open()
            }, "What does this machine do?", ::machine, "Is this your house?", ::house)
            else -> dialogue.npc(PROFESSOR, "Be careful. There's dangerous equipment up here.")
                .options("What does this machine do?", ::machine, "Is this your house?", ::house)
        }
        dialogue.open()
    }

    private fun machine(player: Player) {
        player.newDialogue().npc(PROFESSOR, "It's a transmutation machine. Or it would be, if it worked.")
            .npc(PROFESSOR, "It used to travel through time, make lightning, and generate monsters.").open()
    }

    private fun house(player: Player) {
        player.newDialogue().npc(PROFESSOR, "I'm a tenant. The count who lives in the basement owns the manor.").open()
    }

    private fun explainParts(player: Player, npc: Npc) {
        player.newDialogue().npc(PROFESSOR, "The house gremlins stole some parts before I could change him back.")
            .npc(PROFESSOR, "Look around the house and its grounds. I need a pressure gauge,",
                "a rubber tube and my oil can.")
            .then { if (near(it, npc)) advance(it, STARTED, FIND_PARTS) }.open()
    }

    private fun advance(player: Player, current: Int, next: Int) {
        if (stage(player) == current && progress.advance(player, next)) refresh(player)
    }

    private fun near(player: Player, npc: Npc): Boolean =
        player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
            npc.state == EntityState.ACTIVE && player.z == npc.z && player.position.isWithinDistance(npc.position, 3)

    private fun presentReward(player: Player) {
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed Ernest the Chicken!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, FEATHER))
                plr.sendText("4 Quest Points", 12150)
                plr.sendText("300 Coins", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }
        })
    }

    /**
     * Keeps the player in the lab until the ray fires. A disconnect or death before that point leaves all parts
     * in inventory so the conversation can be retried.
     *
     * @author lare96
     */
    private class RestoreErnest(player: Player, private val professor: Npc) :
        LockedAction(player), DeathInterruptible {

        override fun beforeLock(): Boolean {
            if (!near(mob, professor) || stage(mob) != FIND_PARTS ||
                !parts.keys.all { mob.inventory.contains(it) }) return false
            if (mob.inventory.computeAmountForId(COINS) > Int.MAX_VALUE - CASH_REWARD) {
                mob.sendMessage("Make room in your coin stack before finishing the quest.")
                return false
            }
            return true
        }

        override fun onLock() {
            mob.walking.clear()
            mob.overlays.closeWindows()
        }

        override fun run(): Boolean {
            when (executions) {
                0 -> mob.sendMessage("You hand the three parts to Professor Oddenstein.")
                1 -> mob.sendMessage("The professor starts the machine.")
                3 -> mob.sendMessage("The machine hums and shakes.")
                5 -> mob.sendMessage("A ray shoots from the machine and hits the chicken.")
                6 -> {
                    val chicken = mob.world.locator.findNpcs(lab, 5) {
                        it.baseId == CHICKEN && it.z == lab.z && it.state == EntityState.ACTIVE
                    }.firstOrNull() ?: return true
                    if (!parts.keys.all { mob.inventory.contains(it) } ||
                        mob.inventory.computeAmountForId(COINS) > Int.MAX_VALUE - CASH_REWARD ||
                        !progress.complete(mob, FIND_PARTS)) return true
                    mob.inventory.updateAll(listOf(Item(COINS, CASH_REWARD)), parts.keys.map { Item(it) })
                    if (chicken.id == CHICKEN) {
                        chicken.transformKeepingLevels(ERNEST)
                        mob.world.scheduleOnce(100) {
                            if (chicken.state == EntityState.ACTIVE && chicken.id == ERNEST) chicken.resetTransform()
                        }
                    }
                    mob.sendMessage("Ernest thanks you and hands you 300 coins.")
                    refresh(mob)
                    presentReward(mob)
                    return true
                }
            }
            return false
        }
    }
}
