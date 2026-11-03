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

import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * The adventurer's slip of the tongue and Shamus's instructions make the Dramen sequence reachable naturally.
 * Each delayed progress callback requires the same nearby conversation partner and its expected saved stage.
 *
 * @author lare96
 */
object LostCityDialogue {

    /**
     * Starts the quest through the warrior's native conversation path.
     */
    fun warrior(player: Player, warrior: Npc) {
        if (!near(player, warrior, LostCity.WARRIOR)) return
        when (LostCity.stage(player)) {
            0 -> player.newDialogue().npc(LostCity.WARRIOR, "Hello there traveller.")
                .options("What are you camped here for?", { camp(it, warrior) },
                    "Do you know any good adventures I can go on?", {
                        it.newDialogue().npc(LostCity.WARRIOR, "We're on an adventure right now.",
                            "This is OUR adventure. Find your own!")
                            .options("Please tell me?", { please(it) },
                                "I don't think you've found a good adventure at all!", { p ->
                                    p.newDialogue().npc(LostCity.WARRIOR,
                                        "Adventurers of our calibre don't just hang",
                                        "around in forests for fun!")
                                        .then { camp(it, warrior) }.open()
                                }).open()
                    }).open()
            LostCity.STARTED -> player.newDialogue().player("A leprechaun in a tree can tell me where Zanaris is?")
                .npc(LostCity.WARRIOR, "What? How did you know that?",
                    "You're very wrong, and I definitely didn't tell you that.").open()
            LostCity.COMPLETE -> player.newDialogue().player("Thanks for the help finding Zanaris!")
                .npc(LostCity.WARRIOR, "Please don't say that! My party would make me walk home!",
                    "Where is Zanaris? Is it all the legends say it is?")
                .player("I think I'll keep that to myself.").open()
            else -> player.newDialogue().npc(LostCity.WARRIOR,
                "We're still searching for Zanaris... GAH!",
                "I mean, we're not doing anything here.").open()
        }
    }

    private fun camp(player: Player, warrior: Npc) {
        if (!near(player, warrior, LostCity.WARRIOR)) return
        player.newDialogue().npc(LostCity.WARRIOR,
            "We're looking for Zanaris... GAH!",
            "I mean we're not here for any particular reason.")
            .options("Who's Zanaris?", {
                it.newDialogue().npc(LostCity.WARRIOR,
                    "Zanaris isn't a person! It's a magical hidden city",
                    "filled with treasures.",
                    "Uh, nothing. It's nothing.").then { hidden(it, warrior) }.open()
            }, "What's Zanaris?", {
                it.newDialogue().npc(LostCity.WARRIOR,
                    "We don't want other people competing with us.",
                    "Forget I said anything.")
                    .options("Please tell me.", { please(it) }, "Never mind.", { it.overlays.closeWindows() }).open()
            }, "What makes you think it's out here?", {
                it.newDialogue().npc(LostCity.WARRIOR,
                    "The legends tell of a magical city hidden in the swamp...",
                    "Uh, no, you're right. We're wasting our time here.").then { hidden(it, warrior) }.open()
            }).open()
    }

    private fun hidden(player: Player, warrior: Npc) {
        if (!near(player, warrior, LostCity.WARRIOR)) return
        player.newDialogue().options("If it's hidden how are you planning to find it?", {
            it.newDialogue().npc(LostCity.WARRIOR,
                "We don't want to share all that glory and treasure.")
                .options("Please tell me?", { please(it) }, "Looks like you don't know either.", { p ->
                    p.newDialogue().npc(LostCity.WARRIOR,
                        "Of course we know! We haven't found which tree",
                        "the leprechaun's hiding in!",
                        "GAH! Forget I said anything!")
                        .player("So a leprechaun knows where Zanaris is?")
                        .npc(LostCity.WARRIOR,
                            "No! He definitely isn't hiding in some tree around here.")
                        .player("Thanks for the help!")
                        .then {
                            if (near(it, warrior, LostCity.WARRIOR) && LostCity.stage(it) == 0)
                                LostCity.advance(it, LostCity.STARTED)
                        }
                        .open()
                }).open()
        }, "There's no such thing!", { camp(it, warrior) }).open()
    }

    private fun please(player: Player) {
        player.newDialogue().player("Please tell me?").npc(LostCity.WARRIOR, "No.")
            .player("Please?").npc(LostCity.WARRIOR, "No!")
            .player("PLEEEEEEEEEEEEEEEEEEEEEEASE???").npc(LostCity.WARRIOR, "NO!").open()
    }

    /**
     * Reveals how to make the staff only after the warrior has disclosed the leprechaun's secret.
     */
    fun shamus(player: Player, shamus: Npc) {
        if (!near(player, shamus, LostCity.SHAMUS)) return
        val dialogue = player.newDialogue().npc(LostCity.SHAMUS,
            "Ay yer big elephant! Yer've caught me!",
            "What would yer be wanting wid ol' Shamus?")
        when (LostCity.stage(player)) {
            0 -> dialogue.player("I'm not sure.").npc(LostCity.SHAMUS,
                "Catch me again when yer are, elephant!").then { disappear(it, shamus) }.open()
            LostCity.STARTED -> dialogue.player("I want to find Zanaris.")
                .npc(LostCity.SHAMUS, "Yer'll be needing that funny little shed in the swamp.")
                .player("But Zanaris is a city! How does it fit in a shed?")
                .npc(LostCity.SHAMUS, "The city isn't IN the shed! Its doorway is a portal!",
                    "Yer need to be holding a Dramenwood staff to get there.")
                .player("Where would I get a staff?")
                .npc(LostCity.SHAMUS, "Craft one from a branch of the Dramen tree.",
                    "There's one in a cave on Entrana.",
                    "The monks sail there from Port Sarim.")
                .then {
                    if (near(it, shamus, LostCity.SHAMUS) && LostCity.stage(it) == LostCity.STARTED) {
                        LostCity.advance(it, LostCity.SPOKEN_SHAMUS)
                        disappear(it, shamus)
                    }
                }.open()
            else -> dialogue.options("I'm not sure.", { disappear(it, shamus) },
                "How do I get to Zanaris again?", {
                    it.newDialogue().npc(LostCity.SHAMUS,
                        "Enter the shed while holding a Dramenwood staff!",
                        "Make it from a Dramen branch. There's a tree in a cave on Entrana.")
                        .then { disappear(it, shamus) }.open()
                }).open()
        }
    }

    private fun disappear(player: Player, shamus: Npc) {
        if (near(player, shamus, LostCity.SHAMUS)) {
            player.sendMessage("The leprechaun magically disappears.")
            player.world.npcs.remove(shamus)
        }
    }

    private fun near(player: Player, npc: Npc, id: Int): Boolean = npc.id == id &&
        npc.state == EntityState.ACTIVE && player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && npc.z == player.z && npc.position.isWithinDistance(player.position, 3)
}
