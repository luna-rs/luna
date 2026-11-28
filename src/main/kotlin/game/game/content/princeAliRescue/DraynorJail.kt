package game.content.princeAliRescue

import game.content.princeAliRescue.PrinceAliRescue as Quest
import game.content.princeAliRescue.PrinceAliRescue.aliCellUnlocked
import game.obj.doors.QuestDoors
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Runs the key impression, guard distraction and prisoner hand-off in Draynor.
 *
 * @author lare96
 */
object DraynorJail {
    const val FLATTER = "I've heard stories about you."
    const val ASK_PRISONER = "What is your latest plan?"
    const val ASK_ESCAPE = "What if someone tries to free your prisoner?"
    const val SEE_KEY = "Could I see the key please?"
    const val TOUCH_KEY = "Could I touch the key for a moment?"
    const val OFFER_BEER = "I have some beer here, fancy one?"

    fun keli(player: Player, npc: Npc) {
        if (Quest.stage(player) >= Quest.KELI_TIED) {
            player.newDialogue().npc(Quest.KELI, "I have nothing to say to you.").open()
            return
        }
        player.newDialogue().npc(Quest.KELI, "Who are you? What are you doing here?")
            .options(FLATTER, { p ->
                p.newDialogue().npc(Quest.KELI, "I am Lady Keli. What have you heard about me?")
                    .options(ASK_PRISONER, { askAboutPrisoner(it, npc) }, "I was just passing by.", { }).open()
            }, "I was just passing by.", { }).open()
    }

    private fun askAboutPrisoner(player: Player, npc: Npc) {
        player.newDialogue().npc(Quest.KELI, "I captured the prince. Nobody is going to take him from me.")
            .options(ASK_ESCAPE, { p ->
                p.newDialogue().npc(Quest.KELI, "There is only one key and I wear it on a chain around my neck.")
                    .options(SEE_KEY, { showKey(it, npc) }, "I should leave you to it.", { }).open()
            }, "I should leave you to it.", { }).open()
    }

    private fun showKey(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(Quest.KELI, "You may look at it. The chain is made of runite.")
        if (canImprint(player)) {
            dialogue.options(TOUCH_KEY, { p ->
                p.newDialogue().npc(Quest.KELI, "Very well. Be quick about it.")
                    .then {
                        if (Quest.nearby(it, npc) && canImprint(it)) {
                            it.inventory.updateAll(listOf(Item(Quest.KEY_PRINT)), listOf(Item(Quest.SOFT_CLAY)))
                            it.sendMessage("You press the key into your soft clay while Keli shows it to you.")
                        }
                    }.open()
            }, "I've seen enough, thanks.", { })
        }
        dialogue.open()
    }

    private fun canImprint(player: Player): Boolean =
        Quest.stage(player) == Quest.BRIEFED && Quest.keyStatus(player) == 0 &&
            player.inventory.contains(Quest.SOFT_CLAY) && !player.inventory.contains(Quest.KEY_PRINT) &&
            !player.bank.contains(Quest.KEY_PRINT)

    fun joe(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (Quest.stage(player)) {
            Quest.PREPARED -> dialogue.npc(Quest.JOE, "What are you doing here?")
                .options(OFFER_BEER, { offerBeer(it, npc) }, "Tell me about the life of a guard.", { p ->
                    p.newDialogue().npc(Quest.JOE, "The hours drag. A cold beer would make a nice change.").open()
                }, "I'll leave you to it.", { })
            Quest.JOE_DRUNK, Quest.KELI_TIED ->
                dialogue.npc(Quest.JOE, "I'm shill on duty. Don't tell Keli about the beer.")
            Quest.ALI_RESCUED, Quest.COMPLETE -> dialogue.npc(Quest.JOE, "The prince escaped. I'm in enough trouble already.")
            else -> dialogue.npc(Quest.JOE, "Keep moving. There's nothing for you here.")
        }
        dialogue.open()
    }

    fun offerBeer(player: Player, npc: Npc) {
        if (Quest.stage(player) != Quest.PREPARED) {
            player.sendMessage("You have no reason to offer Joe a beer.")
            return
        }
        player.newDialogue().npc(Quest.JOE, "Ah, one beer would be lovely. Just to wet my throat.")
            .then {
                if (!Quest.nearby(it, npc) || Quest.stage(it) != Quest.PREPARED) return@then
                val beers = it.inventory.computeAmountForId(Quest.BEER)
                if (beers == 0) return@then
                if (beers < 3) {
                    it.inventory.remove(Item(Quest.BEER))
                    it.newDialogue().npc(Quest.JOE, "Thanks. It takes more than one to get me drunk.").open()
                    return@then
                }
                // Commit all three beers with the stage so closing dialogue cannot strand the rescue partway through.
                it.inventory.remove(Item(Quest.BEER, 3))
                Quest.advance(it, Quest.PREPARED, Quest.JOE_DRUNK)
                it.newDialogue().text("Joe downs the first beer. You leave him two more for later.",
                    "He opens them both and soon forgets he is on duty.").open()
            }.open()
    }

    fun tieKeli(player: Player) {
        when (Quest.stage(player)) {
            Quest.JOE_DRUNK -> {
                if (!player.inventory.remove(Item(Quest.ROPE))) return
                Quest.advance(player, Quest.JOE_DRUNK, Quest.KELI_TIED)
                player.newDialogue().text("You overpower Keli, tie her up and put her in a cupboard.").open()
            }
            in Quest.BRIEFED until Quest.JOE_DRUNK -> player.sendMessage("Distract Joe before trying to tie up Lady Keli.")
            else -> player.sendMessage("You have no reason to use the rope on Lady Keli.")
        }
    }

    fun unlock(player: Player, door: GameObject) {
        if (door.position != Quest.cellDoorTile || player.y <= door.y) return
        if (Quest.stage(player) != Quest.KELI_TIED || !player.inventory.contains(Quest.BRONZE_KEY)) {
            player.sendMessage("You need to deal with Joe and Lady Keli before using the key.")
            return
        }
        player.aliCellUnlocked = true
        player.sendMessage("You unlock the prison door with the bronze key.")
        QuestDoors.cross(player, door)
    }

    fun ali(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when {
            Quest.hasRescuedAli(player) ->
                dialogue.npc(Quest.ALI, "I owe you my life. Thank you for getting me out.")
            Quest.stage(player) != Quest.KELI_TIED ->
                dialogue.npc(Quest.ALI, "Please be careful. Lady Keli and her guards are watching.")
            !player.aliCellUnlocked ->
                dialogue.npc(Quest.ALI, "Use the duplicate key on the cell door so I can get out.")
            !Quest.hasDisguiseAndKey(player) ->
                dialogue.npc(Quest.ALI, "Do you have the key and all three pieces of the disguise?")
            else -> dialogue.npc(Quest.ALI, "You've come to rescue me? How will I get past the guards?")
                .player("Dress as Lady Keli and go to Leela. I have the disguise and the key.")
                .then { rescue(it, npc) }
        }
        dialogue.open()
    }

    private fun rescue(player: Player, npc: Npc) {
        if (!Quest.nearby(player, npc) || !Quest.insideCell(player.position) ||
            Quest.stage(player) != Quest.KELI_TIED || !player.aliCellUnlocked || !Quest.hasDisguiseAndKey(player)) return
        player.inventory.removeAll((Quest.disguise + Quest.BRONZE_KEY).map { Item(it) })
        Quest.advance(player, Quest.KELI_TIED, Quest.ALI_RESCUED)
        player.aliCellUnlocked = false
        player.newDialogue().npc(Quest.ALI_DISGUISED, "Thank you, my friend. I will leave with Leela.",
            "Hassan will pay you for your help.")
            .text("Prince Ali escapes in disguise. The Al Kharid border guards will now let you pass for free.").open()
    }
}
