package game.content.mageArena

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.item.shop.ShopInterface

/**
 * Kolodion, the guardian and the arena-side services, adapted from the pinned area_mage_arena scripts.
 * Dialogue callbacks recheck location and progression before starting combat or handing out an item.
 *
 * @author lare96
 */
object MageArenaDialogue {
    fun kolodion(player: Player) {
        if (!MageArena.cave(player.position)) return
        if (player.skill(Skill.MAGIC).staticLevel < 60) {
            player.newDialogue().npc(905, "Do not waste my time with trivial questions. I am the",
                "Great Kolodion, master of battle magic. I have an arena to run.")
                .player("Can I enter?").npc(905, "Hah! A wizard of your level? Don't be absurd.").open()
        } else if (MageArena.completed(player)) {
            player.newDialogue().npc(905, "Hello, young mage. You're a tough one.")
                .options("What now?", {
                    it.newDialogue().npc(905, "Step into the magic pool. It will take you to a chamber.",
                        "There, you must decide which god you will represent in the arena.").open()
                }, "How can I use my new spells outside of the arena?", { progress(it) },
                    "I think I've had enough for now.", { it.overlays.closeWindows() }).open()
        } else {
            player.newDialogue().npc(905,
                if (MageArena.stage(player) == 0) "I am the great Kolodion, master of battle magic."
                else "You return, young conjurer.",
                "Before you can use the power of this arena, you must prove yourself against me.")
                .options("Okay, let's fight.", {
                    if (!MageArena.canStart(it)) {
                        it.sendMessage("You cannot enter the arena while carrying weapons or armour.")
                    } else MageArena.start(it)
                }, "What's the point of that?", {
                    it.newDialogue().npc(905, "We learn how to use our magic to its fullest and how",
                        "to channel the forces of the cosmos into our world...",
                        "But mainly, I just like blasting people into dust.")
                        .then { p -> kolodion(p) }.open()
                }, "No thanks.", { it.overlays.closeWindows() }).open()
        }
    }

    private fun progress(player: Player) {
        val text = MageArena.God.entries.map { god ->
            if (MageArena.casts(player, god) >= MageArena.SPELL_CASTS)
                "You're experienced enough to use ${god.spell.formattedName} anywhere."
            else "You still need to train with ${god.spell.formattedName} inside the arena."
        }
        player.newDialogue().npc(905, *text.toTypedArray()).open()
    }

    fun guardian(player: Player) {
        if (!MageArena.chamber(player.position)) return
        if (MageArena.stage(player) == 7 && MageArena.ownedGod(player) != null) {
            player.newDialogue().npc(904, "Good, good, I hope you have chosen well.",
                "I will now present you with a magic staff.")
                .then { MageArena.claimStaff(it) }.open()
        } else if (MageArena.stage(player) < MageArena.STAFF_GIVEN) {
            player.newDialogue().npc(904, "Sssshhh... the gods are talking. I can hear their whispers.",
                "Go chant at the statue of the god you most wish to represent in this world.",
                "Once you are done, come back to me. I shall supply you with a mage staff ready for battle.").open()
        } else {
            player.newDialogue().npc(904, "Hello adventurer, are you looking for another staff?")
                .options("What do you have to offer?", { staffShop(it) }, "No thanks.", { it.overlays.closeWindows() },
                    "Tell me what you know about the charge spell.", {
                        it.newDialogue().npc(904, "The charge spell draws even more power from the cosmos.",
                            "While wearing a matching cape and staff it will add 50% more damage",
                            "to that already caused by battle mage spells for several minutes.").open()
                    }).open()
        }
    }

    fun staffShop(player: Player) {
        if (MageArena.chamber(player.position) && MageArena.stage(player) >= MageArena.STAFF_GIVEN)
            player.overlays.open(ShopInterface(player.world, "Mage Arena Staffs."))
        else player.sendMessage("The guardian is deep in concentration.")
    }

    fun gundai(player: Player) {
        player.newDialogue().npc(902, "I'm a banker, the only one around these dangerous parts.")
            .options("Cool, I'd like to access my bank account please.", { it.bank.open() },
                "Well, now I know.", { it.newDialogue().npc(902, "Knowledge is power my friend.").open() }).open()
    }

    fun lundail(player: Player) {
        player.newDialogue().npc(903, "How can I help you, brave adventurer?")
            .options("What are you selling?", {
                it.overlays.open(ShopInterface(it.world, "Lundail's Arena-side Rune Shop."))
            }, "What's that big old building above us?", {
                it.newDialogue().npc(903, "That, my friend, is the mage battle arena.",
                    "Top mages come from all over RuneScape to compete in the arena.",
                    "Few return, most get fried, hence the smell.").open()
            }).open()
    }
}
